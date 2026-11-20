package com.library.userservice.auth;

import static com.library.userservice.auth.web.RegistrationController.REGISTER_PATH;
import static com.library.userservice.auth.web.RegistrationController.REQUEST_CODE_PATH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import com.library.userservice.security.PasswordService;
import com.library.userservice.security.UserSecurityTestKeys;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.config.import=", "debug=false", "spring.mail.host=127.0.0.1",
                "app.otp.mail-from=library-test@example.com"})
@EnabledIfSystemProperty(named = "spring.datasource.url", matches = "jdbc:postgresql://[^/]+/UserDB_test(?:\\?.*)?")
class RegistrationIntegrationTests extends UserSecurityTestKeys {
    private static final String PASSWORD = "  mật khẩu 密  ";
    private static final Instant START = Instant.parse("2026-11-20T02:00:00Z");
    private static final String CODES = """
             FROM "VerificationCodes" WHERE "Email" = ? AND "Purpose" = 'REGISTER'
            """;
    private static final SmtpFixture SMTP = new SmtpFixture();

    @DynamicPropertySource static void registrationProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mail.port", SMTP::port);
        registry.add("app.otp.hmac-key", () -> Base64.getEncoder().encodeToString(new byte[32]));
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired JsonMapper mapper;
    @MockitoBean Clock clock;
    @MockitoSpyBean PasswordService passwords;
    @LocalServerPort int port;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final String username = "otp-" + UUID.randomUUID();
    private final String email = username + "@example.com";

    @BeforeEach void setup() {
        SMTP.clear();
        when(clock.instant()).thenReturn(START);
        SMTP.observe = this::countCodes;
    }

    @AfterEach void cleanup() {
        SMTP.clear();
        jdbc.update("DELETE FROM \"VerificationCodes\" WHERE \"Email\" = ?", email);
        jdbc.update("DELETE FROM \"Users\" WHERE \"Email\" IN (?, ?)", email, "other-" + email);
    }

    @AfterAll static void closeSmtp() throws IOException { SMTP.close(); }

