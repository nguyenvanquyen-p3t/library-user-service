package com.library.userservice.user;

import com.library.userservice.auth.dto.LoginRequest;
import com.library.userservice.auth.dto.RefreshTokenRequest;
import com.library.userservice.auth.persistence.RefreshTokenRepository;
import com.library.userservice.security.PasswordService;
import com.library.userservice.security.RefreshTokenGenerator;
import com.library.userservice.security.UserAccessTokenService;
import com.library.userservice.security.UserSecurityTestKeys;
import com.library.userservice.session.persistence.UserSessionRepository;
import com.library.userservice.user.dto.ChangePasswordRequest;
import com.library.userservice.user.persistence.UserRepository;
import jakarta.persistence.EntityManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** HTTP/COMMIT/khóa PostgreSQL thật; chỉ UserDB_test và khóa RSA tạm. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "debug=false")
@EnabledIfSystemProperty(named = "spring.datasource.url", matches = "jdbc:postgresql://[^/]+/UserDB_test(?:\\?.*)?")
class ChangePasswordIntegrationTests extends UserSecurityTestKeys {
    private static final String OLD = "old-uc07-test-only";
    private static final String NEXT = "  mật khẩu mới 密  ";
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired JsonMapper mapper;
    @Autowired UserAccessTokenService jwt;
    @Autowired RefreshTokenGenerator generator;
    @MockitoSpyBean UserRepository users;
    @MockitoSpyBean UserSessionRepository sessions;
    @MockitoSpyBean RefreshTokenRepository tokens;
    @MockitoSpyBean PasswordService passwords;
    @MockitoSpyBean Clock clock;
    @LocalServerPort int port;
    private final HttpClient client = HttpClient.newHttpClient();
    private final List<Integer> fixtureIds = new ArrayList<>();

    @AfterEach void cleanup() {
        reset(users, sessions, tokens, passwords, clock);
        for (int id : fixtureIds) jdbc.update("DELETE FROM \"Users\" WHERE \"Id\" = ?", id);
    }

