package com.library.userservice.auth.service;

import static com.library.userservice.auth.web.PasswordResetController.REQUEST_CODE_PATH;
import static com.library.userservice.auth.web.PasswordResetController.RESET_PATH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.library.userservice.common.api.ErrorCode;
import com.library.userservice.common.exception.ApplicationException;
import com.library.userservice.security.PasswordService;
import com.library.userservice.security.UserSecurityTestKeys;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.config.import=", "debug=false", "spring.mail.host=127.0.0.1",
        "app.otp.mail-from=library-test@example.com",
        "app.otp.hmac-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
})
@EnabledIfSystemProperty(named = "spring.datasource.url", matches = "jdbc:postgresql://[^/]+/UserDB_test(?:\\?.*)?")
class PasswordResetIntegrationTests extends UserSecurityTestKeys {
    private static final String OLD = "old-password-uc05";
    private static final String NEXT = "  mật khẩu mới 密  ";
    private static final String CODES = """
             FROM "VerificationCodes" WHERE "Email" = ? AND "Purpose" = 'PASSWORD_RESET'
            """;
    private static final Pattern OTP = Pattern.compile("\\b[0-9]{6}\\b");

    @Autowired JdbcTemplate jdbc;
    @Autowired JsonMapper mapper;
    @MockitoBean JavaMailSender smtp;
    @MockitoBean Clock clock;
    @MockitoSpyBean PasswordService passwords;
    @MockitoSpyBean OtpMailSender mail;
    @LocalServerPort int port;

    private final String username = "reset-" + UUID.randomUUID();
    private final String email = username + "@example.com";
    private final Instant start = Instant.now();
    private final HttpClient client = HttpClient.newHttpClient();
    private final LinkedBlockingQueue<String> deliveries = new LinkedBlockingQueue<>();
    private Integer userId;

    @BeforeEach void setup() {
        when(clock.instant()).thenReturn(start);
        doAnswer(call -> {
            var message = call.getArgument(0, SimpleMailMessage.class);
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(countCodes()).isPositive();
            assertThat(message.getSubject()).contains("đặt lại mật khẩu");
            var matcher = OTP.matcher(message.getText());
            assertThat(matcher.find()).isTrue();
            deliveries.add(matcher.group());
            return null;
        }).when(smtp).send(any(SimpleMailMessage.class));
    }

    @AfterEach void cleanup() {
        jdbc.update("DELETE FROM \"VerificationCodes\" WHERE \"Email\" = ?", email);
        if (userId != null) jdbc.update("DELETE FROM \"Users\" WHERE \"Id\" = ?", userId);
    }

    @Test void absentAndPasswordlessAccountsDoNotIssueOrSendCodes() throws Exception {
        assertCode(requestCode(), 200, "PASSWORD_RESET_CODE_SENT");
        assertCode(reset("123456", NEXT), 400, "OTP_INVALID");
        createUser("ACTIVE", null);
        assertCode(requestCode(), 409, "PASSWORD_NOT_CONFIGURED");
        assertCode(reset("123456", NEXT), 409, "PASSWORD_NOT_CONFIGURED");
        assertThat(countCodes()).isZero();
        verifyNoInteractions(smtp);
    }

    @Test void missingSmtpConfigurationLeavesThePreviousCodeAndQuotaUsable() throws Exception {
        createUser("ACTIVE", passwords.encode(OLD));
        String previous = issueAndReceive();
        when(clock.instant()).thenReturn(start.plusSeconds(60));
        doThrow(new ApplicationException(ErrorCode.SERVICE_UNAVAILABLE)).when(mail).requireConfigured();
        assertCode(requestCode(), 503, "SERVICE_UNAVAILABLE");
        assertThat(countCodes()).isEqualTo(1);
        verify(smtp, times(1)).send(any(SimpleMailMessage.class));
        assertCode(reset(previous, NEXT), 200, "PASSWORD_RESET_SUCCESS");
    }

