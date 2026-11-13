package com.library.userservice.user;

import com.library.userservice.security.RefreshTokenGenerator;
import com.library.userservice.security.UserAccessTokenService;
import com.library.userservice.security.UserSecurityTestKeys;
import com.library.userservice.user.persistence.RoleRepository;
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
import java.util.concurrent.atomic.AtomicBoolean;
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

/** HTTP/transaction/khóa thật trên database test; không dùng dữ liệu hoặc khóa dự án. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "debug=false")
@EnabledIfSystemProperty(named = "spring.datasource.url", matches = "jdbc:postgresql://[^/]+/UserDB_test(?:\\?.*)?")
class UserProfileIntegrationTests extends UserSecurityTestKeys {
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired JsonMapper mapper;
    @Autowired UserAccessTokenService jwt;
    @Autowired RefreshTokenGenerator generator;
    @MockitoSpyBean UserRepository users;
    @MockitoSpyBean RoleRepository roles;
    @MockitoSpyBean Clock clock;
    @LocalServerPort int port;
    private final HttpClient client = HttpClient.newHttpClient();
    private final List<Integer> fixtureIds = new ArrayList<>();

    @AfterEach void cleanup() {
        reset(users, roles, clock);
        for (int id : fixtureIds) jdbc.update("DELETE FROM \"Users\" WHERE \"Id\" = ?", id);
    }

    @Test void getUsesCurrentDatabaseProfileRolesAndProvidersWithoutChangingState() throws Exception {
        var fixture = fixture(); var before = user(fixture.id());
        var first = get(fixture); assertCode(first, 200, "PROFILE_GET_SUCCESS");
        var data = mapper.readTree(first.body()).path("data");
        assertThat(data.size()).isEqualTo(9); assertThat(data.path("hasPassword").asBoolean()).isFalse();
        assertThat(data.path("phoneNumber").isNull()).isTrue();
        assertThat(data.path("roles").isEmpty()).isTrue(); assertThat(data.path("linkedProviders").isEmpty()).isTrue();
        assertThat(user(fixture.id()).equals(before)).isTrue();
        jdbc.update("INSERT INTO \"UserRoles\" SELECT ?, \"Id\" FROM \"Roles\" WHERE \"Name\" IN ('READER', 'ADMIN')", fixture.id());
        jdbc.update("INSERT INTO \"UserIdentities\" (\"UserId\", \"Provider\", \"ProviderUserId\") VALUES (?, 'GOOGLE', ?)", fixture.id(), UUID.randomUUID().toString());
        jdbc.update("UPDATE \"Users\" SET \"PasswordHash\" = 'uc06-test-only' WHERE \"Id\" = ?", fixture.id());
        data = mapper.readTree(get(fixture).body()).path("data");
        assertThat(data.path("roles").toString()).isEqualTo("[\"ADMIN\",\"READER\"]");
        assertThat(data.path("linkedProviders").toString()).isEqualTo("[\"GOOGLE\"]");
        assertThat(data.path("hasPassword").asBoolean()).isTrue();
        assertThat(data.has("passwordHash")).isFalse(); assertThat(data.has("providerUserId")).isFalse();
    }

    @Test void patchPreservesOmittedFieldsOtherUsersAndSecurityStateAndHonorsTrigger() throws Exception {
        var a = fixture(); var b = fixture(); var otherBefore = user(b.id());
        var before = user(a.id()); var sessions = session(a); var tokens = tokens(a);
        var changed = patch(a, "{\"fullName\":\"  Nguyễn Văn A  \",\"phoneNumber\":\"+12345678\"}");
        assertCode(changed, 200, "PROFILE_UPDATE_SUCCESS");
        var after = user(a.id()); assertThat(after.get("FullName")).isEqualTo("Nguyễn Văn A");
        assertThat(after.get("PhoneNumber")).isEqualTo("+12345678");
        assertThat(((java.sql.Timestamp) after.get("UpdatedAt")).toInstant())
                .isAfter(((java.sql.Timestamp) before.get("UpdatedAt")).toInstant());
        for (String key : before.keySet()) if (!Set.of("FullName", "PhoneNumber", "UpdatedAt").contains(key))
            assertThat(java.util.Objects.equals(after.get(key), before.get(key))).isTrue();
        assertCode(patch(a, "{\"fullName\":\"Tên mới\"}"), 200, "PROFILE_UPDATE_SUCCESS");
        assertThat(user(a.id()).get("PhoneNumber")).isEqualTo("+12345678");
        var unchanged = user(a.id());
        assertCode(patch(a, "{\"fullName\":\" Tên mới \"}"), 200, "PROFILE_UPDATE_SUCCESS");
        assertThat(user(a.id()).equals(unchanged)).isTrue();
        assertCode(patch(a, "{\"phoneNumber\":null}"), 200, "PROFILE_UPDATE_SUCCESS");
        var data = mapper.readTree(get(a).body()).path("data");
        assertThat(data.path("phoneNumber").isNull()).isTrue(); assertThat(data.path("fullName").asText()).isEqualTo("Tên mới");
        assertThat(user(b.id()).equals(otherBefore)).isTrue();
        assertThat(session(a)).isEqualTo(sessions); assertThat(tokens(a).equals(tokens)).isTrue();
    }

    @Test void invalidPatchDoesNotPersistAnyFields() throws Exception {
        var fixture = fixture(); var before = user(fixture.id());
        for (String body : List.of("{}", "{\"fullName\":\"Changed\",\"phoneNumber\":\"bad\"}",
                "{\"fullName\":null}", "{\"roles\":[\"ADMIN\"]}", "{\"status\":\"BANNED\"}")) {
            assertCode(patch(fixture, body), 400, "VALIDATION_ERROR");
            assertThat(user(fixture.id()).equals(before)).isTrue();
        }
    }

    @Test void authenticationRejectsExpiredJwtAndRevokedSessionOnActualEndpoints() throws Exception {
        var fixture = fixture(); var before = user(fixture.id());
        Instant expires = jwt.verify(fixture.access()).getExpiresAt();
        doReturn(expires.plusSeconds(30)).when(clock).instant();
        assertCode(get(fixture), 401, "ACCESS_TOKEN_INVALID");
        assertCode(patch(fixture, "{\"fullName\":\"Changed\"}"), 401, "ACCESS_TOKEN_INVALID");
        reset(clock);
        jdbc.update("UPDATE \"UserSessions\" SET \"RevokedAt\" = statement_timestamp(), \"RevocationReason\" = 'LOGOUT' WHERE \"Id\" = ?", fixture.sid());
        assertCode(get(fixture), 401, "SESSION_INACTIVE");
        assertCode(patch(fixture, "{\"fullName\":\"Changed\"}"), 401, "SESSION_INACTIVE");
        assertThat(user(fixture.id()).equals(before)).isTrue();
    }

    @Test void saveResponseAssemblyAndCommitFailuresRollBackProfileAndTimestamp() throws Exception {
        var fixture = fixture(); var before = user(fixture.id());
        doAnswer(call -> {
            real(users, call); em.flush();
            throw new DataAccessResourceFailureException("private-uc06-save");
        }).when(users).save(any());
        assertCode(patch(fixture, "{\"fullName\":\"Changed\"}"), 503, "SERVICE_UNAVAILABLE");
        assertThat(user(fixture.id()).equals(before)).isTrue(); reset(users);
        // Filter đọc roles trước service; chỉ làm hỏng lần đọc trong transaction ghi.
        doAnswer(call -> {
            if (org.springframework.transaction.support.TransactionSynchronizationManager.isCurrentTransactionReadOnly()) return real(roles, call);
            em.flush(); throw new IllegalStateException("private-uc06-mapping");
        }).when(roles).findNamesByUserId(fixture.id());
        assertCode(patch(fixture, "{\"fullName\":\"Changed\"}"), 500, "INTERNAL_ERROR");
        assertThat(user(fixture.id()).equals(before)).isTrue(); reset(roles);
        doAnswer(call -> {
            var result = real(roles, call);
            if (!org.springframework.transaction.support.TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
                jdbc.execute("CREATE TEMP TABLE uc06_commit_fixture (id integer UNIQUE DEFERRABLE INITIALLY DEFERRED) ON COMMIT DROP");
                jdbc.update("INSERT INTO uc06_commit_fixture VALUES (1), (1)");
            }
            return result;
        }).when(roles).findNamesByUserId(fixture.id());
        assertCode(patch(fixture, "{\"fullName\":\"Changed\"}"), 500, "INTERNAL_ERROR");
        assertThat(user(fixture.id()).equals(before)).isTrue();
    }

    @Test void simultaneousPatchesSerializeWithoutLosingOmittedFieldsAndLastWriterWinsSameField() throws Exception {
        for (boolean sameField : List.of(false, true)) {
            var fixture = fixture(); var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
            var first = new AtomicBoolean(true);
            doAnswer(call -> {
                var result = real(users, call);
                if (first.compareAndSet(true, false)) { entered.countDown(); await(release); }
                return result;
            }).when(users).findByIdForUpdate(fixture.id());
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                var a = executor.submit(() -> patch(fixture, "{\"fullName\":\"First\"}"));
                try {
                    assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                    var b = executor.submit(() -> patch(fixture, sameField ? "{\"fullName\":\"Second\"}" : "{\"phoneNumber\":\"12345678\"}"));
                    assertUserLockWait(); assertThat(b.isDone()).isFalse(); release.countDown();
                    assertCode(a.get(15, TimeUnit.SECONDS), 200, "PROFILE_UPDATE_SUCCESS");
                    assertCode(b.get(15, TimeUnit.SECONDS), 200, "PROFILE_UPDATE_SUCCESS");
                    assertThat(user(fixture.id()).get("FullName")).isEqualTo(sameField ? "Second" : "First");
                    if (!sameField) assertThat(user(fixture.id()).get("PhoneNumber")).isEqualTo("12345678");
                } finally { release.countDown(); }
            } finally { reset(users); }
        }
    }

    @Test void patchRechecksBannedRevokedOrExpiredStateAfterWaitingForRealUserLock() throws Exception {
        for (String change : List.of("ban", "revoke", "expire")) {
            var fixture = fixture(); var before = user(fixture.id()); var tokensBefore = tokens(fixture);
            var locked = new CountDownLatch(1); var release = new CountDownLatch(1);
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                var writer = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                    jdbc.queryForObject("SELECT \"Id\" FROM \"Users\" WHERE \"Id\" = ? FOR UPDATE", Integer.class, fixture.id());
                    if (change.equals("ban")) jdbc.update("UPDATE \"Users\" SET \"Status\" = 'BANNED' WHERE \"Id\" = ?", fixture.id());
                    if (change.equals("revoke")) jdbc.update("UPDATE \"UserSessions\" SET \"RevokedAt\" = statement_timestamp(), \"RevocationReason\" = 'LOGOUT' WHERE \"Id\" = ?", fixture.sid());
                    locked.countDown(); await(release); return null;
                }));
                try {
                    assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
                    var pending = executor.submit(() -> patch(fixture, "{\"fullName\":\"Changed\"}"));
                    assertUserLockWait(); assertThat(pending.isDone()).isFalse();
                    if (change.equals("expire")) {
                        Instant expiry = ((java.sql.Timestamp) session(fixture).get("ExpiresAt")).toInstant();
                        doReturn(expiry).when(clock).instant();
                    }
                    release.countDown(); writer.get(10, TimeUnit.SECONDS);
                    assertCode(pending.get(15, TimeUnit.SECONDS), 401, "SESSION_INACTIVE");
                    assertThat(user(fixture.id()).get("FullName")).isEqualTo(before.get("FullName"));
                    if (!change.equals("ban")) assertThat(user(fixture.id()).equals(before)).isTrue();
                    assertThat(tokens(fixture).equals(tokensBefore)).isTrue();
                } finally { release.countDown(); }
            } finally { reset(clock); }
        }
    }

    @Test void fullContextPublishesExactlySixImplementedOperationsWithoutDuplicates() throws Exception {
        var response = request("GET", "/v3/api-docs", null, null); assertThat(response.statusCode()).isEqualTo(200);
        var paths = mapper.readTree(response.body()).path("paths");
        var ids = new java.util.HashSet<String>(); var implemented = new java.util.HashSet<String>(); int count = 0;
        for (var path : paths) for (var operation : path) {
            count++; String id = operation.path("operationId").asText(); assertThat(ids.add(id)).isTrue();
            if (operation.path("x-implementation-status").asText().equals("IMPLEMENTED")) implemented.add(id);
        }
        assertThat(count).isEqualTo(20);
        assertThat(implemented).containsExactlyInAnyOrder("login", "refreshTokens", "logout", "getMyProfile", "updateMyProfile", "changePassword");
    }

    private Fixture fixture() {
        String name = "uc06-" + UUID.randomUUID();
        int id = jdbc.queryForObject("INSERT INTO \"Users\" (\"UserName\", \"Email\", \"FullName\") VALUES (?, ?, 'UC06 Fixture') RETURNING \"Id\"",
                Integer.class, name, name + "@example.com");
        fixtureIds.add(id); UUID sid = UUID.randomUUID();
        jdbc.update("INSERT INTO \"UserSessions\" (\"Id\", \"UserId\", \"ExpiresAt\") VALUES (?, ?, CURRENT_TIMESTAMP + INTERVAL '30 days')", sid, id);
        jdbc.update("INSERT INTO \"RefreshTokens\" (\"UserId\", \"SessionId\", \"TokenHash\", \"ExpiresAt\") VALUES (?, ?, ?, CURRENT_TIMESTAMP + INTERVAL '7 days')",
                id, sid, generator.hash(generator.generate()));
        return new Fixture(id, sid, jwt.issue(id, sid));
    }

    private Map<String, Object> user(int id) { return jdbc.queryForMap("SELECT * FROM \"Users\" WHERE \"Id\" = ?", id); }
    private Map<String, Object> session(Fixture fixture) { return jdbc.queryForMap("SELECT * FROM \"UserSessions\" WHERE \"Id\" = ?", fixture.sid()); }
    private List<Map<String, Object>> tokens(Fixture fixture) { return jdbc.queryForList("SELECT * FROM \"RefreshTokens\" WHERE \"UserId\" = ? ORDER BY \"Id\"", fixture.id()); }
    private HttpResponse<String> get(Fixture fixture) throws Exception { return request("GET", "/api/v1/users/me", null, fixture.access()); }
    private HttpResponse<String> patch(Fixture fixture, String body) throws Exception { return request("PATCH", "/api/v1/users/me?userId=999", body, fixture.access()); }
    private HttpResponse<String> request(String method, String path, String body, String bearer) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).timeout(Duration.ofSeconds(20)).header("X-User-Id", "999");
        if (bearer != null) request.header("Authorization", "Bearer " + bearer);
        if (body != null) request.header("Content-Type", "application/json");
        return client.send(request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    private void assertCode(HttpResponse<String> response, int status, String code) throws Exception {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(mapper.readTree(response.body()).path("messageCode").asText()).isEqualTo(code);
        assertThat(response.body()).doesNotContain("private-uc06", "PasswordHash", "TokenHash");
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
    private record Fixture(int id, UUID sid, String access) {
        @Override public String toString() { return "Fixture[id=" + id + ", access=REDACTED]"; }
    }
}