    @Test void commitsHashAndRevokesOnlyActiveOwnedRecordsPreservingHistoryAndGoogleIdentity() throws Exception {
        var a = fixture(); var other = fixture(); var otherBefore = snapshot(other.id());
        var second = addSession(a.id(), "active"); var expired = addSession(a.id(), "expired");
        var revoked = addSession(a.id(), "revoked");
        addToken(a.id(), second, false); addToken(a.id(), expired, false); addToken(a.id(), revoked, false);
        String expiredToken = addToken(a.id(), a.sid(), true);
        var rotated = refresh(a.refresh()); assertCode(rotated, 200, "TOKEN_REFRESH_SUCCESS");
        var pair = mapper.readTree(rotated.body()).path("data"); String replacement = pair.path("refreshToken").asText();
        var oldTokenBefore = token(a.refresh()); var expiredTokenBefore = token(expiredToken);
        var expiredBefore = session(expired); var revokedBefore = session(revoked);
        jdbc.update("INSERT INTO \"UserIdentities\" (\"UserId\", \"Provider\", \"ProviderUserId\") VALUES (?, 'GOOGLE', ?)", a.id(), UUID.randomUUID().toString());
        var identities = jdbc.queryForList("SELECT * FROM \"UserIdentities\" WHERE \"UserId\" = ?", a.id());
        var roles = jdbc.queryForList("SELECT * FROM \"UserRoles\" WHERE \"UserId\" = ?", a.id());
        var before = snapshot(a.id());
        assertCode(change(a, NEXT), 200, "PASSWORD_CHANGE_SUCCESS");
        var after = snapshot(a.id()); String hash = (String) after.user().get("PasswordHash");
        assertThat(passwords.matches(NEXT, hash)).isTrue(); assertThat(passwords.matches(OLD, hash)).isFalse();
        assertThat(passwords.matches(NEXT.strip(), hash)).isFalse();
        assertThat(hash.startsWith("{argon2id}$argon2id$v=19$m=19456,t=2,p=1$")).isTrue();
        assertThat(instant(after.user().get("UpdatedAt"))).isAfter(instant(before.user().get("UpdatedAt")));
        for (String key : before.user().keySet()) if (!Set.of("PasswordHash", "UpdatedAt").contains(key))
            assertThat(java.util.Objects.equals(after.user().get(key), before.user().get(key))).isTrue();
        for (UUID id : List.of(a.sid(), second)) {
            assertThat(session(id).get("RevocationReason")).isEqualTo("PASSWORD_CHANGED");
            assertThat(session(id).get("RevokedAt")).isNotNull();
        }
        assertThat(session(a.sid()).get("RevokedAt")).isEqualTo(session(second).get("RevokedAt"));
        for (int i = 0; i < before.sessions().size(); i++) {
            var previous = before.sessions().get(i); var current = after.sessions().get(i);
            for (String key : previous.keySet()) if (!Set.of("RevokedAt", "RevocationReason").contains(key))
                assertThat(java.util.Objects.equals(current.get(key), previous.get(key))).isTrue();
        }
        assertThat(session(expired).equals(expiredBefore)).isTrue(); assertThat(session(revoked).equals(revokedBefore)).isTrue();
        assertThat(token(a.refresh()).equals(oldTokenBefore)).isTrue(); assertThat(token(expiredToken).equals(expiredTokenBefore)).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM \"RefreshTokens\" WHERE \"UserId\" = ? AND \"RevokedAt\" IS NULL AND \"ExpiresAt\" > CURRENT_TIMESTAMP", Long.class, a.id())).isZero();
        for (int i = 0; i < before.tokens().size(); i++) {
            var previous = before.tokens().get(i); var current = after.tokens().get(i);
            for (String key : previous.keySet()) if (!key.equals("RevokedAt"))
                assertThat(java.util.Objects.equals(current.get(key), previous.get(key))).isTrue();
        }
        assertThat(snapshot(other.id()).equals(otherBefore)).isTrue();
        assertThat(jdbc.queryForList("SELECT * FROM \"UserIdentities\" WHERE \"UserId\" = ?", a.id()).equals(identities)).isTrue();
        assertThat(jdbc.queryForList("SELECT * FROM \"UserRoles\" WHERE \"UserId\" = ?", a.id()).equals(roles)).isTrue();
        assertCode(get(a.access()), 401, "SESSION_INACTIVE");
        assertCode(get(pair.path("accessToken").asText()), 401, "SESSION_INACTIVE");
        assertCode(refresh(replacement), 401, "REFRESH_TOKEN_INVALID");
        assertCode(login(a, OLD), 401, "INVALID_CREDENTIALS");
        assertCode(login(a, NEXT), 200, "LOGIN_SUCCESS");
    }

    @Test void passwordlessIncorrectCorruptAndInvalidRequestsLeaveAllStateUnchanged() throws Exception {
        for (String variant : List.of("missing", "corrupt", "incorrect", "validation")) {
            var f = fixture();
            if (variant.equals("missing")) jdbc.update("UPDATE \"Users\" SET \"PasswordHash\" = NULL WHERE \"Id\" = ?", f.id());
            if (variant.equals("corrupt")) jdbc.update("UPDATE \"Users\" SET \"PasswordHash\" = '{argon2id}$broken' WHERE \"Id\" = ?", f.id());
            var before = snapshot(f.id());
            var response = request("PUT", "/api/v1/users/me/password", variant.equals("validation") ? "{}" :
                    mapper.writeValueAsString(new ChangePasswordRequest("incorrect-test-only", NEXT, NEXT)), f.access());
            assertCode(response, variant.equals("missing") ? 409 : 400, variant.equals("missing") ? "PASSWORD_NOT_CONFIGURED" :
                    variant.equals("validation") ? "VALIDATION_ERROR" : "CURRENT_PASSWORD_INCORRECT");
            assertThat(snapshot(f.id()).equals(before)).isTrue();
        }
    }

    @Test void encodingSaveRevocationAndCommitFailuresRollbackPasswordTimestampSessionsAndTokens() throws Exception {
        for (String stage : List.of("encode", "save", "sessions", "tokens", "commit")) {
            var f = fixture(); var before = snapshot(f.id());
            if (stage.equals("encode")) doThrow(new IllegalStateException("private-uc07-encode")).when(passwords).encode(NEXT);
            if (stage.equals("save")) doAnswer(call -> {
                real(users, call); em.flush(); throw new DataAccessResourceFailureException("private-uc07-save");
            }).when(users).save(any());
            if (stage.equals("sessions")) doAnswer(call -> {
                real(sessions, call); throw new DataAccessResourceFailureException("private-uc07-sessions");
            }).when(sessions).revokeActiveByUser(eq(f.id()), any(), eq("PASSWORD_CHANGED"));
            if (stage.equals("tokens") || stage.equals("commit")) doAnswer(call -> {
                var result = real(tokens, call);
                if (stage.equals("tokens")) throw new DataAccessResourceFailureException("private-uc07-tokens");
                jdbc.execute("CREATE TEMP TABLE uc07_commit_fixture (id integer UNIQUE DEFERRABLE INITIALLY DEFERRED) ON COMMIT DROP");
                jdbc.update("INSERT INTO uc07_commit_fixture VALUES (1), (1)"); return result;
            }).when(tokens).revokeActiveByUser(eq(f.id()), any());
            assertCode(change(f, NEXT), Set.of("encode", "commit").contains(stage) ? 500 : 503,
                    Set.of("encode", "commit").contains(stage) ? "INTERNAL_ERROR" : "SERVICE_UNAVAILABLE");
            assertThat(snapshot(f.id()).equals(before)).isTrue();
            reset(users, sessions, tokens, passwords);
            assertCode(get(f.access()), 200, "PROFILE_GET_SUCCESS");
            assertCode(refresh(f.refresh()), 200, "TOKEN_REFRESH_SUCCESS");
        }
    }

    @Test void samePasswordStillHashesWithFreshSaltAndRevokesCurrentSession() throws Exception {
        var f = fixture(); String before = (String) snapshot(f.id()).user().get("PasswordHash");
        assertCode(change(f, OLD), 200, "PASSWORD_CHANGE_SUCCESS");
        String after = (String) snapshot(f.id()).user().get("PasswordHash");
        assertThat(before.equals(after)).isFalse(); assertThat(passwords.matches(OLD, after)).isTrue();
        assertCode(get(f.access()), 401, "SESSION_INACTIVE");
    }

    @Test void rechecksAccountAndSessionAfterActuallyWaitingForUserLock() throws Exception {
        for (String variant : List.of("ban", "revoke", "expire")) {
            var f = fixture(); var before = snapshot(f.id());
            var locked = new CountDownLatch(1); var release = new CountDownLatch(1);
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                var writer = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                    jdbc.queryForObject("SELECT \"Id\" FROM \"Users\" WHERE \"Id\" = ? FOR UPDATE", Integer.class, f.id());
                    if (variant.equals("ban")) jdbc.update("UPDATE \"Users\" SET \"Status\" = 'BANNED' WHERE \"Id\" = ?", f.id());
                    if (variant.equals("revoke")) jdbc.update("UPDATE \"UserSessions\" SET \"RevokedAt\" = statement_timestamp(), \"RevocationReason\" = 'LOGOUT' WHERE \"Id\" = ?", f.sid());
                    locked.countDown(); await(release); return null;
                }));
                try {
                    assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
                    var pending = executor.submit(() -> change(f, NEXT)); assertUserLockWait();
                    assertThat(pending.isDone()).isFalse();
                    if (variant.equals("expire")) doReturn(instant(session(f.sid()).get("ExpiresAt"))).when(clock).instant();
                    release.countDown(); writer.get(10, TimeUnit.SECONDS);
                    assertCode(pending.get(15, TimeUnit.SECONDS), 401, "SESSION_INACTIVE");
                    var after = snapshot(f.id());
                    assertThat(java.util.Objects.equals(after.user().get("PasswordHash"), before.user().get("PasswordHash"))).isTrue();
                    assertThat(after.tokens().equals(before.tokens())).isTrue();
                    if (!variant.equals("ban")) assertThat(after.user().equals(before.user())).isTrue();
                } finally { release.countDown(); }
            } finally { reset(clock); }
        }
    }

    @Test void twoPasswordChangesSerializeAndOnlyFirstCanUseTheOldSessions() throws Exception {
        var f = fixture(); var secondSid = addSession(f.id(), "active");
        var second = new Fixture(f.id(), secondSid, jwt.issue(f.id(), secondSid), addToken(f.id(), secondSid, false), f.name());
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        doAnswer(call -> { entered.countDown(); await(release); return call.callRealMethod(); }).when(passwords).encode(NEXT);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> change(f, NEXT));
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                var pending = executor.submit(() -> change(second, "second-test-only")); assertUserLockWait(); release.countDown();
                assertCode(first.get(15, TimeUnit.SECONDS), 200, "PASSWORD_CHANGE_SUCCESS");
                assertCode(pending.get(15, TimeUnit.SECONDS), 401, "SESSION_INACTIVE");
                assertThat(passwords.matches(NEXT, (String) snapshot(f.id()).user().get("PasswordHash"))).isTrue();
            } finally { release.countDown(); }
        }
    }

    @Test void loginSerializesWithPasswordChangeInBothOrders() throws Exception {
        for (boolean changeFirst : List.of(true, false)) {
            var f = fixture(); var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
            if (changeFirst) doAnswer(call -> { entered.countDown(); await(release); return call.callRealMethod(); }).when(passwords).encode(NEXT);
            else doAnswer(call -> {
                var result = real(users, call); entered.countDown(); await(release); return result;
            }).when(users).findByUserNameForUpdate(f.name());
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                var first = executor.submit(() -> changeFirst ? change(f, NEXT) : login(f, OLD));
                try {
                    assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                    var pending = executor.submit(() -> changeFirst ? login(f, OLD) : change(f, NEXT));
                    assertUserLockWait(); release.countDown();
                    var a = first.get(15, TimeUnit.SECONDS); var b = pending.get(15, TimeUnit.SECONDS);
                    assertCode(a, 200, changeFirst ? "PASSWORD_CHANGE_SUCCESS" : "LOGIN_SUCCESS");
                    assertCode(b, changeFirst ? 401 : 200, changeFirst ? "INVALID_CREDENTIALS" : "PASSWORD_CHANGE_SUCCESS");
                    if (!changeFirst) assertCode(get(mapper.readTree(a.body()).path("data").path("accessToken").asText()), 401, "SESSION_INACTIVE");
                } finally { release.countDown(); }
            } finally { reset(users, passwords); }
            assertCode(login(f, NEXT), 200, "LOGIN_SUCCESS");
        }
    }

    @Test void refreshSerializesWithPasswordChangeInBothOrdersAndCannotLeaveUsableSuccessor() throws Exception {
        for (boolean changeFirst : List.of(true, false)) {
            var f = fixture(); var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
            if (changeFirst) doAnswer(call -> { entered.countDown(); await(release); return call.callRealMethod(); }).when(passwords).encode(NEXT);
            else doAnswer(call -> {
                var result = real(tokens, call); entered.countDown(); await(release); return result;
            }).when(tokens).findByTokenHash(generator.hash(f.refresh()));
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                var first = executor.submit(() -> changeFirst ? change(f, NEXT) : refresh(f.refresh()));
                try {
                    assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                    var pending = executor.submit(() -> changeFirst ? refresh(f.refresh()) : change(f, NEXT));
                    assertUserLockWait(); release.countDown();
                    var a = first.get(15, TimeUnit.SECONDS); var b = pending.get(15, TimeUnit.SECONDS);
                    assertCode(a, 200, changeFirst ? "PASSWORD_CHANGE_SUCCESS" : "TOKEN_REFRESH_SUCCESS");
                    assertCode(b, changeFirst ? 401 : 200, changeFirst ? "REFRESH_TOKEN_INVALID" : "PASSWORD_CHANGE_SUCCESS");
                    reset(tokens);
                    if (!changeFirst) {
                        var pair = mapper.readTree(a.body()).path("data");
                        assertCode(refresh(pair.path("refreshToken").asText()), 401, "REFRESH_TOKEN_INVALID");
                        assertCode(get(pair.path("accessToken").asText()), 401, "SESSION_INACTIVE");
                    }
                } finally { release.countDown(); }
            } finally { reset(tokens, passwords); }
        }
    }

    private Fixture fixture() {
        String name = "uc07-" + UUID.randomUUID();
        int id = jdbc.queryForObject("INSERT INTO \"Users\" (\"UserName\", \"Email\", \"FullName\", \"PasswordHash\") VALUES (?, ?, 'UC07 Fixture', ?) RETURNING \"Id\"",
                Integer.class, name, name + "@example.com", passwords.encode(OLD));
        fixtureIds.add(id);
        jdbc.update("INSERT INTO \"UserRoles\" SELECT ?, \"Id\" FROM \"Roles\" WHERE \"Name\" = 'READER'", id);
        UUID sid = addSession(id, "active"); return new Fixture(id, sid, jwt.issue(id, sid), addToken(id, sid, false), name);
    }
    private UUID addSession(int userId, String state) {
        UUID sid = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO "UserSessions" ("Id", "UserId", "CreatedAt", "LastSeenAt", "ExpiresAt", "RevokedAt", "RevocationReason")
                VALUES (?, ?, CURRENT_TIMESTAMP - INTERVAL '31 days', CURRENT_TIMESTAMP - INTERVAL '31 days',
                CASE WHEN ? THEN CURRENT_TIMESTAMP - INTERVAL '1 second' ELSE CURRENT_TIMESTAMP + INTERVAL '30 days' END,
                CASE WHEN ? THEN CURRENT_TIMESTAMP - INTERVAL '1 hour' ELSE NULL END,
                CASE WHEN ? THEN 'LOGOUT' ELSE NULL END)
                """, sid, userId, state.equals("expired"), state.equals("revoked"), state.equals("revoked"));
        return sid;
    }
    private String addToken(int userId, UUID sid, boolean expired) {
        String token = generator.generate();
        jdbc.update("""
                INSERT INTO "RefreshTokens" ("UserId", "SessionId", "TokenHash", "CreatedAt", "ExpiresAt") VALUES (?, ?, ?,
                CURRENT_TIMESTAMP - INTERVAL '8 days',
                CASE WHEN ? THEN CURRENT_TIMESTAMP - INTERVAL '1 second' ELSE CURRENT_TIMESTAMP + INTERVAL '7 days' END)
                """, userId, sid, generator.hash(token), expired);
        return token;
    }
    private HttpResponse<String> change(Fixture f, String next) throws Exception {
        return request("PUT", "/api/v1/users/me/password", mapper.writeValueAsString(new ChangePasswordRequest(OLD, next, next)), f.access());
    }
    private HttpResponse<String> login(Fixture f, String password) throws Exception {
        return request("POST", "/api/v1/auth/login", mapper.writeValueAsString(new LoginRequest(f.name(), password)), null);
    }
    private HttpResponse<String> refresh(String token) throws Exception {
        return request("POST", "/api/v1/auth/refresh", mapper.writeValueAsString(new RefreshTokenRequest(token)), null);
    }
    private HttpResponse<String> get(String access) throws Exception { return request("GET", "/api/v1/users/me", null, access); }
    private HttpResponse<String> request(String method, String path, String body, String access) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).timeout(Duration.ofSeconds(20));
        if (access != null) request.header("Authorization", "Bearer " + access);
        if (body != null) request.header("Content-Type", "application/json");
        return client.send(request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    private Snapshot snapshot(int id) {
        return new Snapshot(jdbc.queryForMap("SELECT * FROM \"Users\" WHERE \"Id\" = ?", id),
                jdbc.queryForList("SELECT * FROM \"UserSessions\" WHERE \"UserId\" = ? ORDER BY \"Id\"", id),
                jdbc.queryForList("SELECT * FROM \"RefreshTokens\" WHERE \"UserId\" = ? ORDER BY \"Id\"", id));
    }
    private Map<String, Object> session(UUID sid) { return jdbc.queryForMap("SELECT * FROM \"UserSessions\" WHERE \"Id\" = ?", sid); }
    private Map<String, Object> token(String raw) { return jdbc.queryForMap("SELECT * FROM \"RefreshTokens\" WHERE \"TokenHash\" = ?", generator.hash(raw)); }
    private Instant instant(Object value) { return ((java.sql.Timestamp) value).toInstant(); }
    private void assertCode(HttpResponse<String> response, int status, String code) throws Exception {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(mapper.readTree(response.body()).path("messageCode").asText()).isEqualTo(code);
        assertThat(response.body()).doesNotContain("private-uc07", "PasswordHash", "TokenHash");
    }
    private Object real(Object spy, org.mockito.invocation.InvocationOnMock call) throws Throwable {
        return mockingDetails(spy).getMockCreationSettings().getDefaultAnswer().answer(call);
    }
    private void await(CountDownLatch latch) {
        try { if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("fixture-lock-timeout"); }
        catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException("fixture-interrupted"); }
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
    private record Fixture(int id, UUID sid, String access, String refresh, String name) {
        @Override public String toString() { return "Fixture[id=" + id + ", credentials=REDACTED]"; }
    }
    private record Snapshot(Map<String, Object> user, List<Map<String, Object>> sessions, List<Map<String, Object>> tokens) {
        @Override public String toString() { return "Snapshot[REDACTED]"; }
    }
}
