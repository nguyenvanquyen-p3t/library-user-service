package com.library.userservice.auth;

import com.library.userservice.auth.persistence.RefreshTokenRepository;
import com.library.userservice.security.RefreshTokenGenerator;
import com.library.userservice.security.UserAccessTokenService;
import com.library.userservice.security.UserPrincipal;
import com.library.userservice.security.UserSecurityTestKeys;
import com.library.userservice.session.persistence.SessionCreationRepository;
import io.swagger.v3.oas.annotations.Hidden;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Có commit thật: chỉ bật với UserDB_test; dọn đúng user fixture sau từng test. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "debug=false")
@Import(LoginIntegrationTests.FixtureController.class)
@EnabledIfSystemProperty(named = "spring.datasource.url", matches = "jdbc:postgresql://[^/]+/UserDB_test(?:\\?.*)?")
class LoginIntegrationTests extends UserSecurityTestKeys {
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired RefreshTokenGenerator refresh;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired JsonMapper mapper;
    @MockitoSpyBean UserAccessTokenService accessTokens;
    @MockitoSpyBean RefreshTokenRepository refreshTokens;
    @MockitoSpyBean SessionCreationRepository sessionCreator;
    @LocalServerPort int port;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final List<Integer> fixtureIds = new ArrayList<>();
    private String hash;
    private static final String PASSWORD = "  mật khẩu " + "密".repeat(90);

    @BeforeEach void setup() { hash = passwords.encode(PASSWORD); }

    @AfterEach void cleanup() {
        reset(accessTokens, refreshTokens, sessionCreator);
        for (int id : fixtureIds) jdbc.update("DELETE FROM \"Users\" WHERE \"Id\" = ?", id);
    }