    @Test void commitsBeforeSmtpAndKeepsGenericResponseAndQuotaWhenSmtpTimesOut() throws Exception {
        createUser("ACTIVE", passwords.encode(OLD));
        String previous = issueAndReceive();
        when(clock.instant()).thenReturn(start.plusSeconds(60));
        doAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(countCodes()).isEqualTo(2);
            throw new MailSendException("SMTP test timeout", new SocketTimeoutException());
        }).when(smtp).send(any(SimpleMailMessage.class));

        assertCode(requestCode(), 200, "PASSWORD_RESET_CODE_SENT");
        assertCode(reset(previous, NEXT), 400, "OTP_INVALID");
        var limited = requestCode();
        assertCode(limited, 429, "OTP_RATE_LIMITED");
        assertThat(limited.headers().firstValue("Retry-After")).contains("60");
        assertThat(countCodes()).isEqualTo(2);
        verify(smtp, times(2)).send(any(SimpleMailMessage.class));
    }

    @Test void appliesPurposeIsolationRateLimitsExpirationAndCommittedWrongAttempts() throws Exception {
        createUser("ACTIVE", passwords.encode(OLD));
        String correct = issueAndReceive();
        var wrong = correct.equals("123456") ? "654321" : "123456";
        jdbc.update("""
                INSERT INTO "VerificationCodes" ("Email", "Purpose", "CodeHash", "CreatedAt", "ExpiresAt")
                SELECT "Email", 'REGISTER', "CodeHash", "CreatedAt", "ExpiresAt"
                FROM "VerificationCodes" WHERE "Email" = ? AND "Purpose" = 'PASSWORD_RESET'
                """, email);
        assertCode(post("/api/v1/auth/register", Map.of("userName", username, "email", email,
                "fullName", "Reset Test", "password", NEXT, "verificationCode", correct)), 400, "OTP_INVALID");
        assertThat(attempts()).isZero();

        for (int attempt = 1; attempt <= 5; attempt++) {
            assertCode(reset(wrong, NEXT), 400, attempt == 5 ? "OTP_ATTEMPTS_EXCEEDED" : "OTP_INVALID");
            assertThat(attempts()).isEqualTo(attempt);
        }
        assertCode(reset(correct, NEXT), 400, "OTP_ATTEMPTS_EXCEEDED");
        assertThat(codeUsed()).isFalse();
        when(clock.instant()).thenReturn(start.plusSeconds(60).minusMillis(1));
        assertRetryAfter(1);
        for (int i = 1; i <= 4; i++) {
            when(clock.instant()).thenReturn(start.plusSeconds(i * 60L));
            correct = issueAndReceive();
        }
        assertRetryAfter(3360);
        when(clock.instant()).thenReturn(start.plusSeconds(840));
        assertCode(reset(correct, NEXT), 400, "OTP_INVALID");
        assertThat(attempts()).isZero();
        when(clock.instant()).thenReturn(start.plusSeconds(3600));
        issueAndReceive();
        assertThat(countCodes()).isEqualTo(6);
    }

    @Test void rollsBackHashCodeSessionsAndTokensWhenCommitFailsAndCanRetry() throws Exception {
        when(clock.instant()).thenAnswer(call -> Instant.now());
        createUser("ACTIVE", passwords.encode(OLD));
        var originalPair = tokenPair(login(OLD));
        var rotated = tokenPair(refresh(originalPair.get("refreshToken")));
        String code = issueAndReceive();
        var before = snapshot();
        doAnswer(call -> {
            var hash = call.callRealMethod();
            jdbc.execute("""
                    CREATE TEMP TABLE uc05_commit_fixture
                        (id integer UNIQUE DEFERRABLE INITIALLY DEFERRED) ON COMMIT DROP
                    """);
            jdbc.update("INSERT INTO uc05_commit_fixture VALUES (1), (1)");
            return hash;
        }).when(passwords).encode("commit-failure");
        assertCode(reset(code, "commit-failure"), 500, "INTERNAL_ERROR");
        assertThat(snapshot().equals(before)).isTrue();
        assertThat(codeUsed()).isFalse();

        assertCode(reset(code, NEXT), 200, "PASSWORD_RESET_SUCCESS");
        assertThat(codeUsed()).isTrue();
        assertThat(jdbc.queryForObject("""
                SELECT "RevocationReason" FROM "UserSessions" WHERE "UserId" = ?
                """, String.class, userId)).isEqualTo("PASSWORD_RESET");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM "RefreshTokens" WHERE "UserId" = ? AND "RevokedAt" IS NULL
                """, Integer.class, userId)).isZero();
        assertThat(jdbc.queryForList("""
                SELECT * FROM "RefreshTokens" WHERE "UserId" = ? ORDER BY "Id"
                """, userId).getFirst().equals(before.tokens().getFirst())).isTrue();
        var bearer = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/users/me"))
                .header("Authorization", "Bearer " + rotated.get("accessToken")).GET().build();
        assertCode(client.send(bearer, HttpResponse.BodyHandlers.ofString()), 401, "SESSION_INACTIVE");
        assertCode(refresh(rotated.get("refreshToken")), 401, "REFRESH_TOKEN_INVALID");
        assertCode(reset(code, NEXT), 400, "OTP_INVALID");
        assertCode(login(OLD), 401, "INVALID_CREDENTIALS");
        assertCode(login(NEXT), 200, "LOGIN_SUCCESS");
    }

    @Test void concurrentResetConsumesTheCodeOnlyOnceAndLeavesBannedStatus() throws Exception {
        createUser("BANNED", passwords.encode(OLD));
        String code = issueAndReceive();
        var gate = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> { await(gate); return reset(code, NEXT); });
            var second = executor.submit(() -> { await(gate); return reset(code, NEXT); });
            gate.countDown();
            assertThat(List.of(first.get().statusCode(), second.get().statusCode()))
                    .containsExactlyInAnyOrder(200, 400);
        }
        assertThat(codeUsed()).isTrue();
        assertThat(jdbc.queryForObject("SELECT \"Status\" FROM \"Users\" WHERE \"Id\" = ?", String.class, userId))
                .isEqualTo("BANNED");
        assertCode(login(NEXT), 403, "ACCOUNT_BANNED");
    }

    @Test void loginAndRefreshWaitForResetAndThenSeeTheCommittedState() throws Exception {
        when(clock.instant()).thenAnswer(call -> Instant.now());
        createUser("ACTIVE", passwords.encode(OLD));
        String refreshToken = tokenPair(login(OLD)).get("refreshToken");
        String code = issueAndReceive();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        doAnswer(call -> {
            entered.countDown();
            await(release);
            return call.callRealMethod();
        }).when(passwords).encode(NEXT);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var reset = executor.submit(() -> reset(code, NEXT));
            try {
                await(entered);
                var login = executor.submit(() -> login(OLD));
                var refresh = executor.submit(() -> refresh(refreshToken));
                assertUserLockWaits();
                release.countDown();
                assertCode(reset.get(), 200, "PASSWORD_RESET_SUCCESS");
                assertCode(login.get(), 401, "INVALID_CREDENTIALS");
                assertCode(refresh.get(), 401, "REFRESH_TOKEN_INVALID");
            } finally {
                release.countDown();
            }
        }
    }

    private void createUser(String status, String hash) {
        userId = jdbc.queryForObject("""
                INSERT INTO "Users" ("UserName", "Email", "FullName", "PasswordHash", "Status")
                VALUES (?, ?, 'Reset Test', ?, ?) RETURNING "Id"
                """, Integer.class, username, email, hash, status);
    }

    private String issueAndReceive() throws Exception {
        assertCode(requestCode(), 200, "PASSWORD_RESET_CODE_SENT");
        String code = deliveries.poll(5, TimeUnit.SECONDS);
        assertThat(code).isNotNull();
        return code;
    }

    private HttpResponse<String> requestCode() throws Exception {
        return post(REQUEST_CODE_PATH, Map.of("email", " " + email.toUpperCase(Locale.ROOT) + " "));
    }

    private HttpResponse<String> reset(String code, String next) throws Exception {
        return post(RESET_PATH, Map.of("email", email, "verificationCode", code, "newPassword", next,
                "confirmPassword", next));
    }

    private HttpResponse<String> login(String password) throws Exception {
        return post("/api/v1/auth/login", Map.of("identifier", email, "password", password));
    }

    private HttpResponse<String> refresh(String token) throws Exception {
        return post("/api/v1/auth/refresh", Map.of("refreshToken", token));
    }

    private HttpResponse<String> post(String path, Map<String, String> body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(15)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private Map<String, String> tokenPair(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(200);
        var data = mapper.readTree(response.body()).path("data");
        return Map.of("accessToken", data.path("accessToken").asText(),
                "refreshToken", data.path("refreshToken").asText());
    }

    private void assertCode(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(mapper.readTree(response.body()).path("messageCode").asText()).isEqualTo(code);
    }

    private void assertRetryAfter(int seconds) throws Exception {
        var response = requestCode();
        assertCode(response, 429, "OTP_RATE_LIMITED");
        assertThat(response.headers().firstValue("Retry-After")).contains(Integer.toString(seconds));
    }

    private int countCodes() { return jdbc.queryForObject("SELECT count(*)" + CODES, Integer.class, email); }

    private int attempts() {
        return jdbc.queryForObject("SELECT \"AttemptCount\"" + CODES + " ORDER BY \"Id\" DESC LIMIT 1", Integer.class, email);
    }

    private boolean codeUsed() {
        return jdbc.queryForObject("SELECT \"UsedAt\" IS NOT NULL" + CODES + " ORDER BY \"Id\" DESC LIMIT 1", Boolean.class, email);
    }

    private Snapshot snapshot() {
        return new Snapshot(jdbc.queryForMap("SELECT * FROM \"Users\" WHERE \"Id\" = ?", userId),
                jdbc.queryForList("SELECT * FROM \"UserSessions\" WHERE \"UserId\" = ? ORDER BY \"Id\"", userId),
                jdbc.queryForList("SELECT * FROM \"RefreshTokens\" WHERE \"UserId\" = ? ORDER BY \"Id\"", userId));
    }

    private void assertUserLockWaits() throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            int waiting = jdbc.queryForObject("""
                    SELECT count(*) FROM pg_stat_activity WHERE datname = current_database()
                    AND pid <> pg_backend_pid() AND wait_event_type = 'Lock'
                    AND query LIKE '%Users%' AND query LIKE '%for%update%'
                    """, Integer.class);
            if (waiting >= 2) return;
            Thread.sleep(20);
        }
        throw new AssertionError("Không quan sát được cả login và refresh chờ khóa user");
    }

    private static void await(CountDownLatch latch) throws InterruptedException {
        if (!latch.await(10, TimeUnit.SECONDS)) throw new AssertionError("Hết thời gian chờ fixture");
    }

    private record Snapshot(Map<String, Object> user, List<Map<String, Object>> sessions,
                            List<Map<String, Object>> tokens) {
        @Override public String toString() { return "Snapshot[REDACTED]"; }
    }
}
