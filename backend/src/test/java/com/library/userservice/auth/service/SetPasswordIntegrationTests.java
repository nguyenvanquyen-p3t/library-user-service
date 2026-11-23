package com.library.userservice.auth.service;

import static com.library.userservice.auth.persistence.VerificationPurpose.REGISTER;
import static com.library.userservice.auth.service.GoogleTestJwks.INITIAL;
import static com.library.userservice.auth.web.GoogleAuthController.GOOGLE_PATH;
import static com.library.userservice.auth.web.GoogleAuthController.REGISTER_PATH;
import static com.library.userservice.user.web.SetPasswordController.REQUEST_CODE_PATH;
import static com.library.userservice.user.web.SetPasswordController.SET_PASSWORD_PATH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.library.userservice.common.api.ErrorCode;
import com.library.userservice.common.exception.ApplicationException;
import com.library.userservice.security.PasswordService;
import com.library.userservice.security.UserSecurityTestKeys;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.config.import=", "debug=false", "spring.mail.host=127.0.0.1",
        "app.otp.mail-from=library-test@example.com",
        "app.otp.hmac-key=" + SetPasswordIntegrationTests.TEST_OTP_KEY,
        "app.google.registration-hmac-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
})
@EnabledIfSystemProperty(named = "spring.datasource.url", matches = "jdbc:postgresql://[^/]+/UserDB_test(?:\\?.*)?")
@Import(GoogleAuthIntegrationTests.FakeGoogle.class)
@ExtendWith(OutputCaptureExtension.class)
class SetPasswordIntegrationTests extends UserSecurityTestKeys {
    private static final String PASSWORD = "  mật khẩu đầu tiên 密  ";
    static final String TEST_OTP_KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";
    private static final Pattern OTP = Pattern.compile("\\b[0-9]{6}\\b");
    private static final String CODES = """
             FROM "VerificationCodes" WHERE "Email" = ? AND "Purpose" = 'SET_PASSWORD'
            """;

    @Autowired JdbcTemplate jdbc;
    @Autowired JsonMapper mapper;
    @MockitoBean Clock clock;
    @MockitoBean JavaMailSender smtp;
    @MockitoSpyBean OtpMailSender mail;
    @MockitoSpyBean PasswordService passwords;
    @LocalServerPort int port;

    private final String username = "uc09-" + UUID.randomUUID();
    private final String email = username + "@example.com";
    private final HttpClient client = HttpClient.newHttpClient();
    private final LinkedBlockingQueue<String> deliveries = new LinkedBlockingQueue<>();
    private Instant now;
    private String googleIdToken;
    private int userId;

    @BeforeEach void setup() throws Exception {
        // Phiên lấy CreatedAt từ database, còn thời điểm nghiệp vụ dùng đồng hồ kiểm thử.
        now = Instant.now().plusSeconds(30).truncatedTo(ChronoUnit.MICROS);
        when(clock.instant()).thenReturn(now);
        googleIdToken = GoogleTestJwks.token(INITIAL, INITIAL.getKeyID(), now,
                claims -> claims.subject(username).claim("email", email).claim("email_verified", true));
        doAnswer(call -> {
            var message = call.getArgument(0, SimpleMailMessage.class);
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(codeCount()).isPositive();
            assertThat(message.getTo()).containsExactly(email);
            assertThat(message.getSubject()).isEqualTo("Mã xác minh thiết lập mật khẩu");
            var matcher = OTP.matcher(message.getText());
            assertThat(matcher.find()).isTrue();
            deliveries.add(matcher.group());
            return null;
        }).when(smtp).send(any(SimpleMailMessage.class));
    }

    @AfterEach void cleanup() {
        jdbc.update("DELETE FROM \"VerificationCodes\" WHERE \"Email\" = ?", email);
        jdbc.update("DELETE FROM \"Users\" WHERE \"Email\" = ?", email);
    }

