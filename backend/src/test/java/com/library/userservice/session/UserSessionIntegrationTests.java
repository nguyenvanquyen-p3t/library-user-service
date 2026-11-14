package com.library.userservice.session;

import com.library.userservice.auth.dto.LoginRequest;
import com.library.userservice.auth.dto.RefreshTokenRequest;
import com.library.userservice.auth.persistence.RefreshTokenRepository;
import com.library.userservice.security.PasswordService;
import com.library.userservice.security.RefreshTokenGenerator;
import com.library.userservice.security.UserAccessTokenService;
import com.library.userservice.security.UserSecurityTestKeys;
import com.library.userservice.session.persistence.UserSessionRepository;
import com.library.userservice.user.persistence.UserRepository;
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

/** HTTP/commit/khóa PostgreSQL thật, chỉ UserDB_test và khóa tạm. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "debug=false")
@EnabledIfSystemProperty(named = "spring.datasource.url", matches = "jdbc:postgresql://[^/]+/UserDB_test(?:\\?.*)?")
class UserSessionIntegrationTests extends UserSecurityTestKeys {
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired JsonMapper mapper;
    @Autowired PasswordService passwords;
    @Autowired RefreshTokenGenerator generator;
    @Autowired UserAccessTokenService jwt;
    @MockitoSpyBean UserRepository users;
    @MockitoSpyBean UserSessionRepository sessions;
    @MockitoSpyBean RefreshTokenRepository tokens;
    @MockitoSpyBean Clock clock;
    @LocalServerPort int port;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final List<Integer> fixtureIds = new ArrayList<>();
    private static final String BASE = "/api/v1/users/me/sessions";
    private static final String PASSWORD = "sessions-test-only";

    @AfterEach void cleanup() {
        reset(users, sessions, tokens, clock);
        for (int id : fixtureIds) jdbc.update("DELETE FROM \"Users\" WHERE \"Id\" = ?", id);
    }

    @Test void listsOwnedActiveSessionsWithStableOrderingMetadataAndHugePagesWithoutWriting() throws Exception {
        var f = fixture(); var other = fixture();
        var second = addSession(f.id(), "active"); var third = addSession(f.id(), "active");
        addSession(f.id(), "expired"); addSession(f.id(), "revoked");
        jdbc.update("UPDATE \"UserSessions\" SET \"CreatedAt\" = TIMESTAMPTZ '2026-10-01T00:00:00Z', \"LastSeenAt\" = TIMESTAMPTZ '2026-10-02T00:00:00Z' WHERE \"UserId\" = ?", f.id());
        jdbc.update("UPDATE \"UserSessions\" SET \"DeviceName\" = 'Fixture Android', \"ClientType\" = 'ANDROID', \"UserAgent\" = 'private-sessions-agent' WHERE \"Id\" = ?", second);
        var before = snapshot(f.id()); var foreign = snapshot(other.id());
        var ordered = List.of(f.sid(), second, third).stream().sorted(java.util.Comparator.comparing(UUID::toString)).toList();
        var response = request("GET", BASE + "?size=2", null, f.access()); assertCode(response, 200, "SESSION_LIST_SUCCESS");
        var data = mapper.readTree(response.body()).path("data");
        assertThat(data.path("totalElements").asLong()).isEqualTo(3); assertThat(data.path("items").size()).isEqualTo(2);
        for (int i = 0; i < 2; i++) assertThat(data.path("items").get(i).path("id").asText()).isEqualTo(ordered.get(i).toString());
        response = request("GET", BASE + "?page=1&size=2", null, f.access());
        assertThat(mapper.readTree(response.body()).path("data").path("items").get(0).path("id").asText()).isEqualTo(ordered.get(2).toString());
        data = mapper.readTree(list(f).body()).path("data");
        for (var item : data.path("items")) {
            assertThat(item.size()).isEqualTo(7);
            assertThat(item.path("isCurrent").asBoolean()).isEqualTo(item.path("id").asText().equals(f.sid().toString()));
            assertThat(item.path("lastSeenAt").asText()).isEqualTo("2026-10-02T00:00:00Z");
            if (item.path("id").asText().equals(second.toString())) {
                assertThat(item.path("clientType").asText()).isEqualTo("ANDROID");
                assertThat(item.path("deviceName").asText()).isEqualTo("Fixture Android");
            } else { assertThat(item.path("deviceName").isNull()).isTrue(); assertThat(item.path("clientType").isNull()).isTrue(); }
        }
        response = request("GET", BASE + "?page=2147483647&size=100", null, f.access());
        assertCode(response, 200, "SESSION_LIST_SUCCESS"); data = mapper.readTree(response.body()).path("data");
        assertThat(data.path("items").isEmpty()).isTrue(); assertThat(data.path("page").asInt()).isEqualTo(Integer.MAX_VALUE);
        assertThat(data.path("totalElements").asLong()).isEqualTo(3);
        assertThat(snapshot(f.id()).equals(before)).isTrue(); assertThat(snapshot(other.id()).equals(foreign)).isTrue();
    }

    @Test void revokeOnePreservesOtherSessionsAndTokenHistoryAndRejectsBearerAndRefreshAfterCommit() throws Exception {
        var f = fixture(); UUID target = addSession(f.id(), "active");
        String old = addToken(f.id(), target, false); String expired = addToken(f.id(), target, true);
        var rotated = refresh(old); assertCode(rotated, 200, "TOKEN_REFRESH_SUCCESS");
        String successor = mapper.readTree(rotated.body()).path("data").path("refreshToken").asText();
        var oldBefore = token(old); var expiredBefore = token(expired); var currentBefore = session(f.sid());
        var callerToken = token(f.refresh());
        assertCode(revoke(f, target), 200, "SESSION_REVOKE_SUCCESS");
        var after = session(target); assertThat(after.get("RevocationReason")).isEqualTo("SESSION_REVOKED");
        assertThat(after.get("RevokedAt")).isNotNull(); assertThat(token(successor).get("RevokedAt")).isNotNull();
        assertThat(token(old).equals(oldBefore)).isTrue(); assertThat(token(expired).equals(expiredBefore)).isTrue();
        assertThat(session(f.sid()).equals(currentBefore)).isTrue(); assertThat(token(f.refresh()).equals(callerToken)).isTrue();
        var history = snapshot(f.id()); assertCode(revoke(f, target), 200, "SESSION_REVOKE_SUCCESS");
        assertThat(snapshot(f.id()).equals(history)).isTrue();
        assertCode(refresh(old), 401, "REFRESH_TOKEN_INVALID"); assertCode(refresh(successor), 401, "REFRESH_TOKEN_INVALID");
        assertCode(request("GET", BASE, null, jwt.issue(f.id(), target)), 401, "SESSION_INACTIVE");
        assertCode(list(f), 200, "SESSION_LIST_SUCCESS");
    }

    @Test void bulkRevokesOnlyOtherActiveSessionsAndTheirActiveTokensCountingSessionsAndKeepingHistory() throws Exception {
        var f = fixture(); var foreign = fixture(); var foreignBefore = snapshot(foreign.id());
        UUID a = addSession(f.id(), "active"), b = addSession(f.id(), "active");
        UUID expired = addSession(f.id(), "expired"), revoked = addSession(f.id(), "revoked");
        String ta = addToken(f.id(), a, false), tb = addToken(f.id(), b, false), extra = addToken(f.id(), a, false);
        String endedA = addToken(f.id(), expired, false), endedB = addToken(f.id(), revoked, false);
        String expiredToken = addToken(f.id(), a, true), revokedToken = addToken(f.id(), a, false);
        jdbc.update("UPDATE \"RefreshTokens\" SET \"RevokedAt\" = CURRENT_TIMESTAMP - INTERVAL '1 hour' WHERE \"TokenHash\" = ?", generator.hash(revokedToken));
        var expiredBefore = session(expired); var revokedBefore = session(revoked);
        var endedTokenA = token(endedA); var endedTokenB = token(endedB); var expiredTokenBefore = token(expiredToken); var revokedTokenBefore = token(revokedToken);
        var callerBefore = session(f.sid()); var callerTokenBefore = token(f.refresh());
        var response = revokeOthers(f); assertCode(response, 200, "OTHER_SESSIONS_REVOKED");
        assertThat(mapper.readTree(response.body()).path("data").path("revokedCount").asLong()).isEqualTo(2);
        for (UUID sid : List.of(a, b)) {
            assertThat(session(sid).get("RevocationReason")).isEqualTo("OTHER_SESSIONS_REVOKED");
            assertThat(session(sid).get("RevokedAt")).isEqualTo(session(a).get("RevokedAt"));
        }
        for (String raw : List.of(ta, tb, extra)) assertThat(token(raw).get("RevokedAt")).isEqualTo(session(a).get("RevokedAt"));
        assertThat(session(expired).equals(expiredBefore)).isTrue(); assertThat(session(revoked).equals(revokedBefore)).isTrue();
        assertThat(token(endedA).equals(endedTokenA)).isTrue(); assertThat(token(endedB).equals(endedTokenB)).isTrue();
        assertThat(token(expiredToken).equals(expiredTokenBefore)).isTrue(); assertThat(token(revokedToken).equals(revokedTokenBefore)).isTrue();
        assertThat(session(f.sid()).equals(callerBefore)).isTrue(); assertThat(token(f.refresh()).equals(callerTokenBefore)).isTrue();
        assertThat(snapshot(foreign.id()).equals(foreignBefore)).isTrue();
        var history = snapshot(f.id()); response = revokeOthers(f);
        assertThat(mapper.readTree(response.body()).path("data").path("revokedCount").asLong()).isZero();
        assertThat(snapshot(f.id()).equals(history)).isTrue();
        assertCode(refresh(ta), 401, "REFRESH_TOKEN_INVALID"); assertCode(refresh(f.refresh()), 200, "TOKEN_REFRESH_SUCCESS");
    }

    @Test void rejectsCurrentForeignAndUnknownTargetsAndLeavesEndedTargetUntouched() throws Exception {
        var f = fixture(); var foreign = fixture(); var ended = addSession(f.id(), "expired");
        addToken(f.id(), ended, false); var before = snapshot(f.id()); var otherBefore = snapshot(foreign.id());
        assertCode(revoke(f, f.sid()), 400, "CURRENT_SESSION_REVOKE_NOT_ALLOWED");
        assertCode(revoke(f, foreign.sid()), 404, "SESSION_NOT_FOUND"); assertCode(revoke(f, UUID.randomUUID()), 404, "SESSION_NOT_FOUND");
        assertCode(revoke(f, ended), 200, "SESSION_REVOKE_SUCCESS");
        assertThat(snapshot(f.id()).equals(before)).isTrue(); assertThat(snapshot(foreign.id()).equals(otherBefore)).isTrue();
    }

    @Test void rollsBackAllChangesForSessionTokenAndDeferredCommitFailuresInBothWriters() throws Exception {
        for (boolean bulk : List.of(false, true)) for (String stage : List.of("sessions", "tokens", "commit")) {
            var f = fixture(); var target = addSession(f.id(), "active"); addToken(f.id(), target, false);
            var before = snapshot(f.id());
            if (stage.equals("sessions")) {
                if (bulk) doAnswer(call -> { real(sessions, call); throw new DataAccessResourceFailureException("private-sessions-write"); })
                        .when(sessions).revokeActiveOthers(eq(f.id()), any(), any(), any());
                else doAnswer(call -> { real(sessions, call); throw new DataAccessResourceFailureException("private-sessions-write"); })
                        .when(sessions).save(argThat(s -> s.getId().equals(target)));
            } else {
                org.mockito.stubbing.Answer<Object> failure = call -> {
                    var result = real(tokens, call);
                    if (stage.equals("tokens")) throw new DataAccessResourceFailureException("private-sessions-token");
                    jdbc.execute("CREATE TEMP TABLE uc1012_commit_fixture (id integer UNIQUE DEFERRABLE INITIALLY DEFERRED) ON COMMIT DROP");
                    jdbc.update("INSERT INTO uc1012_commit_fixture VALUES (1), (1)"); return result;
                };
                if (bulk) doAnswer(failure).when(tokens).revokeActiveByOtherActiveSessions(eq(f.id()), any(), any());
                else doAnswer(failure).when(tokens).revokeActiveBySession(eq(target), eq(f.id()), any());
            }
            assertCode(bulk ? revokeOthers(f) : revoke(f, target), stage.equals("commit") ? 500 : 503,
                    stage.equals("commit") ? "INTERNAL_ERROR" : "SERVICE_UNAVAILABLE");
            assertThat(snapshot(f.id()).equals(before)).isTrue(); reset(sessions, tokens);
        }
    }

    @Test void listUsesOneSnapshotForCountAndRowsWhileAnotherRequestRevokesSessions() throws Exception {
        var f = fixture(); addSession(f.id(), "active"); addSession(f.id(), "active");
        var counted = new CountDownLatch(1); var release = new CountDownLatch(1);
        doAnswer(call -> { var result = real(sessions, call); counted.countDown(); await(release); return result; })
                .when(sessions).countActiveByUserId(eq(f.id()), any());
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var pending = executor.submit(() -> list(f));
            try {
                assertThat(counted.await(5, TimeUnit.SECONDS)).isTrue();
                assertCode(revokeOthers(f), 200, "OTHER_SESSIONS_REVOKED"); release.countDown();
                var response = pending.get(15, TimeUnit.SECONDS); assertCode(response, 200, "SESSION_LIST_SUCCESS");
                var data = mapper.readTree(response.body()).path("data");
                assertThat(data.path("totalElements").asLong()).isEqualTo(3); assertThat(data.path("items").size()).isEqualTo(3);
            } finally { release.countDown(); }
        }
        reset(sessions);
        assertThat(mapper.readTree(list(f).body()).path("data").path("totalElements").asLong()).isEqualTo(1);
    }

    @Test void bothWritersRecheckCallerAfterWaitingForLockIncludingExactExpiry() throws Exception {
        for (boolean bulk : List.of(false, true)) for (String variant : List.of("ban", "revoke", "expire")) {
            var f = fixture(); var target = addSession(f.id(), "active"); var raw = addToken(f.id(), target, false);
            var targetBefore = session(target); var tokenBefore = token(raw);
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
                    var pending = executor.submit(() -> bulk ? revokeOthers(f) : revoke(f, target)); assertUserLockWait();
                    if (variant.equals("expire")) doReturn(((java.sql.Timestamp) session(f.sid()).get("ExpiresAt")).toInstant()).when(clock).instant();
                    release.countDown(); writer.get(10, TimeUnit.SECONDS);
                    assertCode(pending.get(15, TimeUnit.SECONDS), 401, "SESSION_INACTIVE");
                    assertThat(session(target).equals(targetBefore)).isTrue(); assertThat(token(raw).equals(tokenBefore)).isTrue();
                } finally { release.countDown(); }
            } finally { reset(clock); }
        }
    }

    @Test void twoRequestsRevokingSameTargetSerializeWithoutOverwritingHistory() throws Exception {
        var f = fixture(); var target = addSession(f.id(), "active"); addToken(f.id(), target, false);
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        doAnswer(call -> { var result = real(sessions, call); entered.countDown(); await(release); return result; })
                .when(sessions).save(argThat(s -> s.getId().equals(target)));
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> revoke(f, target));
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                var pending = executor.submit(() -> revoke(f, target)); assertUserLockWait(); release.countDown();
                assertCode(first.get(15, TimeUnit.SECONDS), 200, "SESSION_REVOKE_SUCCESS");
                var committed = session(target);
                assertCode(pending.get(15, TimeUnit.SECONDS), 200, "SESSION_REVOKE_SUCCESS");
                assertThat(session(target).equals(committed)).isTrue();
                verify(sessions, times(1)).save(argThat(s -> s.getId().equals(target)));
            } finally { release.countDown(); }
        }
    }

    @Test void refreshAndBothRevocationOperationsSerializeInBothOrders() throws Exception {
        for (boolean bulk : List.of(false, true)) for (boolean revokeFirst : List.of(false, true)) {
            var f = fixture(); var target = addSession(f.id(), "active"); String raw = addToken(f.id(), target, false);
            var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
            if (revokeFirst) {
                if (bulk) doAnswer(call -> { var r = real(tokens, call); entered.countDown(); await(release); return r; })
                        .when(tokens).revokeActiveByOtherActiveSessions(eq(f.id()), any(), any());
                else doAnswer(call -> { var r = real(tokens, call); entered.countDown(); await(release); return r; })
                        .when(tokens).revokeActiveBySession(eq(target), eq(f.id()), any());
            } else doAnswer(call -> { var r = real(tokens, call); entered.countDown(); await(release); return r; })
                    .when(tokens).findByTokenHash(generator.hash(raw));
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                var first = executor.submit(() -> revokeFirst ? (bulk ? revokeOthers(f) : revoke(f, target)) : refresh(raw));
                try {
                    assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                    var pending = executor.submit(() -> revokeFirst ? refresh(raw) : (bulk ? revokeOthers(f) : revoke(f, target)));
                    assertUserLockWait(); release.countDown();
                    var a = first.get(15, TimeUnit.SECONDS); var b = pending.get(15, TimeUnit.SECONDS);
                    String revokeCode = bulk ? "OTHER_SESSIONS_REVOKED" : "SESSION_REVOKE_SUCCESS";
                    assertCode(a, 200, revokeFirst ? revokeCode : "TOKEN_REFRESH_SUCCESS");
                    assertCode(b, revokeFirst ? 401 : 200, revokeFirst ? "REFRESH_TOKEN_INVALID" : revokeCode);
                    reset(tokens);
                    if (!revokeFirst) {
                        var pair = mapper.readTree(a.body()).path("data");
                        assertCode(refresh(pair.path("refreshToken").asText()), 401, "REFRESH_TOKEN_INVALID");
                        assertCode(request("GET", BASE, null, pair.path("accessToken").asText()), 401, "SESSION_INACTIVE");
                    }
                } finally { release.countDown(); }
            } finally { reset(tokens); }
        }
    }

    @Test void loginBeforeBulkRevocationIsRevokedAndLoginAfterItCreatesAUsableSession() throws Exception {
        for (boolean revokeFirst : List.of(false, true)) {
            var f = fixture(); var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
            if (revokeFirst) doAnswer(call -> { var r = real(tokens, call); entered.countDown(); await(release); return r; })
                    .when(tokens).revokeActiveByOtherActiveSessions(eq(f.id()), any(), any());
            else doAnswer(call -> { var r = real(users, call); entered.countDown(); await(release); return r; })
                    .when(users).findByUserNameForUpdate(f.name());
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                var first = executor.submit(() -> revokeFirst ? revokeOthers(f) : login(f));
                try {
                    assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                    var pending = executor.submit(() -> revokeFirst ? login(f) : revokeOthers(f)); assertUserLockWait(); release.countDown();
                    var a = first.get(15, TimeUnit.SECONDS); var b = pending.get(15, TimeUnit.SECONDS);
                    assertCode(a, 200, revokeFirst ? "OTHER_SESSIONS_REVOKED" : "LOGIN_SUCCESS");
                    assertCode(b, 200, revokeFirst ? "LOGIN_SUCCESS" : "OTHER_SESSIONS_REVOKED");
                    var pair = mapper.readTree((revokeFirst ? b : a).body()).path("data");
                    assertCode(request("GET", BASE, null, pair.path("accessToken").asText()), revokeFirst ? 200 : 401,
                            revokeFirst ? "SESSION_LIST_SUCCESS" : "SESSION_INACTIVE");
                } finally { release.countDown(); }
            } finally { reset(users, tokens); }
        }
    }

    private Fixture fixture() {
        String name = "uc1012-" + UUID.randomUUID();
        int id = jdbc.queryForObject("INSERT INTO \"Users\" (\"UserName\", \"Email\", \"FullName\", \"PasswordHash\") VALUES (?, ?, 'Session Fixture', ?) RETURNING \"Id\"",
                Integer.class, name, name + "@example.com", passwords.encode(PASSWORD));
        fixtureIds.add(id); UUID sid = addSession(id, "active");
        return new Fixture(id, sid, jwt.issue(id, sid), addToken(id, sid, false), name);
    }
    private UUID addSession(int userId, String state) {
        UUID sid = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO "UserSessions" ("Id", "UserId", "CreatedAt", "LastSeenAt", "ExpiresAt", "RevokedAt", "RevocationReason")
                VALUES (?, ?, CURRENT_TIMESTAMP - INTERVAL '31 days', CURRENT_TIMESTAMP - INTERVAL '31 days',
                CASE WHEN ? THEN CURRENT_TIMESTAMP - INTERVAL '1 second' ELSE CURRENT_TIMESTAMP + INTERVAL '30 days' END,
                CASE WHEN ? THEN CURRENT_TIMESTAMP - INTERVAL '1 hour' ELSE NULL END,
                CASE WHEN ? THEN 'LOGOUT' ELSE NULL END)
                """, sid, userId, state.equals("expired"), state.equals("revoked"), state.equals("revoked")); return sid;
    }
    private String addToken(int userId, UUID sid, boolean expired) {
        String raw = generator.generate();
        jdbc.update("""
                INSERT INTO "RefreshTokens" ("UserId", "SessionId", "TokenHash", "CreatedAt", "ExpiresAt")
                VALUES (?, ?, ?, CURRENT_TIMESTAMP - INTERVAL '8 days',
                CASE WHEN ? THEN CURRENT_TIMESTAMP - INTERVAL '1 second' ELSE CURRENT_TIMESTAMP + INTERVAL '7 days' END)
                """, userId, sid, generator.hash(raw), expired); return raw;
    }
    private HttpResponse<String> list(Fixture f) throws Exception { return request("GET", BASE, null, f.access()); }
    private HttpResponse<String> revoke(Fixture f, UUID sid) throws Exception { return request("DELETE", BASE + "/" + sid, null, f.access()); }
    private HttpResponse<String> revokeOthers(Fixture f) throws Exception { return request("POST", BASE + "/revoke-others", null, f.access()); }
    private HttpResponse<String> refresh(String raw) throws Exception { return request("POST", "/api/v1/auth/refresh", mapper.writeValueAsString(new RefreshTokenRequest(raw)), null); }
    private HttpResponse<String> login(Fixture f) throws Exception { return request("POST", "/api/v1/auth/login", mapper.writeValueAsString(new LoginRequest(f.name(), PASSWORD)), null); }
    private HttpResponse<String> request(String method, String path, String body, String bearer) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).timeout(Duration.ofSeconds(20));
        if (bearer != null) request.header("Authorization", "Bearer " + bearer);
        if (body != null) request.header("Content-Type", "application/json");
        return client.send(request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    private Snapshot snapshot(int id) { return new Snapshot(jdbc.queryForMap("SELECT * FROM \"Users\" WHERE \"Id\" = ?", id),
            jdbc.queryForList("SELECT * FROM \"UserSessions\" WHERE \"UserId\" = ? ORDER BY \"Id\"", id),
            jdbc.queryForList("SELECT * FROM \"RefreshTokens\" WHERE \"UserId\" = ? ORDER BY \"Id\"", id)); }
    private Map<String, Object> session(UUID sid) { return jdbc.queryForMap("SELECT * FROM \"UserSessions\" WHERE \"Id\" = ?", sid); }
    private Map<String, Object> token(String raw) { return jdbc.queryForMap("SELECT * FROM \"RefreshTokens\" WHERE \"TokenHash\" = ?", generator.hash(raw)); }
    private void assertCode(HttpResponse<String> response, int status, String code) throws Exception {
        assertThat(response.statusCode()).isEqualTo(status); assertThat(mapper.readTree(response.body()).path("messageCode").asText()).isEqualTo(code);
        assertThat(response.body()).doesNotContain("private-sessions", "PasswordHash", "TokenHash", "UserAgent");
    }
    private Object real(Object spy, org.mockito.invocation.InvocationOnMock call) throws Throwable {
        return mockingDetails(spy).getMockCreationSettings().getDefaultAnswer().answer(call);
    }
    private void await(CountDownLatch latch) {
        try { if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("fixture-lock-timeout"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException("fixture-interrupted"); }
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
        fail("Không quan sát được chờ khóa user");
    }
    private record Fixture(int id, UUID sid, String access, String refresh, String name) {
        @Override public String toString() { return "Fixture[id=" + id + ", credentials=REDACTED]"; }
    }
    private record Snapshot(Map<String, Object> user, List<Map<String, Object>> sessions, List<Map<String, Object>> tokens) {
        @Override public String toString() { return "Snapshot[REDACTED]"; }
    }
}