    @Test void sendsAfterCommitAndRegistersThenLogsInWithChangedUsername() throws Exception {
        assertCode(requestCode(), 200, "REGISTER_CODE_SENT");
        var receipt = receipt();
        assertThat(receipt.visibleCodes()).isEqualTo(1);
        var codeHash = jdbc.queryForObject("SELECT \"CodeHash\"" + CODES, String.class, email);
        assertThat(codeHash).matches("hmac-sha256-v1:[a-f0-9]{64}");
        assertThat(jdbc.queryForObject("SELECT EXTRACT(EPOCH FROM \"ExpiresAt\" - \"CreatedAt\")" + CODES,
                Integer.class, email)).isEqualTo(600);

        var result = register("r-" + username, receipt.code(), PASSWORD);
        assertCode(result, 201, "REGISTER_SUCCESS");
        var data = mapper.readTree(result.body()).path("data");
        assertThat(data.path("userName").asText()).isEqualTo("r-" + username);
        assertThat(data.path("status").asText()).isEqualTo("ACTIVE");
        assertThat(data.path("roles").get(0).asText()).isEqualTo("READER");
        assertThat(data.path("hasPassword").asBoolean()).isTrue();
        assertThat(data.path("linkedProviders").isEmpty()).isTrue();
        assertThat(data.path("phoneNumber").isNull()).isTrue();
        assertThat(latestUsed()).isTrue();
        int id = data.path("id").asInt();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM \"UserSessions\" WHERE \"UserId\" = ?", Long.class, id)).isZero();
        assertThat(jdbc.queryForObject("SELECT \"PasswordHash\" FROM \"Users\" WHERE \"Id\" = ?", String.class, id))
                .startsWith("{argon2id}");
        assertCode(post("/api/v1/auth/login", Map.of("identifier", email, "password", PASSWORD)), 200, "LOGIN_SUCCESS");
        assertCode(requestCode(), 409, "EMAIL_ALREADY_EXISTS");
    }

    @Test void smtpRejectionKeepsIssuanceAndSupersedesPreviousCode() throws Exception {
        assertCode(requestCode(), 200, "REGISTER_CODE_SENT");
        String old = receipt().code();
        when(clock.instant()).thenReturn(START.plusSeconds(60));
        SMTP.reject = true;
        assertCode(requestCode(), 503, "SERVICE_UNAVAILABLE");
        assertThat(receipt().visibleCodes()).isEqualTo(2);
        assertThat(countCodes()).isEqualTo(2);
        assertCode(register(username, old, PASSWORD), 400, "OTP_INVALID");
        assertThat(attempts()).isEqualTo(1);
        assertCode(requestCode(), 429, "OTP_RATE_LIMITED");
    }

    @Test void enforcesExactCooldownSlidingWindowAndExpiration() throws Exception {
        assertCode(requestCode(), 200, "REGISTER_CODE_SENT");
        when(clock.instant()).thenReturn(START.plusSeconds(60).minusNanos(1_000));
        assertRetryAfter(1);
        for (int index = 1; index <= 4; index++) {
            when(clock.instant()).thenReturn(START.plusSeconds(index * 60L));
            assertCode(requestCode(), 200, "REGISTER_CODE_SENT");
        }
        assertRetryAfter(3360);
        when(clock.instant()).thenReturn(START.plusSeconds(3600).minusNanos(1_000));
        assertRetryAfter(1);
        when(clock.instant()).thenReturn(START.plusSeconds(3600));
        assertCode(requestCode(), 200, "REGISTER_CODE_SENT");
        var messages = new java.util.ArrayList<MailReceipt>();
        SMTP.deliveries.drainTo(messages);
        assertThat(messages).hasSize(6);
        when(clock.instant()).thenReturn(START.plusSeconds(4200));
        assertCode(register(username, messages.getLast().code(), PASSWORD), 400, "OTP_INVALID");
        assertThat(attempts()).isZero();
    }

    @Test void persistsWrongAttemptsAndRejectsCorrectCodeAfterFifthFailure() throws Exception {
        assertCode(requestCode(), 200, "REGISTER_CODE_SENT");
        String right = receipt().code();
        String wrong = right.equals("000001") ? "000002" : "000001";
        for (int index = 1; index <= 5; index++) {
            assertCode(register(username, wrong, PASSWORD), 400, index == 5 ? "OTP_ATTEMPTS_EXCEEDED" : "OTP_INVALID");
            assertThat(attempts()).isEqualTo(index);
        }
        assertCode(register(username, right, PASSWORD), 400, "OTP_ATTEMPTS_EXCEEDED");
        assertThat(attempts()).isEqualTo(5);
        assertThat(latestUsed()).isFalse();
        assertThat(countUsers()).isZero();
    }

    @Test void concurrentFirstIssuanceAndConsumptionEachHaveOneWinner() throws Exception {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var issueGate = new CountDownLatch(1);
            var first = executor.submit(() -> { issueGate.await(); return requestCode(); });
            var second = executor.submit(() -> { issueGate.await(); return requestCode(); });
            issueGate.countDown();
            assertThat(List.of(first.get(15, TimeUnit.SECONDS).statusCode(), second.get(15, TimeUnit.SECONDS).statusCode()))
                    .containsExactlyInAnyOrder(200, 429);
            assertThat(countCodes()).isEqualTo(1);
            String code = receipt().code();
            var registerGate = new CountDownLatch(1);
            var left = executor.submit(() -> { registerGate.await(); return register(username, code, PASSWORD); });
            var right = executor.submit(() -> { registerGate.await(); return register(username, code, PASSWORD); });
            registerGate.countDown();
            assertThat(List.of(left.get(15, TimeUnit.SECONDS).statusCode(), right.get(15, TimeUnit.SECONDS).statusCode()))
                    .containsExactlyInAnyOrder(201, 400);
        }
        assertThat(latestUsed()).isTrue();
        assertThat(countUsers()).isEqualTo(1);
    }

    @Test void usernameConflictAndCommitFailureLeaveOtpUsable() throws Exception {
        jdbc.update("""
                INSERT INTO "Users" ("UserName", "Email", "FullName") VALUES (?, ?, 'Other Reader')
                """, "taken-" + username, "other-" + email);
        assertCode(requestCode("taken-" + username), 409, "USERNAME_ALREADY_EXISTS");
        assertCode(requestCode(), 200, "REGISTER_CODE_SENT");
        String code = receipt().code();
        assertCode(register("taken-" + username, code, PASSWORD), 409, "USERNAME_ALREADY_EXISTS");
        assertThat(latestUsed()).isFalse();

        doAnswer(invocation -> {
            Object hash = invocation.callRealMethod();
            jdbc.execute("CREATE TEMP TABLE uc01_commit_fixture (id integer UNIQUE DEFERRABLE INITIALLY DEFERRED) ON COMMIT DROP");
            jdbc.update("INSERT INTO uc01_commit_fixture VALUES (1), (1)");
            return hash;
        }).when(passwords).encode("commit-failure");
        assertCode(register(username, code, "commit-failure"), 500, "INTERNAL_ERROR");
        assertThat(countUsers()).isZero();
        assertThat(latestUsed()).isFalse();
        assertCode(register(username, code, PASSWORD), 201, "REGISTER_SUCCESS");
    }

    private void assertRetryAfter(int seconds) throws Exception {
        var response = requestCode();
        assertCode(response, 429, "OTP_RATE_LIMITED");
        assertThat(response.headers().firstValue("Retry-After")).contains(Integer.toString(seconds));
    }

    private HttpResponse<String> requestCode() throws Exception { return requestCode(username); }
    private HttpResponse<String> requestCode(String requestedUserName) throws Exception {
        return post(REQUEST_CODE_PATH, Map.of("userName", requestedUserName, "email", " " + email.toUpperCase(Locale.ROOT) + " "));
    }

    private HttpResponse<String> register(String name, String code, String password) throws Exception {
        return post(REGISTER_PATH, Map.of("userName", name, "email", email, "fullName", "Reader Test",
                "password", password, "verificationCode", code));
    }

    private HttpResponse<String> post(String path, Map<String, String> body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(12)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private void assertCode(HttpResponse<String> result, int status, String code) {
        assertThat(result.statusCode()).as("HTTP response %s", result.body()).isEqualTo(status);
        assertThat(mapper.readTree(result.body()).path("messageCode").asText()).isEqualTo(code);
    }

    private MailReceipt receipt() throws InterruptedException {
        MailReceipt result = SMTP.deliveries.poll(5, TimeUnit.SECONDS);
        assertThat(result).isNotNull();
        return result;
    }

    private int countCodes() { return jdbc.queryForObject("SELECT count(*)" + CODES, Integer.class, email); }
    private int countUsers() {
        return jdbc.queryForObject("SELECT count(*) FROM \"Users\" WHERE \"Email\" = ?", Integer.class, email);
    }
    private int attempts() {
        return jdbc.queryForObject("SELECT \"AttemptCount\"" + CODES + " ORDER BY \"Id\" DESC LIMIT 1", Integer.class, email);
    }
    private boolean latestUsed() {
        return jdbc.queryForObject("SELECT \"UsedAt\" IS NOT NULL" + CODES + " ORDER BY \"Id\" DESC LIMIT 1", Boolean.class, email);
    }

    private record MailReceipt(String code, int visibleCodes) {}

    /** SMTP loopback: JavaMail gửi qua socket thật, có thể từ chối thư sau DATA. */
    private static final class SmtpFixture implements AutoCloseable {
        private static final Pattern OTP = Pattern.compile("\\b[0-9]{6}\\b");
        private final ServerSocket server;
        private final LinkedBlockingQueue<MailReceipt> deliveries = new LinkedBlockingQueue<>();
        private volatile java.util.function.IntSupplier observe = () -> 0;
        private volatile boolean reject;

        SmtpFixture() {
            try {
                server = new ServerSocket(0, 10, InetAddress.getByName("127.0.0.1"));
            } catch (IOException exception) {
                throw new IllegalStateException("Không mở được SMTP fixture", exception);
            }
            Thread.startVirtualThread(this::listen);
        }

        int port() { return server.getLocalPort(); }
        void clear() { deliveries.clear(); observe = () -> 0; reject = false; }

        private void listen() {
            while (!server.isClosed()) {
                try (var socket = server.accept()) {
                    handle(socket);
                } catch (Exception exception) {
                    if (!server.isClosed()) throw new IllegalStateException("SMTP fixture bị lỗi", exception);
                }
            }
        }

        private void handle(Socket socket) throws Exception {
            socket.setSoTimeout(8000);
            var reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
            var writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII));
            reply(writer, "220 fixture SMTP");
            for (String line; (line = reader.readLine()) != null;) {
                switch (line.split(" ", 2)[0].toUpperCase(Locale.ROOT)) {
                    case "DATA" -> {
                        reply(writer, "354 End data with <CRLF>.<CRLF>");
                        var data = new StringBuilder();
                        while ((line = reader.readLine()) != null && !line.equals(".")) data.append(line).append("\r\n");
                        var message = new MimeMessage(Session.getInstance(new Properties()),
                                new ByteArrayInputStream(data.toString().getBytes(StandardCharsets.US_ASCII)));
                        var matcher = OTP.matcher((String) message.getContent());
                        if (!matcher.find()) throw new IllegalStateException("Thư fixture thiếu OTP");
                        deliveries.add(new MailReceipt(matcher.group(), observe.getAsInt()));
                        reply(writer, reject ? "451 Temporary failure" : "250 Queued");
                    }
                    case "QUIT" -> { reply(writer, "221 Bye"); return; }
                    default -> reply(writer, "250 OK");
                }
            }
        }

        private static void reply(BufferedWriter writer, String reply) throws IOException {
            writer.write(reply + "\r\n");
            writer.flush();
        }

        @Override public void close() throws IOException { server.close(); }
    }
}
