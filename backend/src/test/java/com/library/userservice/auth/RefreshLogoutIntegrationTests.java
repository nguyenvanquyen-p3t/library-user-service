package com.library.userservice.auth;

import com.library.userservice.auth.persistence.RefreshTokenRepository;
import com.library.userservice.security.RefreshTokenGenerator;
import com.library.userservice.security.UserAccessTokenService;
import com.library.userservice.security.UserPrincipal;
import com.library.userservice.security.UserSecurityTestKeys;
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
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
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
import static org.mockito.Mockito.*;

/** HTTP/commit/khóa PostgreSQL thật, chỉ trên UserDB_test; dọn đúng ID fixture. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "debug=false")
@Import(RefreshLogoutIntegrationTests.FixtureController.class)
@EnabledIfSystemProperty(named = "spring.datasource.url", matches = "jdbc:postgresql://[^/]+/UserDB_test(?:\\?.*)?")
class RefreshLogoutIntegrationTests extends UserSecurityTestKeys {
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired RefreshTokenGenerator generator;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired JsonMapper mapper;
    @MockitoSpyBean UserAccessTokenService jwt;
    @MockitoSpyBean RefreshTokenRepository tokens;
    @LocalServerPort int port;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final List<Integer> fixtureIds = new ArrayList<>();
    private String passwordHash;

    @AfterEach void cleanup() {
        reset(jwt, tokens);
        for (int id : fixtureIds) jdbc.update("DELETE FROM \"Users\" WHERE \"Id\" = ?", id);
    }

    @Test void rotatesWithinSessionAndLogoutByOldTokenRevokesSuccessorsOnly() throws Exception {
        var fixture = user();
        var first = login(fixture); var other = login(fixture);
        UUID sid = sid(first);
        var before = session(sid);
        Instant issuedAfter = Instant.now();
        var response = post("refresh", first.refresh());
        assertCode(response, 200, "TOKEN_REFRESH_SUCCESS");
        var second = pair(response);
        assertThat(sid(second)).isEqualTo(sid);
        assertThat(session(sid)).isEqualTo(before);
        assertThat(count(fixture.id())).isEqualTo(3);
        int oldId = tokenId(first.refresh()), nextId = tokenId(second.refresh());
        assertThat(nextId).isGreaterThan(oldId);
        var old = tokenState(oldId);
        assertThat(old.get("ReplacedByTokenId")).isEqualTo(nextId);
        assertThat(old.get("RevokedAt")).isNotNull();
        var expiry = jdbc.queryForObject("SELECT \"ExpiresAt\" FROM \"RefreshTokens\" WHERE \"Id\" = ?", java.sql.Timestamp.class, nextId).toInstant();
        assertThat(expiry).isBetween(issuedAfter.plusSeconds(604800), Instant.now().plusSeconds(604800));
        assertThat(jdbc.queryForObject("SELECT \"TokenHash\" FROM \"RefreshTokens\" WHERE \"Id\" = ?", String.class, nextId)
                .equals(generator.hash(second.refresh()))).isTrue();
        var claims = jwt.verify(second.access());
        assertThat(claims.getExpiresAt()).isEqualTo(claims.getIssuedAt().plusSeconds(3600));
        assertCode(post("refresh", first.refresh()), 401, "REFRESH_TOKEN_INVALID");
        assertThat(principal(first.access()).statusCode()).isEqualTo(200);
        var thirdResponse = post("refresh", second.refresh()); assertCode(thirdResponse, 200, "TOKEN_REFRESH_SUCCESS");
        var third = pair(thirdResponse);
        assertCode(post("logout", first.refresh()), 200, "LOGOUT_SUCCESS");
        var loggedOut = session(sid);
        assertThat(loggedOut.get("RevocationReason")).isEqualTo("LOGOUT");
        assertThat(loggedOut.get("RevokedAt")).isNotNull();
        assertThat(tokenState(oldId)).isEqualTo(old);
        assertThat(tokenState(tokenId(third.refresh())).get("RevokedAt")).isNotNull();
        assertThat(count(fixture.id())).isEqualTo(4);
        for (Pair pair : List.of(first, second, third)) assertCode(principal(pair.access()), 401, "SESSION_INACTIVE");
        assertCode(post("refresh", third.refresh()), 401, "REFRESH_TOKEN_INVALID");
        assertCode(post("logout", third.refresh()), 200, "LOGOUT_SUCCESS");
        assertThat(session(sid)).isEqualTo(loggedOut);
        assertThat(principal(other.access()).statusCode()).isEqualTo(200);
        assertCode(post("refresh", other.refresh()), 200, "TOKEN_REFRESH_SUCCESS");
    }

    @Test void refreshRejectsExpiredTokensSessionsAndBannedUsersWithConfirmedPrecedence() throws Exception {
        var fixture = user();
        var expired = login(fixture); expireToken(expired.refresh());
        assertCode(post("refresh", expired.refresh()), 401, "REFRESH_TOKEN_INVALID");
        var ended = login(fixture); expireSession(sid(ended));
        assertCode(post("refresh", ended.refresh()), 401, "SESSION_INACTIVE");
        var revoked = login(fixture);
        jdbc.update("UPDATE \"UserSessions\" SET \"RevokedAt\" = CURRENT_TIMESTAMP, \"RevocationReason\" = 'PASSWORD_RESET' WHERE \"Id\" = ?", sid(revoked));
        assertCode(post("refresh", revoked.refresh()), 401, "SESSION_INACTIVE");
        var active = login(fixture);
        jdbc.update("UPDATE \"Users\" SET \"Status\" = 'BANNED' WHERE \"Id\" = ?", fixture.id());
        assertCode(post("refresh", active.refresh()), 403, "ACCOUNT_BANNED");
        assertCode(post("refresh", ended.refresh()), 403, "ACCOUNT_BANNED");
        assertCode(post("refresh", expired.refresh()), 401, "REFRESH_TOKEN_INVALID");
        assertThat(count(fixture.id())).isEqualTo(4);
        // Logout vẫn được thực hiện khi user BANNED.
        assertCode(post("logout", active.refresh()), 200, "LOGOUT_SUCCESS");
        assertCode(post("refresh", active.refresh()), 401, "REFRESH_TOKEN_INVALID");
    }

    @Test void logoutAcceptsExpiredTokenAndPreservesExpiredAndPreviouslyRevokedTokenHistory() throws Exception {
        var fixture = user(); var first = login(fixture);
        var rotated = post("refresh", first.refresh()); assertCode(rotated, 200, "TOKEN_REFRESH_SUCCESS");
        var second = pair(rotated); int oldId = tokenId(first.refresh());
        expireToken(first.refresh());
        var old = tokenState(oldId);
        int expiredId = jdbc.queryForObject("""
                INSERT INTO "RefreshTokens" ("UserId", "SessionId", "TokenHash", "CreatedAt", "ExpiresAt")
                VALUES (?, ?, ?, CURRENT_TIMESTAMP - INTERVAL '8 days', CURRENT_TIMESTAMP - INTERVAL '1 second') RETURNING "Id"
                """, Integer.class, fixture.id(), sid(first), generator.hash(generator.generate()));
        assertCode(post("logout", first.refresh()), 200, "LOGOUT_SUCCESS");
        assertThat(tokenState(oldId)).isEqualTo(old);
        assertThat(tokenState(expiredId).get("RevokedAt")).isNull();
        assertThat(tokenState(tokenId(second.refresh())).get("RevokedAt")).isNotNull();
        assertCode(principal(second.access()), 401, "SESSION_INACTIVE");
    }

    @Test void logoutUnknownTokenOrEndedSessionIsIdempotentWithoutChangingHistory() throws Exception {
        assertCode(post("logout", generator.generate()), 200, "LOGOUT_SUCCESS");
        var fixture = user(); var expired = login(fixture); expireSession(sid(expired));
        var before = session(sid(expired)); var token = tokenState(tokenId(expired.refresh()));
        assertCode(post("logout", expired.refresh()), 200, "LOGOUT_SUCCESS");
        assertThat(session(sid(expired))).isEqualTo(before);
        assertThat(tokenState(tokenId(expired.refresh()))).isEqualTo(token);
        var revoked = login(fixture);
        jdbc.update("UPDATE \"UserSessions\" SET \"RevokedAt\" = CURRENT_TIMESTAMP, \"RevocationReason\" = 'PASSWORD_CHANGED' WHERE \"Id\" = ?", sid(revoked));
        before = session(sid(revoked));
        assertCode(post("logout", revoked.refresh()), 200, "LOGOUT_SUCCESS");
        assertThat(session(sid(revoked))).isEqualTo(before);
    }

    @Test void refreshRollsBackInsertRevocationSigningAndCommitFailures() throws Exception {
        var fixture = user(); var first = login(fixture); int id = tokenId(first.refresh());
        var original = tokenState(id); var originalSession = session(sid(first));
        doThrow(new DataAccessResourceFailureException("fixture-save-failure")).when(tokens).save(any());
        assertCode(post("refresh", first.refresh()), 503, "SERVICE_UNAVAILABLE");
        assertUnchanged(fixture, first, original, originalSession); reset(tokens);

        doAnswer(call -> {
            com.library.userservice.auth.persistence.RefreshToken token = call.getArgument(0);
            if (token.getId() != null) throw new DataAccessResourceFailureException("fixture-old-save-failure");
            return callRepository(call);
        }).when(tokens).save(any());
        assertCode(post("refresh", first.refresh()), 503, "SERVICE_UNAVAILABLE");
        assertUnchanged(fixture, first, original, originalSession); reset(tokens);

        doThrow(new IllegalStateException("fixture-sign-failure")).when(jwt).issue(anyInt(), any(UUID.class));
        assertCode(post("refresh", first.refresh()), 500, "INTERNAL_ERROR");
        assertUnchanged(fixture, first, original, originalSession); reset(jwt);

        doAnswer(call -> { var result = call.callRealMethod(); failAtCommit(); return result; }).when(jwt).issue(anyInt(), any(UUID.class));
        assertCode(post("refresh", first.refresh()), 500, "INTERNAL_ERROR");
        assertUnchanged(fixture, first, original, originalSession);
    }

    @Test void logoutRollsBackBothSessionAndTokensOnUpdateAndCommitFailures() throws Exception {
        var fixture = user(); var first = login(fixture);
        var original = tokenState(tokenId(first.refresh())); var originalSession = session(sid(first));
        doAnswer(call -> { callRepository(call); throw new DataAccessResourceFailureException("fixture-logout-failure"); })
                .when(tokens).revokeActiveBySession(any(), anyInt(), any());
        assertCode(post("logout", first.refresh()), 503, "SERVICE_UNAVAILABLE");
        assertUnchanged(fixture, first, original, originalSession); reset(tokens);
        doAnswer(call -> { var result = callRepository(call); failAtCommit(); return result; })
                .when(tokens).revokeActiveBySession(any(), anyInt(), any());
        assertCode(post("logout", first.refresh()), 500, "INTERNAL_ERROR");
        assertUnchanged(fixture, first, original, originalSession); reset(tokens);
        assertThat(principal(first.access()).statusCode()).isEqualTo(200);
        assertCode(post("refresh", first.refresh()), 200, "TOKEN_REFRESH_SUCCESS");
    }

    @Test void twoSimultaneousRefreshesHaveExactlyOneSuccess() throws Exception {
        var fixture = user(); var first = login(fixture);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var start = new CountDownLatch(1);
            var a = executor.submit(() -> { await(start); return post("refresh", first.refresh()); });
            var b = executor.submit(() -> { await(start); return post("refresh", first.refresh()); });
            start.countDown();
            var ar = a.get(15, TimeUnit.SECONDS); var br = b.get(15, TimeUnit.SECONDS);
            assertThat(List.of(ar.statusCode(), br.statusCode())).containsExactlyInAnyOrder(200, 401);
            var success = ar.statusCode() == 200 ? ar : br;
            assertCode(ar.statusCode() == 401 ? ar : br, 401, "REFRESH_TOKEN_INVALID");
            assertThat(count(fixture.id())).isEqualTo(2);
            assertThat(session(sid(first)).get("RevokedAt")).isNull();
            assertCode(post("refresh", pair(success).refresh()), 200, "TOKEN_REFRESH_SUCCESS");
        }
    }

    @Test void refreshAndLogoutSerializeInBothOrdersWithoutLeavingUsableSuccessor() throws Exception {
        for (String firstOperation : List.of("refresh", "logout")) {
            var fixture = user(); var first = login(fixture);
            var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
            var once = new AtomicBoolean();
            doAnswer(call -> {
                var result = callRepository(call);
                if (once.compareAndSet(false, true)) { entered.countDown(); await(release); }
                return result;
            }).when(tokens).findByTokenHash(generator.hash(first.refresh()));
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                var a = executor.submit(() -> post(firstOperation, first.refresh()));
                try {
                    assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                    var b = executor.submit(() -> post(firstOperation.equals("refresh") ? "logout" : "refresh", first.refresh()));
                    assertUserLockWait(); assertThat(b.isDone()).isFalse();
                    release.countDown();
                    var ar = a.get(15, TimeUnit.SECONDS); var br = b.get(15, TimeUnit.SECONDS);
                    assertCode(ar, 200, firstOperation.equals("refresh") ? "TOKEN_REFRESH_SUCCESS" : "LOGOUT_SUCCESS");
                    assertCode(br, firstOperation.equals("refresh") ? 200 : 401,
                            firstOperation.equals("refresh") ? "LOGOUT_SUCCESS" : "REFRESH_TOKEN_INVALID");
                    if (firstOperation.equals("refresh")) {
                        var replacement = pair(ar);
                        assertCode(post("refresh", replacement.refresh()), 401, "REFRESH_TOKEN_INVALID");
                        assertCode(principal(replacement.access()), 401, "SESSION_INACTIVE");
                    }
                    assertThat(session(sid(first)).get("RevocationReason")).isEqualTo("LOGOUT");
                } finally { release.countDown(); }
            } finally { reset(tokens); }
        }
    }

    @Test void refreshWaitsForUserWriterAndReadsCommittedAccountOrSessionState() throws Exception {
        for (boolean ban : List.of(true, false)) {
            var fixture = user(); var first = login(fixture);
            var locked = new CountDownLatch(1); var release = new CountDownLatch(1);
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                var writer = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                    jdbc.queryForObject("SELECT \"Id\" FROM \"Users\" WHERE \"Id\" = ? FOR UPDATE", Integer.class, fixture.id());
                    if (ban) jdbc.update("UPDATE \"Users\" SET \"Status\" = 'BANNED' WHERE \"Id\" = ?", fixture.id());
                    else jdbc.update("UPDATE \"UserSessions\" SET \"RevokedAt\" = CURRENT_TIMESTAMP, \"RevocationReason\" = 'PASSWORD_RESET' WHERE \"Id\" = ?", sid(first));
                    locked.countDown(); await(release); return null;
                }));
                try {
                    assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
                    var pending = executor.submit(() -> post("refresh", first.refresh()));
                    assertUserLockWait(); assertThat(pending.isDone()).isFalse(); release.countDown();
                    writer.get(10, TimeUnit.SECONDS);
                    assertCode(pending.get(15, TimeUnit.SECONDS), ban ? 403 : 401, ban ? "ACCOUNT_BANNED" : "SESSION_INACTIVE");
                    assertThat(count(fixture.id())).isEqualTo(1);
                    assertThat(tokenState(tokenId(first.refresh())).get("RevokedAt")).isNull();
                } finally { release.countDown(); }
            }
        }
    }

    private void assertUserLockWait() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (Boolean.TRUE.equals(jdbc.queryForObject("""
                    SELECT EXISTS (SELECT 1 FROM pg_stat_activity WHERE datname = current_database()
                    AND pid <> pg_backend_pid() AND wait_event_type = 'Lock'
                    AND query LIKE '%Users%' AND query LIKE '%for%update%')
                    """, Boolean.class))) return;
            Thread.sleep(20);
        }
        fail("Không quan sát được request chờ khóa user");
    }

    private void await(CountDownLatch latch) {
        try { if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("fixture-lock-timeout"); }
        catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException("fixture-interrupted"); }
    }

    private void failAtCommit() {
        jdbc.execute("CREATE TEMP TABLE uc0304_commit_fixture (id integer UNIQUE DEFERRABLE INITIALLY DEFERRED) ON COMMIT DROP");
        jdbc.update("INSERT INTO uc0304_commit_fixture VALUES (1), (1)");
    }

    // Spring Data là JDK proxy; default answer của spy chuyển tiếp tới repository thật.
    private Object callRepository(org.mockito.invocation.InvocationOnMock call) throws Throwable {
        return mockingDetails(tokens).getMockCreationSettings().getDefaultAnswer().answer(call);
    }

    private void assertUnchanged(Fixture fixture, Pair pair, Map<String, Object> token, Map<String, Object> session) {
        assertThat(count(fixture.id())).isEqualTo(1);
        assertThat(tokenState(tokenId(pair.refresh()))).isEqualTo(token);
        assertThat(session(sid(pair))).isEqualTo(session);
    }

    private Fixture user() {
        if (passwordHash == null) passwordHash = passwords.encode("UC0304 fixture password");
        String name = "uc0304-" + UUID.randomUUID();
        int id = jdbc.queryForObject("""
                INSERT INTO "Users" ("UserName", "Email", "PasswordHash", "FullName")
                VALUES (?, ?, ?, 'UC0304 Fixture') RETURNING "Id"
                """, Integer.class, name, name + "@example.com", passwordHash);
        fixtureIds.add(id); return new Fixture(id, name);
    }

    private Pair login(Fixture fixture) throws Exception {
        var response = send("/api/v1/auth/login", Map.of("identifier", fixture.name(), "password", "UC0304 fixture password"));
        assertCode(response, 200, "LOGIN_SUCCESS"); return pair(response);
    }
    private int count(int userId) { return jdbc.queryForObject("SELECT count(*) FROM \"RefreshTokens\" WHERE \"UserId\" = ?", Integer.class, userId); }
    private int tokenId(String token) { return jdbc.queryForObject("SELECT \"Id\" FROM \"RefreshTokens\" WHERE \"TokenHash\" = ?", Integer.class, generator.hash(token)); }
    private UUID sid(Pair pair) { return UUID.fromString(jwt.verify(pair.access()).getClaimAsString("sid")); }
    private Map<String, Object> tokenState(int id) { return jdbc.queryForMap("SELECT \"Id\", \"RevokedAt\", \"ReplacedByTokenId\" FROM \"RefreshTokens\" WHERE \"Id\" = ?", id); }
    private Map<String, Object> session(UUID sid) { return jdbc.queryForMap("SELECT * FROM \"UserSessions\" WHERE \"Id\" = ?", sid); }
    private void expireToken(String token) { jdbc.update("UPDATE \"RefreshTokens\" SET \"CreatedAt\" = CURRENT_TIMESTAMP - INTERVAL '8 days', \"ExpiresAt\" = CURRENT_TIMESTAMP - INTERVAL '1 second' WHERE \"Id\" = ?", tokenId(token)); }
    private void expireSession(UUID sid) { jdbc.update("UPDATE \"UserSessions\" SET \"CreatedAt\" = CURRENT_TIMESTAMP - INTERVAL '31 days', \"LastSeenAt\" = CURRENT_TIMESTAMP - INTERVAL '31 days', \"ExpiresAt\" = CURRENT_TIMESTAMP - INTERVAL '1 second' WHERE \"Id\" = ?", sid); }
    private Pair pair(HttpResponse<String> response) throws Exception {
        var data = mapper.readTree(response.body()).path("data");
        assertThat(data.size()).isEqualTo(4);
        return new Pair(data.path("accessToken").asText(), data.path("refreshToken").asText());
    }
    private HttpResponse<String> post(String operation, String token) throws Exception { return send("/api/v1/auth/" + operation, Map.of("refreshToken", token)); }
    private HttpResponse<String> send(String path, Object body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json").header("Authorization", "Bearer stale-fixture")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build(), HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> principal(String access) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/__refresh-test/principal"))
                .timeout(Duration.ofSeconds(15)).header("Authorization", "Bearer " + access).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    private void assertCode(HttpResponse<String> response, int status, String code) throws Exception {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(mapper.readTree(response.body()).path("messageCode").asText()).isEqualTo(code);
        assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
        assertThat(response.headers().firstValue("Set-Cookie")).isEmpty();
        if (code.equals("LOGOUT_SUCCESS")) assertThat(mapper.readTree(response.body()).path("data").isNull()).isTrue();
    }
    private record Fixture(int id, String name) {}
    private record Pair(String access, String refresh) {
        @Override public String toString() { return "Pair[REDACTED]"; }
    }
    @RestController
    @TestComponent
    @Hidden
    static class FixtureController {
        @GetMapping("/__refresh-test/principal") UserPrincipal principal(Authentication auth) { return (UserPrincipal) auth.getPrincipal(); }
    }
}