    @Test void commitsNewSessionAndHashedRefreshForEachUsernameOrEmailLogin() throws Exception {
        var fixture = user(hash, "ACTIVE");
        Instant before = Instant.now();
        var first = login("  " + fixture.name().toUpperCase() + "  ", PASSWORD);
        assertCode(first, 200, "LOGIN_SUCCESS");
        assertThat(first.headers().firstValue("Cache-Control")).contains("no-store");
        assertThat(first.headers().firstValue("Set-Cookie")).isEmpty();
        var data = mapper.readTree(first.body()).path("data");
        assertThat(data.size()).isEqualTo(4);
        assertThat(data.path("tokenType").asText()).isEqualTo("Bearer");
        assertThat(data.path("expiresIn").asInt()).isEqualTo(3600);
        var jwt = accessTokens.verify(data.path("accessToken").asText());
        assertThat(jwt.getSubject()).isEqualTo(Integer.toString(fixture.id()));
        UUID sid = UUID.fromString(jwt.getClaimAsString("sid"));
        assertThat(count("UserSessions", fixture.id())).isEqualTo(1);
        assertThat(count("RefreshTokens", fixture.id())).isEqualTo(1);
        var session = jdbc.queryForMap("SELECT * FROM \"UserSessions\" WHERE \"Id\" = ?", sid);
        assertThat(session.get("UserId")).isEqualTo(fixture.id());
        var created = ((java.sql.Timestamp) session.get("CreatedAt")).toInstant();
        assertThat(((java.sql.Timestamp) session.get("ExpiresAt")).toInstant()).isEqualTo(created.plusSeconds(2592000));
        assertThat(session.get("LastSeenAt")).isEqualTo(session.get("CreatedAt"));
        for (String field : List.of("RevokedAt", "RevocationReason", "DeviceName", "ClientType", "UserAgent")) assertThat(session.get(field)).isNull();
        var savedToken = jdbc.queryForMap("SELECT * FROM \"RefreshTokens\" WHERE \"SessionId\" = ?", sid);
        assertThat(savedToken.get("TokenHash").equals(refresh.hash(data.path("refreshToken").asText()))).isTrue();
        assertThat(savedToken.get("TokenHash").equals(data.path("refreshToken").asText())).isFalse();
        assertThat(((java.sql.Timestamp) savedToken.get("ExpiresAt")).toInstant()).isBetween(before.plusSeconds(604800), Instant.now().plusSeconds(604800));
        assertThat(savedToken.get("RevokedAt")).isNull(); assertThat(savedToken.get("ReplacedByTokenId")).isNull();
        assertThat(get("/__login-test/principal", data.path("accessToken").asText()).statusCode()).isEqualTo(200);

        var second = login(fixture.name().toUpperCase() + "@EXAMPLE.COM", PASSWORD);
        assertCode(second, 200, "LOGIN_SUCCESS");
        var next = accessTokens.verify(mapper.readTree(second.body()).path("data").path("accessToken").asText());
        assertThat(next.getClaimAsString("sid")).isNotEqualTo(sid.toString());
        assertThat(count("UserSessions", fixture.id())).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT \"PasswordHash\" FROM \"Users\" WHERE \"Id\" = ?", String.class, fixture.id()).equals(hash)).isTrue();
    }

    @Test void rejectsUnknownWrongPasswordPasswordlessBannedAndCorruptHashWithoutWrites() throws Exception {
        var active = user(hash, "ACTIVE"); var banned = user(hash, "BANNED");
        var passwordless = user(null, "ACTIVE"); var malformed = user("{argon2id}$argon2id$broken-fixture", "ACTIVE");
        assertCode(login("missing-" + UUID.randomUUID(), PASSWORD), 401, "INVALID_CREDENTIALS");
        assertCode(login(active.name(), "wrong"), 401, "INVALID_CREDENTIALS");
        assertCode(login(banned.name(), "wrong"), 401, "INVALID_CREDENTIALS");
        assertCode(login(banned.name(), PASSWORD), 403, "ACCOUNT_BANNED");
        assertCode(login(passwordless.name(), PASSWORD), 409, "PASSWORD_NOT_CONFIGURED");
        assertCode(login(malformed.name(), PASSWORD), 401, "INVALID_CREDENTIALS");
        for (int id : fixtureIds) { assertThat(count("UserSessions", id)).isZero(); assertThat(count("RefreshTokens", id)).isZero(); }
    }

    @Test void rollsBackSessionAndTokenWhenPersistenceSigningOrDatabaseCommitFails() throws Exception {
        var fixture = user(hash, "ACTIVE");
        doThrow(new DataAccessResourceFailureException("fixture-unavailable")).when(refreshTokens).save(any());
        assertCode(login(fixture.name(), PASSWORD), 503, "SERVICE_UNAVAILABLE");
        assertNoSessions(fixture.id()); reset(refreshTokens);

        doThrow(new IllegalStateException("fixture-signing-failure")).when(accessTokens).issue(anyInt(), any(UUID.class));
        assertCode(login(fixture.name(), PASSWORD), 500, "INTERNAL_ERROR");
        assertNoSessions(fixture.id()); reset(accessTokens);

        // Constraint chỉ kiểm tra tại COMMIT: chứng minh controller không báo thành công trước commit.
        doAnswer(invocation -> {
            invocation.callRealMethod();
            jdbc.execute("CREATE TEMP TABLE uc02_commit_fixture (id integer UNIQUE DEFERRABLE INITIALLY DEFERRED) ON COMMIT DROP");
            jdbc.update("INSERT INTO uc02_commit_fixture VALUES (1), (1)");
            return null;
        }).when(sessionCreator).create(any(UUID.class), anyInt());
        assertCode(login(fixture.name(), PASSWORD), 500, "INTERNAL_ERROR");
        assertNoSessions(fixture.id());
    }

    @Test void simultaneousLoginsCreateIndependentSessions() throws Exception {
        var fixture = user(hash, "ACTIVE");
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var gate = new CountDownLatch(1);
            var first = executor.submit(() -> { gate.await(); return login(fixture.name(), PASSWORD); });
            var second = executor.submit(() -> { gate.await(); return login(fixture.name(), PASSWORD); });
            gate.countDown();
            assertCode(first.get(15, TimeUnit.SECONDS), 200, "LOGIN_SUCCESS");
            assertCode(second.get(15, TimeUnit.SECONDS), 200, "LOGIN_SUCCESS");
            assertThat(count("UserSessions", fixture.id())).isEqualTo(2);
            assertThat(count("RefreshTokens", fixture.id())).isEqualTo(2);
        }
    }

    @Test void loginWaitsForUserWriterAndRechecksCommittedStatus() throws Exception {
        var fixture = user(hash, "ACTIVE");
        var locked = new CountDownLatch(1); var release = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var writer = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                jdbc.update("UPDATE \"Users\" SET \"Status\" = 'BANNED' WHERE \"Id\" = ?", fixture.id());
                locked.countDown();
                try { if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("fixture-lock-timeout"); }
                catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException(); }
                return null;
            }));
            try {
                assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
                var pending = executor.submit(() -> login(fixture.name(), PASSWORD));
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                boolean waiting = false;
                while (System.nanoTime() < deadline) {
                    waiting = Boolean.TRUE.equals(jdbc.queryForObject("""
                            SELECT EXISTS (SELECT 1 FROM pg_stat_activity WHERE datname = current_database()
                            AND pid <> pg_backend_pid() AND wait_event_type = 'Lock'
                            AND query LIKE '%Users%' AND query LIKE '%for%update%')
                            """, Boolean.class));
                    if (waiting) break;
                    Thread.sleep(20);
                }
                assertThat(waiting).isTrue(); assertThat(pending.isDone()).isFalse();
                release.countDown(); writer.get(5, TimeUnit.SECONDS);
                assertCode(pending.get(10, TimeUnit.SECONDS), 403, "ACCOUNT_BANNED");
                assertNoSessions(fixture.id());
            } finally { release.countDown(); }
        }
    }

    @Test void fullContextPreservesTwentyOpenApiOperationsAndFiveImplementedOperations() throws Exception {
        var response = get("/v3/api-docs", null);
        assertThat(response.statusCode()).isEqualTo(200);
        var paths = mapper.readTree(response.body()).path("paths"); int count = 0;
        for (var path : paths) for (var operation : path) count++;
        assertThat(count).isEqualTo(20);
        assertThat(paths.path("/api/v1/auth/login").path("post").path("x-implementation-status").asText()).isEqualTo("IMPLEMENTED");
        assertThat(paths.path("/api/v1/auth/refresh").path("post").path("x-implementation-status").asText()).isEqualTo("IMPLEMENTED");
        assertThat(paths.path("/api/v1/auth/logout").path("post").path("x-implementation-status").asText()).isEqualTo("IMPLEMENTED");
        assertThat(paths.path("/api/v1/users/me").path("get").path("x-implementation-status").asText()).isEqualTo("IMPLEMENTED");
        assertThat(paths.path("/api/v1/users/me").path("patch").path("x-implementation-status").asText()).isEqualTo("IMPLEMENTED");
    }

    private Fixture user(String passwordHash, String status) {
        String name = "uc02-" + UUID.randomUUID();
        int id = jdbc.queryForObject("""
                INSERT INTO "Users" ("UserName", "Email", "PasswordHash", "FullName", "Status")
                VALUES (?, ?, ?, 'UC02 Fixture', ?) RETURNING "Id"
                """, Integer.class, name, name + "@example.com", passwordHash, status);
        fixtureIds.add(id);
        jdbc.update("INSERT INTO \"UserRoles\" SELECT ?, \"Id\" FROM \"Roles\" WHERE \"Name\" = 'READER'", id);
        return new Fixture(id, name);
    }
    private int count(String table, int id) { return jdbc.queryForObject("SELECT count(*) FROM \"" + table + "\" WHERE \"UserId\" = ?", Integer.class, id); }
    private void assertNoSessions(int id) { assertThat(count("UserSessions", id)).isZero(); assertThat(count("RefreshTokens", id)).isZero(); }
    private HttpResponse<String> login(String identifier, String password) throws Exception {
        String json = mapper.writeValueAsString(Map.of("identifier", identifier, "password", password));
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/auth/login"))
                .timeout(Duration.ofSeconds(15)).header("Content-Type", "application/json").header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json)).build(), HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> get(String path, String token) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).header("Accept", "application/json");
        if (token != null) builder.header("Authorization", "Bearer " + token);
        return client.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    private void assertCode(HttpResponse<String> response, int status, String code) throws Exception {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(mapper.readTree(response.body()).path("messageCode").asText()).isEqualTo(code);
    }
    private record Fixture(int id, String name) {}

    @RestController
    @TestComponent
    @Hidden
    static class FixtureController {
        @GetMapping("/__login-test/principal") UserPrincipal principal(Authentication auth) { return (UserPrincipal) auth.getPrincipal(); }
    }
}
