package com.library.userservice.internal;

import com.library.userservice.auth.persistence.RefreshTokenRepository;
import com.library.userservice.internal.service.UserStatusAudit;
import com.library.userservice.security.*;
import com.library.userservice.session.persistence.UserSessionRepository;
import com.library.userservice.user.persistence.UserRepository;
import com.library.userservice.user.persistence.UserStatus;
import jakarta.persistence.EntityManager;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
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

/** Chỉ PostgreSQL UserDB_test; HTTP/commit/rollback và khóa thật, RSA tạm. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "debug=false")
@EnabledIfSystemProperty(named = "spring.datasource.url", matches = "jdbc:postgresql://[^/]+/UserDB_test(?:\\?.*)?")
class InternalUserStatusIntegrationTests extends UserSecurityTestKeys {
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JsonMapper mapper;
    @Autowired UserAccessTokenService jwt;
    @Autowired RefreshTokenGenerator generator;
    @Autowired PasswordService passwords;
    @MockitoSpyBean UserRepository users;
    @MockitoSpyBean UserSessionRepository sessions;
    @MockitoSpyBean RefreshTokenRepository tokens;
    @MockitoSpyBean UserStatusAudit audit;
    @MockitoSpyBean Clock clock;
    @LocalServerPort int port;
    final HttpClient client = HttpClient.newHttpClient();
    final List<Integer> ids = new ArrayList<>();
    static final String PASSWORD = "status-test-only-password";

    @AfterEach void cleanup() {
        reset(users, sessions, tokens, audit, clock);
        for (int id : ids) jdbc.update("DELETE FROM \"Users\" WHERE \"Id\" = ?", id);
    }

    @Test void bansAllActiveRecordsPreservesHistoryAndReactivationNeverRestoresTokens() throws Exception {
        var admin = fixture(true); var target = fixture(false); var other = fixture(false);
        var ended = addSession(target.id(), true); var expiredToken = addToken(target.id(), target.sid(), true);
        addToken(target.id(), ended, false);
        var rotated = refresh(target.refresh()); code(rotated, 200, "TOKEN_REFRESH_SUCCESS");
        var replacement = mapper.readTree(rotated.body()).path("data").path("refreshToken").asText();
        var endedBefore = jdbc.queryForMap("SELECT * FROM \"UserSessions\" WHERE \"Id\" = ?", ended);
        var expiredBefore = token(expiredToken); var rotatedBefore = token(target.refresh());
        jdbc.update("INSERT INTO \"UserIdentities\" (\"UserId\", \"Provider\", \"ProviderUserId\") VALUES (?, 'GOOGLE', ?)", target.id(), UUID.randomUUID().toString());
        var before = snapshot(target.id()); var otherBefore = snapshot(other.id());
        code(change(admin, target.id(), "BANNED"), 200, "USER_STATUS_UPDATE_SUCCESS");
        var banned = snapshot(target.id());
        assertThat(banned.user().get("Status")).isEqualTo("BANNED");
        for (String field : before.user().keySet()) if (!Set.of("Status", "UpdatedAt").contains(field))
            assertThat(Objects.equals(before.user().get(field), banned.user().get(field))).isTrue();
        assertThat(instant(banned.user().get("UpdatedAt"))).isAfter(instant(before.user().get("UpdatedAt")));
        assertThat(banned.roles()).isEqualTo(before.roles()); assertThat(banned.identities()).isEqualTo(before.identities());
        assertThat(snapshot(other.id())).isEqualTo(otherBefore);
        assertThat(jdbc.queryForMap("SELECT * FROM \"UserSessions\" WHERE \"Id\" = ?", ended)).isEqualTo(endedBefore);
        assertThat(token(expiredToken)).isEqualTo(expiredBefore); assertThat(token(target.refresh())).isEqualTo(rotatedBefore);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM \"RefreshTokens\" WHERE \"UserId\"=? AND \"RevokedAt\" IS NULL AND \"ExpiresAt\">CURRENT_TIMESTAMP", Integer.class, target.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT \"RevocationReason\" FROM \"UserSessions\" WHERE \"Id\"=?", String.class, target.sid())).isEqualTo("ACCOUNT_BANNED");
        assertThat(introspect(target.access())).isFalse();
        code(request("GET", "/api/v1/users/me", null, "Authorization", "Bearer " + target.access()), 401, "SESSION_INACTIVE");
        code(refresh(replacement), 401, "REFRESH_TOKEN_INVALID");
        code(login(target), 403, "ACCOUNT_BANNED");
        code(change(admin, target.id(), "BANNED"), 200, "USER_STATUS_UPDATE_SUCCESS");
        assertThat(snapshot(target.id())).isEqualTo(banned);
        code(change(admin, target.id(), "ACTIVE"), 200, "USER_STATUS_UPDATE_SUCCESS");
        var active = snapshot(target.id()); assertThat(active.sessions()).isEqualTo(banned.sessions()); assertThat(active.tokens()).isEqualTo(banned.tokens());
        assertThat(introspect(target.access())).isFalse(); code(refresh(replacement), 401, "REFRESH_TOKEN_INVALID");
        code(login(target), 200, "LOGIN_SUCCESS");
        verify(audit, times(3)).record(any(), eq(admin.id()), eq(target.id()), any(), any(), any());
    }

    @Test void rejectsAdminTargetsAndRechecksCurrentRolesWithoutChangingState() throws Exception {
        var admin = fixture(true); var target = fixture(true); var before = snapshot(target.id());
        code(change(admin, target.id(), "BANNED"), 403, "ACCESS_DENIED");
        code(change(admin, admin.id(), "BANNED"), 403, "ACCESS_DENIED");
        code(change(admin, admin.id(), "ACTIVE"), 200, "USER_STATUS_UPDATE_SUCCESS");
        jdbc.update("DELETE FROM \"UserRoles\" WHERE \"UserId\"=?", admin.id());
        code(change(admin, target.id(), "ACTIVE"), 403, "ACCESS_DENIED");
        assertThat(snapshot(target.id())).isEqualTo(before);
        code(change(target, Integer.MAX_VALUE, "ACTIVE"), 404, "USER_NOT_FOUND");
    }

    @Test void rollsBackStatusAndRevocationIncludingCommitFailureAndNeverAuditsRollback() throws Exception {
        var admin = fixture(true);
        for (String stage : List.of("save", "tokens", "sessions", "commit")) {
            var target = fixture(false); var before = snapshot(target.id());
            if (stage.equals("save")) doAnswer(call -> { real(users, call); em.flush(); throw new DataAccessResourceFailureException("private-status"); }).when(users).save(any());
            if (stage.equals("tokens")) doAnswer(call -> { real(tokens, call); throw new DataAccessResourceFailureException("private-status"); }).when(tokens).revokeActiveByUser(eq(target.id()), any());
            if (stage.equals("sessions") || stage.equals("commit")) doAnswer(call -> {
                var result = real(sessions, call);
                if (stage.equals("sessions")) throw new DataAccessResourceFailureException("private-status");
                jdbc.execute("CREATE TEMP TABLE status_commit_fixture (id integer UNIQUE DEFERRABLE INITIALLY DEFERRED) ON COMMIT DROP");
                jdbc.update("INSERT INTO status_commit_fixture VALUES (1),(1)"); return result;
            }).when(sessions).revokeActiveByUser(eq(target.id()), any(), eq("ACCOUNT_BANNED"));
            code(change(admin, target.id(), "BANNED"), stage.equals("commit") ? 500 : 503, stage.equals("commit") ? "INTERNAL_ERROR" : "SERVICE_UNAVAILABLE");
            assertThat(snapshot(target.id())).isEqualTo(before); verifyNoInteractions(audit);
            reset(users, sessions, tokens);
        }
    }

    @Test void actorLosingValidityWhileWaitingForTargetLockCannotWrite() throws Exception {
        for (String variant : List.of("revoke", "expire", "jwt", "role", "ban")) {
            var target = fixture(false); var admin = fixture(true); var before = snapshot(target.id());
            if (variant.equals("expire")) jdbc.update("UPDATE \"UserSessions\" SET \"ExpiresAt\"=CURRENT_TIMESTAMP+INTERVAL '60 seconds' WHERE \"Id\"=?", admin.sid());
            var locked = new CountDownLatch(1); var release = new CountDownLatch(1);
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                var blocker = executor.submit(() -> new TransactionTemplate(transactions).execute(tx -> {
                    jdbc.queryForObject("SELECT \"Id\" FROM \"Users\" WHERE \"Id\"=? FOR UPDATE", Integer.class, target.id());
                    locked.countDown(); await(release); return null;
                }));
                try {
                    assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
                    var pending = executor.submit(() -> change(admin, target.id(), "BANNED")); waitForUserLock();
                    if (variant.equals("revoke")) jdbc.update("UPDATE \"UserSessions\" SET \"RevokedAt\"=CURRENT_TIMESTAMP, \"RevocationReason\"='LOGOUT' WHERE \"Id\"=?", admin.sid());
                    if (variant.equals("expire")) doReturn(instant(jdbc.queryForObject("SELECT \"ExpiresAt\" FROM \"UserSessions\" WHERE \"Id\"=?", java.sql.Timestamp.class, admin.sid()))).when(clock).instant();
                    if (variant.equals("jwt")) doReturn(jwt.verify(admin.access()).getExpiresAt().plusSeconds(30)).when(clock).instant();
                    if (variant.equals("role")) jdbc.update("DELETE FROM \"UserRoles\" WHERE \"UserId\"=?", admin.id());
                    if (variant.equals("ban")) jdbc.update("UPDATE \"Users\" SET \"Status\"='BANNED' WHERE \"Id\"=?", admin.id());
                    release.countDown(); blocker.get(10, TimeUnit.SECONDS);
                    code(pending.get(15, TimeUnit.SECONDS), Set.of("role", "ban").contains(variant) ? 403 : 401,
                            Set.of("role", "ban").contains(variant) ? "ACCESS_DENIED" : "ADMIN_ACTOR_INVALID");
                    assertThat(snapshot(target.id())).isEqualTo(before);
                } finally { release.countDown(); }
            } finally { reset(clock); }
        }
    }

    @Test void loginAndRefreshSerializeWithBanInBothOrders() throws Exception {
        for (String operation : List.of("login", "refresh")) for (boolean banFirst : List.of(true, false)) {
            var admin = fixture(true); var target = fixture(false); var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
            if (banFirst) doAnswer(call -> { var result = real(users, call); entered.countDown(); await(release); return result; }).when(users).save(any());
            else if (operation.equals("login")) doAnswer(call -> { var result = real(users, call); entered.countDown(); await(release); return result; }).when(users).findByUserNameForUpdate(target.name());
            else doAnswer(call -> { var result = real(tokens, call); entered.countDown(); await(release); return result; }).when(tokens).findByTokenHash(generator.hash(target.refresh()));
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                var first = executor.submit(() -> banFirst ? change(admin, target.id(), "BANNED") : operation.equals("login") ? login(target) : refresh(target.refresh()));
                try {
                    assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                    var pending = executor.submit(() -> banFirst ? operation.equals("login") ? login(target) : refresh(target.refresh()) : change(admin, target.id(), "BANNED"));
                    waitForUserLock(); release.countDown();
                    var a = first.get(15, TimeUnit.SECONDS); var b = pending.get(15, TimeUnit.SECONDS);
                    code(a, 200, banFirst ? "USER_STATUS_UPDATE_SUCCESS" : operation.equals("login") ? "LOGIN_SUCCESS" : "TOKEN_REFRESH_SUCCESS");
                    code(b, banFirst ? operation.equals("login") ? 403 : 401 : 200,
                            banFirst ? operation.equals("login") ? "ACCOUNT_BANNED" : "REFRESH_TOKEN_INVALID" : "USER_STATUS_UPDATE_SUCCESS");
                    if (!banFirst) assertThat(introspect(mapper.readTree(a.body()).path("data").path("accessToken").asText())).isFalse();
                } finally { release.countDown(); }
            } finally { reset(users, tokens); }
        }
    }

    @Test void statusCommandsSerializeAndCrossAdminCommandsUseOneLockOrder() throws Exception {
        var admin = fixture(true); var target = fixture(false);
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        doAnswer(call -> { var result = real(users, call); entered.countDown(); await(release); return result; }).when(users).save(any());
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var ban = executor.submit(() -> change(admin, target.id(), "BANNED"));
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                var activate = executor.submit(() -> change(admin, target.id(), "ACTIVE")); waitForUserLock(); release.countDown();
                code(ban.get(15, TimeUnit.SECONDS), 200, "USER_STATUS_UPDATE_SUCCESS"); code(activate.get(15, TimeUnit.SECONDS), 200, "USER_STATUS_UPDATE_SUCCESS");
            } finally { release.countDown(); }
        }
        reset(users);
        var secondAdmin = fixture(true);
        var gate = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var a = executor.submit(() -> { await(gate); return change(admin, secondAdmin.id(), "ACTIVE"); });
            var b = executor.submit(() -> { await(gate); return change(secondAdmin, admin.id(), "ACTIVE"); });
            gate.countDown(); code(a.get(15, TimeUnit.SECONDS), 200, "USER_STATUS_UPDATE_SUCCESS"); code(b.get(15, TimeUnit.SECONDS), 200, "USER_STATUS_UPDATE_SUCCESS");
        }
        assertThat(introspect(target.access())).isFalse();
    }

    @Test void auditObservesCommittedStateAndLoggingFailureDoesNotChangeSuccess() throws Exception {
        var admin = fixture(true); var target = fixture(false);
        doAnswer(call -> {
            var observer = new TransactionTemplate(transactions);
            observer.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            String committedStatus = observer.execute(tx -> jdbc.queryForObject("SELECT \"Status\" FROM \"Users\" WHERE \"Id\"=?", String.class, target.id()));
            assertThat(committedStatus).isEqualTo("BANNED");
            throw new IllegalStateException("private-status-audit");
        }).when(audit).record(any(), eq(admin.id()), eq(target.id()), any(), any(), any());
        code(change(admin, target.id(), "BANNED"), 200, "USER_STATUS_UPDATE_SUCCESS");
        assertThat(introspect(target.access())).isFalse();
        verify(audit).record(any(), eq(admin.id()), eq(target.id()), eq(UserStatus.ACTIVE), eq(UserStatus.BANNED), any());
    }

    Fixture fixture(boolean admin) {
        String name = "status-" + UUID.randomUUID();
        int id = jdbc.queryForObject("INSERT INTO \"Users\" (\"UserName\",\"Email\",\"FullName\",\"PasswordHash\") VALUES (?,?,'Status Fixture',?) RETURNING \"Id\"",
                Integer.class, name, name + "@example.com", passwords.encode(PASSWORD)); ids.add(id);
        jdbc.update("INSERT INTO \"UserRoles\" SELECT ?,\"Id\" FROM \"Roles\" WHERE \"Name\"=?", id, admin ? "ADMIN" : "READER");
        UUID sid = addSession(id, false); return new Fixture(id, sid, jwt.issue(id, sid), addToken(id, sid, false), name);
    }
    UUID addSession(int id, boolean expired) {
        UUID sid = UUID.randomUUID();
        jdbc.update("INSERT INTO \"UserSessions\" (\"Id\",\"UserId\",\"CreatedAt\",\"LastSeenAt\",\"ExpiresAt\") VALUES (?,?,CURRENT_TIMESTAMP-INTERVAL '31 days',CURRENT_TIMESTAMP-INTERVAL '31 days',CURRENT_TIMESTAMP + (? * INTERVAL '1 day'))", sid, id, expired ? -1 : 30);
        return sid;
    }
    String addToken(int id, UUID sid, boolean expired) {
        String token = generator.generate();
        jdbc.update("INSERT INTO \"RefreshTokens\" (\"UserId\",\"SessionId\",\"TokenHash\",\"CreatedAt\",\"ExpiresAt\") VALUES (?,?,?,CURRENT_TIMESTAMP-INTERVAL '8 days',CURRENT_TIMESTAMP + (? * INTERVAL '1 day'))", id, sid, generator.hash(token), expired ? -1 : 7);
        return token;
    }
    HttpResponse<String> change(Fixture actor, int id, String value) throws Exception { return request("PATCH", "/api/v1/internal/users/" + id + "/status", "{\"status\":\""+value+"\"}", "X-Admin-Access-Token", actor.access()); }
    HttpResponse<String> login(Fixture f) throws Exception { return request("POST", "/api/v1/auth/login", "{\"identifier\":\""+f.name()+"\",\"password\":\""+PASSWORD+"\"}", null, null); }
    HttpResponse<String> refresh(String token) throws Exception { return request("POST", "/api/v1/auth/refresh", "{\"refreshToken\":\""+token+"\"}", null, null); }
    boolean introspect(String token) throws Exception {
        var r = client.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1/internal/oauth2/introspect")).header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString("token="+token)).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(r.statusCode()).isEqualTo(200); return mapper.readTree(r.body()).path("active").asBoolean();
    }
    HttpResponse<String> request(String method, String path, String body, String header, String credential) throws Exception {
        var b = HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).timeout(Duration.ofSeconds(20));
        if (header != null) b.header(header, credential);
        if (body != null) b.header("Content-Type", "application/json");
        return client.send(b.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    void code(HttpResponse<String> r, int status, String code) throws Exception {
        assertThat(r.statusCode()).as(r.body()).isEqualTo(status); assertThat(mapper.readTree(r.body()).path("messageCode").asText()).isEqualTo(code);
        assertThat(r.body()).doesNotContain("private-status", "PasswordHash", "TokenHash");
    }
    Map<String,Object> token(String raw) { return jdbc.queryForMap("SELECT * FROM \"RefreshTokens\" WHERE \"TokenHash\"=?", generator.hash(raw)); }
    Snapshot snapshot(int id) {
        return new Snapshot(jdbc.queryForMap("SELECT * FROM \"Users\" WHERE \"Id\"=?", id),
                jdbc.queryForList("SELECT * FROM \"UserSessions\" WHERE \"UserId\"=? ORDER BY \"Id\"", id),
                jdbc.queryForList("SELECT * FROM \"RefreshTokens\" WHERE \"UserId\"=? ORDER BY \"Id\"", id),
                jdbc.queryForList("SELECT * FROM \"UserRoles\" WHERE \"UserId\"=? ORDER BY \"RoleId\"", id),
                jdbc.queryForList("SELECT * FROM \"UserIdentities\" WHERE \"UserId\"=? ORDER BY \"Id\"", id));
    }
    Instant instant(Object value) { return ((java.sql.Timestamp)value).toInstant(); }
    Object real(Object spy, org.mockito.invocation.InvocationOnMock call) throws Throwable { return mockingDetails(spy).getMockCreationSettings().getDefaultAnswer().answer(call); }
    void await(CountDownLatch latch) { try { if (!latch.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("fixture-timeout"); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException("fixture-interrupted"); } }
    void waitForUserLock() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime()<deadline) {
            if (Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM pg_stat_activity WHERE datname=current_database() AND pid<>pg_backend_pid() AND wait_event_type='Lock' AND query LIKE '%Users%' AND query LIKE '%for%update%')", Boolean.class))) return;
            Thread.sleep(20);
        } fail("Không quan sát được chờ khóa user");
    }
    record Fixture(int id, UUID sid, String access, String refresh, String name) { @Override public String toString() { return "Fixture[REDACTED]"; } }
    record Snapshot(Map<String,Object> user, List<Map<String,Object>> sessions, List<Map<String,Object>> tokens,
                    List<Map<String,Object>> roles, List<Map<String,Object>> identities) { @Override public String toString() { return "Snapshot[REDACTED]"; } }
}
