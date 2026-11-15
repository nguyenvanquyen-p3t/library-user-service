package com.library.userservice.internal;

import com.library.userservice.security.UserSecurityTestKeys;
import com.library.userservice.user.persistence.UserRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** HTTP/JPA và transaction đọc thật, fixture riêng trong UserDB_test. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "debug=false")
@EnabledIfSystemProperty(named = "spring.datasource.url", matches = "jdbc:postgresql://[^/]+/UserDB_test(?:\\?.*)?")
class InternalUserIntegrationTests extends UserSecurityTestKeys {
    @Autowired JdbcTemplate jdbc;
    @Autowired JsonMapper mapper;
    @MockitoSpyBean UserRepository users;
    @LocalServerPort int port;
    private final HttpClient client = HttpClient.newHttpClient();
    private final List<Integer> fixtureIds = new ArrayList<>();

    @AfterEach void cleanup() {
        reset(users);
        for (int id : fixtureIds) jdbc.update("DELETE FROM \"Users\" WHERE \"Id\" = ?", id);
    }

    @Test void readsActiveAndBannedUsersWithExactSummaryWithoutChangingData() throws Exception {
        int id = fixture();
        for (String status : List.of("ACTIVE", "BANNED")) {
            jdbc.update("UPDATE \"Users\" SET \"Status\" = ? WHERE \"Id\" = ?", status, id);
            var before = snapshot(id);
            var response = get(id);
            assertCode(response, 200, "USER_GET_SUCCESS");
            var data = mapper.readTree(response.body()).path("data");
            assertThat(data.size()).isEqualTo(5);
            assertThat(data.path("id").asInt()).isEqualTo(id);
            assertThat(data.path("userName").asText()).startsWith("internal-");
            assertThat(data.path("fullName").asText()).isEqualTo("Nguyễn Văn A");
            assertThat(data.path("status").asText()).isEqualTo(status);
            assertThat(data.path("roles").toString()).isEqualTo("[]");
            assertThat(snapshot(id)).isEqualTo(before);
            assertThat(response.body()).doesNotContain("private-fixture", "@example.com", "ProviderUserId", "tokenHash");
        }
    }

    @Test void readsCurrentSortedRolesAcrossRequests() throws Exception {
        int id = fixture();
        jdbc.update("INSERT INTO \"UserRoles\" SELECT ?, \"Id\" FROM \"Roles\" WHERE \"Name\" IN ('READER', 'ADMIN')", id);
        var before = snapshot(id);
        assertThat(mapper.readTree(get(id).body()).path("data").path("roles").toString()).isEqualTo("[\"ADMIN\",\"READER\"]");
        assertThat(snapshot(id)).isEqualTo(before);
        jdbc.update("DELETE FROM \"UserRoles\" WHERE \"UserId\" = ?", id);
        assertThat(mapper.readTree(get(id).body()).path("data").path("roles").toString()).isEqualTo("[]");
    }

    @Test void executesRepositoryReadInRealReadOnlyReadCommittedTransaction() throws Exception {
        int id = fixture();
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isTrue();
            assertThat(jdbc.queryForObject("SHOW transaction_read_only", String.class)).isEqualTo("on");
            assertThat(jdbc.queryForObject("SHOW transaction_isolation", String.class)).isEqualTo("read committed");
            return mockingDetails(users).getMockCreationSettings().getDefaultAnswer().answer(invocation);
        }).when(users).findById(id);
        assertCode(get(id), 200, "USER_GET_SUCCESS");
    }

    @Test void missingUserAndInvalidIdReturnContractErrorsWithoutAuthentication() throws Exception {
        int id = fixture();
        jdbc.update("DELETE FROM \"Users\" WHERE \"Id\" = ?", id);
        assertCode(get(id), 404, "USER_NOT_FOUND");
        assertCode(get(0), 400, "VALIDATION_ERROR");
        var response = send("GET", "/api/v1/internal/users/2147483648");
        assertCode(response, 400, "VALIDATION_ERROR");
        assertThat(mapper.readTree(response.body()).path("data").path("errors").get(0).path("code").asText()).isEqualTo("TYPE_MISMATCH");
    }

    private int fixture() {
        String name = "internal-" + UUID.randomUUID();
        int id = jdbc.queryForObject("INSERT INTO \"Users\" (\"UserName\", \"Email\", \"FullName\", \"PasswordHash\") VALUES (?, ?, ?, ?) RETURNING \"Id\"",
                Integer.class, name, name + "@example.com", "Nguyễn Văn A", "private-fixture-hash");
        fixtureIds.add(id);
        UUID sid = UUID.randomUUID();
        jdbc.update("INSERT INTO \"UserSessions\" (\"Id\", \"UserId\", \"ExpiresAt\") VALUES (?, ?, CURRENT_TIMESTAMP + INTERVAL '30 days')", sid, id);
        jdbc.update("INSERT INTO \"RefreshTokens\" (\"UserId\", \"SessionId\", \"TokenHash\", \"ExpiresAt\") VALUES (?, ?, ?, CURRENT_TIMESTAMP + INTERVAL '7 days')",
                id, sid, "private-fixture-" + UUID.randomUUID());
        jdbc.update("INSERT INTO \"UserIdentities\" (\"UserId\", \"Provider\", \"ProviderUserId\") VALUES (?, 'GOOGLE', ?)", id, UUID.randomUUID().toString());
        return id;
    }

    private List<Object> snapshot(int id) {
        return List.of(jdbc.queryForMap("SELECT * FROM \"Users\" WHERE \"Id\" = ?", id),
                jdbc.queryForList("SELECT * FROM \"UserRoles\" WHERE \"UserId\" = ? ORDER BY \"RoleId\"", id),
                jdbc.queryForList("SELECT * FROM \"UserIdentities\" WHERE \"UserId\" = ? ORDER BY \"Id\"", id),
                jdbc.queryForList("SELECT * FROM \"UserSessions\" WHERE \"UserId\" = ? ORDER BY \"Id\"", id),
                jdbc.queryForList("SELECT * FROM \"RefreshTokens\" WHERE \"UserId\" = ? ORDER BY \"Id\"", id));
    }

    private HttpResponse<String> get(int id) throws Exception { return send("GET", "/api/v1/internal/users/" + id); }

    private HttpResponse<String> send(String method, String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(java.time.Duration.ofSeconds(15)).header("Accept", "application/json")
                .method(method, HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
    }

    private void assertCode(HttpResponse<String> response, int status, String code) throws Exception {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
        assertThat(response.headers().firstValue("Set-Cookie")).isEmpty();
        var body = mapper.readTree(response.body());
        assertThat(body.path("messageCode").asText()).isEqualTo(code);
        if (status == 404) assertThat(body.path("data").isNull()).isTrue();
    }
}