    @Test void setsFirstPasswordAndRevokesActiveSessionsWhilePreservingGoogleAndHistory() throws Exception {
        var first = registerGoogle();
        var history = googleLogin();
        assertCode(post("/api/v1/auth/logout", Map.of("refreshToken", refreshToken(history)), null),
                200, "LOGOUT_SUCCESS");
        var endedSession = jdbc.queryForMap("""
                SELECT * FROM "UserSessions" WHERE "UserId" = ? AND "RevocationReason" = 'LOGOUT'
                """, userId);
        var rotated = assertCode(post("/api/v1/auth/refresh", Map.of("refreshToken", refreshToken(first)), null),
                200, "TOKEN_REFRESH_SUCCESS");
        var oldToken = jdbc.queryForMap("""
                SELECT * FROM "RefreshTokens" WHERE "UserId" = ? AND "ReplacedByTokenId" IS NOT NULL
                """, userId);

        googleLogin();
        var expiredSid = jdbc.queryForObject("""
                UPDATE "UserSessions"
                SET "CreatedAt" = now() - interval '31 days', "ExpiresAt" = now() - interval '1 day'
                WHERE "UserId" = ? AND "Id" = (
                    SELECT "SessionId" FROM "RefreshTokens" WHERE "UserId" = ? ORDER BY "Id" DESC LIMIT 1
                ) RETURNING "Id"
                """, UUID.class, userId, userId);
        var expiredSession = jdbc.queryForMap("SELECT * FROM \"UserSessions\" WHERE \"Id\" = ?", expiredSid);
        var caller = accessToken(rotated);
        String code = issue(caller);
        var result = set(caller, code, PASSWORD);
        assertThat(assertCode(result, 200, "PASSWORD_SET_SUCCESS").isNull()).isTrue();
        assertThat(result.headers().firstValue("Set-Cookie")).isEmpty();
        assertThat(codeUsed()).isTrue();
        assertThat(jdbc.queryForObject("""
                SELECT "RevocationReason" FROM "UserSessions" WHERE "UserId" = ? AND "RevocationReason" = 'PASSWORD_SET'
                """, String.class, userId)).isEqualTo("PASSWORD_SET");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM "RefreshTokens" WHERE "UserId" = ? AND "RevokedAt" IS NULL
                """, Integer.class, userId)).isZero();
        assertThat(jdbc.queryForMap("SELECT * FROM \"UserSessions\" WHERE \"Id\" = ?", endedSession.get("Id"))
                .equals(endedSession)).isTrue();
        assertThat(jdbc.queryForMap("SELECT * FROM \"UserSessions\" WHERE \"Id\" = ?", expiredSid)
                .equals(expiredSession)).isTrue();
        assertThat(jdbc.queryForMap("SELECT * FROM \"RefreshTokens\" WHERE \"Id\" = ?", oldToken.get("Id"))
                .equals(oldToken)).isTrue();
        assertCode(send("GET", "/api/v1/users/me", null, caller, null), 401, "SESSION_INACTIVE");
        assertCode(post("/api/v1/auth/refresh", Map.of("refreshToken", refreshToken(rotated)), null),
                401, "REFRESH_TOKEN_INVALID");

        var local = assertCode(post("/api/v1/auth/login", Map.of("identifier", email, "password", PASSWORD), null),
                200, "LOGIN_SUCCESS");
        var profile = assertCode(send("GET", "/api/v1/users/me", null, accessToken(local), null),
                200, "PROFILE_GET_SUCCESS");
        assertThat(profile.path("id").asInt()).isEqualTo(userId);
        assertThat(profile.path("hasPassword").asBoolean()).isTrue();
        assertThat(profile.path("linkedProviders").get(0).asText()).isEqualTo("GOOGLE");
        assertThat(profile.path("roles").get(0).asText()).isEqualTo("READER");
        var google = googleLogin();
        assertCode(post(REQUEST_CODE_PATH, null, accessToken(google)), 409, "PASSWORD_ALREADY_CONFIGURED");
        assertCode(set(accessToken(google), code, PASSWORD), 409, "PASSWORD_ALREADY_CONFIGURED");
        assertThat(codeCount()).isEqualTo(1);
        verify(smtp).send(any(SimpleMailMessage.class));
    }

    @Test void protectsBothRoutesAndValidatesSetBody() throws Exception {
        for (String path : List.of(REQUEST_CODE_PATH, SET_PASSWORD_PATH)) {
            assertCode(post(path, null, null), 401, "AUTHENTICATION_REQUIRED");
            assertCode(post(path, null, "invalid-token"), 401, "ACCESS_TOKEN_INVALID");
        }
        var access = accessToken(registerGoogle());
        assertCode(send("POST", SET_PASSWORD_PATH, null, access, "application/json"), 400, "INVALID_REQUEST");
        assertCode(post(SET_PASSWORD_PATH, Map.of("verificationCode", "012345", "newPassword", PASSWORD,
                "confirmPassword", "mật khẩu khác"), access), 400, "VALIDATION_ERROR");
        assertCode(post(SET_PASSWORD_PATH, Map.of("verificationCode", "012345", "newPassword", PASSWORD,
                "confirmPassword", PASSWORD, "email", "other@example.com"), access), 400, "VALIDATION_ERROR");
        assertCode(send("POST", SET_PASSWORD_PATH, "{}", access, "text/plain"), 415, "UNSUPPORTED_MEDIA_TYPE");
        assertCode(send("GET", REQUEST_CODE_PATH, null, access, null), 405, "METHOD_NOT_ALLOWED");
        assertThat(codeCount()).isZero();
        verifyNoInteractions(smtp);

        var issued = post(REQUEST_CODE_PATH + "?email=other%40example.com", null, access);
        assertThat(assertCode(issued, 200, "SET_PASSWORD_CODE_SENT").path("expiresIn").asInt()).isEqualTo(600);
        assertThat(codeCount()).isEqualTo(1);
        verify(smtp).send(any(SimpleMailMessage.class));
    }

    @Test void smtpTimeoutKeepsCommittedCodeAndQuotaButMissingConfigurationDoesNotIssue(CapturedOutput output)
            throws Exception {
        var access = accessToken(registerGoogle());
        String oldCode = issue(access);
        when(clock.instant()).thenReturn(now.plusSeconds(60));
        doThrow(new ApplicationException(ErrorCode.SERVICE_UNAVAILABLE)).doCallRealMethod().when(mail).requireConfigured();
        assertCode(post(REQUEST_CODE_PATH, null, access), 503, "SERVICE_UNAVAILABLE");
        assertThat(codeCount()).isEqualTo(1);

        String secretDetail = "smtp-rejected-message-detail";
        doAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(codeCount()).isEqualTo(2);
            throw new MailSendException(secretDetail, new SocketTimeoutException());
        }).when(smtp).send(any(SimpleMailMessage.class));
        var timeout = post(REQUEST_CODE_PATH, null, access);
        assertCode(timeout, 503, "SERVICE_UNAVAILABLE");
        assertThat(timeout.body().contains(secretDetail) || output.getAll().contains(secretDetail)).isFalse();
        assertCode(set(access, oldCode, PASSWORD), 400, "OTP_INVALID");
        assertRetryAfter(access, 60);
        assertThat(codeCount()).isEqualTo(2);
    }

    @Test void isolatesOtpPurposeAndEnforcesExpiryAndAttemptAndIssuanceLimits() throws Exception {
        var access = accessToken(registerGoogle());
        String wrongPurpose = "012345";
        jdbc.update("""
                INSERT INTO "VerificationCodes" ("Email", "Purpose", "CodeHash", "CreatedAt", "ExpiresAt")
                VALUES (?, 'REGISTER', ?, ?, ?)
                """, email, new OtpHash(TEST_OTP_KEY).encode(email, REGISTER, wrongPurpose),
                Timestamp.from(now), Timestamp.from(now.plusSeconds(600)));
        assertCode(set(access, wrongPurpose, PASSWORD), 400, "OTP_INVALID");
        String code = issue(access);
        String wrong = code.equals("012345") ? "654321" : "012345";
        for (int attempt = 1; attempt <= 5; attempt++) {
            assertCode(set(access, wrong, PASSWORD), 400, attempt == 5 ? "OTP_ATTEMPTS_EXCEEDED" : "OTP_INVALID");
            assertThat(jdbc.queryForObject("SELECT \"AttemptCount\"" + CODES, Integer.class, email))
                    .isEqualTo(attempt);
        }
        assertCode(set(access, code, PASSWORD), 400, "OTP_ATTEMPTS_EXCEEDED");
        assertThat(codeUsed()).isFalse();
        when(clock.instant()).thenReturn(now.plusSeconds(60).minusMillis(1));
        assertRetryAfter(access, 1);
        for (int issue = 1; issue <= 4; issue++) {
            when(clock.instant()).thenReturn(now.plusSeconds(issue * 60L));
            code = issue(access);
        }
        assertRetryAfter(access, 3360);
        when(clock.instant()).thenReturn(now.plusSeconds(840));
        assertCode(set(access, code, PASSWORD), 400, "OTP_INVALID");
        assertThat(codeUsed()).isFalse();
        var freshAccess = accessToken(googleLogin());
        when(clock.instant()).thenReturn(now.plusSeconds(3600));
        issue(freshAccess);
        assertThat(codeCount()).isEqualTo(6);
    }

    @Test void rollsBackPasswordAndOtpAndRevocationsOnCommitFailureAndCanRetry(CapturedOutput output) throws Exception {
        var pair = registerGoogle();
        String access = accessToken(pair);
        String code = issue(access);
        var before = snapshot();
        String attemptedPassword = "commit-failure-uc09";
        var attemptedHash = new AtomicReference<String>();
        doAnswer(call -> {
            var hash = (String) call.callRealMethod();
            attemptedHash.set(hash);
            jdbc.execute("""
                    CREATE TEMP TABLE uc09_commit_fixture
                        (id integer UNIQUE DEFERRABLE INITIALLY DEFERRED) ON COMMIT DROP
                    """);
            jdbc.update("INSERT INTO uc09_commit_fixture VALUES (1), (1)");
            return hash;
        }).when(passwords).encode(attemptedPassword);

        var failure = set(access, code, attemptedPassword);
        assertCode(failure, 500, "INTERNAL_ERROR");
        assertThat(snapshot().equals(before)).isTrue();
        String codeHash = (String) before.codes().getFirst().get("CodeHash");
        for (String secret : List.of(code, codeHash, attemptedPassword, attemptedHash.get(), access, refreshToken(pair))) {
            assertThat(failure.body().contains(secret) || output.getAll().contains(secret)).isFalse();
        }
        assertCode(set(access, code, PASSWORD), 200, "PASSWORD_SET_SUCCESS");
        assertThat(codeUsed()).isTrue();
    }

    @Test void concurrentSetsUseTheOtpOnceAndRecheckTheSecondSessionAfterWaitingForLock() throws Exception {
        var firstAccess = accessToken(registerGoogle());
        var secondAccess = accessToken(googleLogin());
        String code = issue(firstAccess);
        String secondPassword = "mật khẩu request thứ hai";
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        doAnswer(call -> {
            entered.countDown();
            await(release);
            return call.callRealMethod();
        }).when(passwords).encode(PASSWORD);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> set(firstAccess, code, PASSWORD));
            try {
                await(entered);
                var second = executor.submit(() -> set(secondAccess, code, secondPassword));
                assertWaitingForOtpLock();
                release.countDown();
                assertCode(first.get(15, TimeUnit.SECONDS), 200, "PASSWORD_SET_SUCCESS");
                assertCode(second.get(15, TimeUnit.SECONDS), 401, "SESSION_INACTIVE");
            } finally {
                release.countDown();
            }
        }
        assertThat(codeUsed()).isTrue();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM "UserSessions" WHERE "UserId" = ? AND "RevocationReason" = 'PASSWORD_SET'
                """, Integer.class, userId)).isEqualTo(2);
        String hash = jdbc.queryForObject("SELECT \"PasswordHash\" FROM \"Users\" WHERE \"Id\" = ?", String.class, userId);
        assertThat(passwords.matches(PASSWORD, hash)).isTrue();
        assertThat(passwords.matches(secondPassword, hash)).isFalse();
    }

    private JsonNode registerGoogle() throws Exception {
        var registration = assertCode(post(GOOGLE_PATH, Map.of("idToken", googleIdToken), null),
                200, "GOOGLE_REGISTRATION_REQUIRED");
        var result = assertCode(post(REGISTER_PATH, Map.of("registrationToken", registration.path("registrationToken").asText(),
                "userName", username, "fullName", "UC09 Reader"), null), 201, "GOOGLE_REGISTER_SUCCESS");
        userId = result.path("user").path("id").asInt();
        assertThat(result.path("user").path("hasPassword").asBoolean()).isFalse();
        return result.path("token");
    }

    private JsonNode googleLogin() throws Exception {
        return assertCode(post(GOOGLE_PATH, Map.of("idToken", googleIdToken), null), 200, "GOOGLE_LOGIN_SUCCESS");
    }

    private static String accessToken(JsonNode pair) { return pair.path("accessToken").asText(); }

    private static String refreshToken(JsonNode pair) { return pair.path("refreshToken").asText(); }

    private String issue(String access) throws Exception {
        var response = post(REQUEST_CODE_PATH, null, access);
        assertThat(assertCode(response, 200, "SET_PASSWORD_CODE_SENT").path("expiresIn").asInt()).isEqualTo(600);
        String code = deliveries.poll(5, TimeUnit.SECONDS);
        assertThat(code != null && !response.body().contains(code)).isTrue();
        return code;
    }

    private HttpResponse<String> set(String access, String otp, String password) throws Exception {
        return post(SET_PASSWORD_PATH, Map.of("verificationCode", otp, "newPassword", password,
                "confirmPassword", password), access);
    }

    private HttpResponse<String> post(String path, Map<String, String> body, String access) throws Exception {
        return send("POST", path, body == null ? null : mapper.writeValueAsString(body), access,
                body == null ? null : "application/json");
    }

    private HttpResponse<String> send(String method, String path, String body, String access, String media) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(20));
        if (access != null) request.header("Authorization", "Bearer " + access);
        if (media != null) request.header("Content-Type", media);
        request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode assertCode(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).isEqualTo(status);
        var body = mapper.readTree(response.body());
        assertThat(body.path("messageCode").asText()).isEqualTo(code);
        return body.path("data");
    }

    private void assertRetryAfter(String access, int seconds) throws Exception {
        var response = post(REQUEST_CODE_PATH, null, access);
        assertCode(response, 429, "OTP_RATE_LIMITED");
        assertThat(response.headers().firstValue("Retry-After")).contains(Integer.toString(seconds));
    }

    private int codeCount() { return jdbc.queryForObject("SELECT count(*)" + CODES, Integer.class, email); }

    private boolean codeUsed() {
        return jdbc.queryForObject("SELECT \"UsedAt\" IS NOT NULL" + CODES + " ORDER BY \"Id\" DESC LIMIT 1",
                Boolean.class, email);
    }

    private Snapshot snapshot() {
        return new Snapshot(jdbc.queryForMap("SELECT * FROM \"Users\" WHERE \"Id\" = ?", userId),
                jdbc.queryForList("SELECT * FROM \"UserSessions\" WHERE \"UserId\" = ? ORDER BY \"Id\"", userId),
                jdbc.queryForList("SELECT * FROM \"RefreshTokens\" WHERE \"UserId\" = ? ORDER BY \"Id\"", userId),
                jdbc.queryForList("SELECT *" + CODES + " ORDER BY \"Id\"", email));
    }

    private void assertWaitingForOtpLock() throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (jdbc.queryForObject("""
                    SELECT EXISTS (SELECT 1 FROM pg_stat_activity
                        WHERE datname = current_database() AND pid <> pg_backend_pid()
                          AND wait_event_type = 'Lock' AND wait_event = 'advisory'
                          AND query LIKE '%pg_advisory_xact_lock%')
                    """, Boolean.class)) return;
            Thread.sleep(20);
        }
        throw new AssertionError("Request thiết lập thứ hai chưa chờ khóa OTP");
    }

    private static void await(CountDownLatch latch) throws InterruptedException {
        if (!latch.await(10, TimeUnit.SECONDS)) throw new AssertionError("Hết thời gian chờ fixture UC09");
    }

    private record Snapshot(Map<String, Object> user, List<Map<String, Object>> sessions,
                            List<Map<String, Object>> tokens, List<Map<String, Object>> codes) {
        @Override public String toString() { return "Snapshot[REDACTED]"; }
    }
}
