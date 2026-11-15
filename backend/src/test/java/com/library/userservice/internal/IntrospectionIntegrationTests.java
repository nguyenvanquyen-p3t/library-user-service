package com.library.userservice.internal;

import com.library.userservice.internal.web.IntrospectionController;
import com.library.userservice.security.PasswordService;
import com.library.userservice.security.UserAccessTokenService;
import com.library.userservice.security.UserSecurityTestKeys;
import com.library.userservice.user.persistence.UserRepository;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.RSASSASigner;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** HTTP/JWT và transaction đọc thật; chỉ dùng fixture/khóa tạm trên UserDB_test. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "debug=false")
@EnabledIfSystemProperty(named = "spring.datasource.url", matches = "jdbc:postgresql://[^/]+/UserDB_test(?:\\?.*)?")
class IntrospectionIntegrationTests extends UserSecurityTestKeys {
    @Autowired JdbcTemplate jdbc;
    @Autowired JsonMapper mapper;
    @Autowired UserAccessTokenService jwt;
    @Autowired PasswordService passwords;
    @MockitoSpyBean UserRepository users;
    @MockitoSpyBean Clock clock;
    @LocalServerPort int port;
    private final HttpClient client = HttpClient.newHttpClient();
    private final List<Integer> fixtureIds = new ArrayList<>();
    private static final String PASSWORD = "fixture password";

    @AfterEach void cleanup() {
        reset(users, clock);
        for (int id : fixtureIds) jdbc.update("DELETE FROM \"Users\" WHERE \"Id\" = ?", id);
    }

    @Test void loginTokenUsesCurrentRolesAndIntrospectionDoesNotWriteAnySecurityState() throws Exception {
        var f = fixture(); var before = snapshot(f.id()); var decoded = jwt.verify(f.access());
        var body = introspect(f.access(), true);
        assertThat(body.size()).isEqualTo(9); assertThat(body.path("sub").asText()).isEqualTo(Integer.toString(f.id()));
        assertThat(body.path("sid").asText()).isEqualTo(f.sid().toString());
        assertThat(body.path("roles").isEmpty()).isTrue(); assertThat(body.path("aud").toString()).isEqualTo("[\"library-api\"]");
        assertThat(body.path("exp").asLong()).isEqualTo(decoded.getExpiresAt().getEpochSecond());
        assertThat(body.path("iat").asLong()).isEqualTo(decoded.getIssuedAt().getEpochSecond());
        assertThat(snapshot(f.id()).equals(before)).isTrue();
        jdbc.update("INSERT INTO \"UserRoles\" SELECT ?, \"Id\" FROM \"Roles\" WHERE \"Name\" IN ('ADMIN', 'READER')", f.id());
        before = snapshot(f.id());
        assertThat(introspect(f.access(), true).path("roles").toString()).isEqualTo("[\"ADMIN\",\"READER\"]");
        assertThat(snapshot(f.id()).equals(before)).isTrue();
    }

    @Test void inactiveResultsSurviveRealTransactionalAuthenticatorAndRespectOwnership() throws Exception {
        var f = fixture(); var other = fixture();
        introspect(jwt.issue(f.id(), other.sid()), false);
        introspect(jwt.issue(f.id(), UUID.randomUUID()), false);
        jdbc.update("UPDATE \"Users\" SET \"Status\" = 'BANNED' WHERE \"Id\" = ?", f.id());
        introspect(f.access(), false);
        jdbc.update("UPDATE \"Users\" SET \"Status\" = 'ACTIVE' WHERE \"Id\" = ?", f.id());
        jdbc.update("UPDATE \"UserSessions\" SET \"RevokedAt\" = CURRENT_TIMESTAMP, \"RevocationReason\" = 'LOGOUT' WHERE \"Id\" = ?", f.sid());
        introspect(f.access(), false);
        jdbc.update("DELETE FROM \"Users\" WHERE \"Id\" = ?", other.id());
        introspect(other.access(), false);
    }

    @Test void absoluteSessionExpiryHasNoSkewEvenWhenJwtRemainsValid() throws Exception {
        var f = fixture();
        Instant expires = ((java.sql.Timestamp) jdbc.queryForMap("SELECT * FROM \"UserSessions\" WHERE \"Id\" = ?", f.sid()).get("ExpiresAt")).toInstant();
        doReturn(expires.minusNanos(1)).when(clock).instant();
        String access = jwt.issue(f.id(), f.sid()); introspect(access, true);
        doReturn(expires).when(clock).instant(); introspect(access, false);
        assertThat(jwt.verify(access).getExpiresAt()).isAfter(expires);
    }

    @Test void invalidCryptoPurposeClaimsAndJwtTimeBoundariesReturnInactive() throws Exception {
        var f = fixture(); Instant issued = jwt.verify(f.access()).getIssuedAt();
        for (var entry : Map.<String, Object>of("iss", "wrong-issuer", "aud", List.of("wrong-audience"),
                "sub", 3, "sid", "invalid").entrySet()) {
            var claims = claims(f, issued); claims.put(entry.getKey(), entry.getValue());
            introspect(sign(claims, "at+jwt"), false);
        }
        introspect(sign(claims(f, issued), "JWT"), false);
        var claims = claims(f, issued); claims.put("purpose", "registration"); claims.remove("sid");
        introspect(sign(claims, "JWT"), false);
        var parsed = com.nimbusds.jwt.SignedJWT.parse(f.access()); var changed = claims(f, issued); changed.put("sub", "2147483647");
        String tampered = parsed.getHeader().toBase64URL() + "." + new Payload(changed).toBase64URL() + "." + parsed.getSignature();
        introspect(tampered, false); introspect(f.refresh(), false);
        doReturn(issued.plusSeconds(3629)).when(clock).instant(); introspect(f.access(), true);
        doReturn(issued.plusSeconds(3630)).when(clock).instant(); introspect(f.access(), false);
        doReturn(issued.minusSeconds(30)).when(clock).instant(); introspect(f.access(), true);
        doReturn(issued.minusSeconds(31)).when(clock).instant(); introspect(f.access(), false);
    }

    @Test void rotationKeepsOldAccessActiveAndLogoutWithOldRefreshInvalidatesTheSession() throws Exception {
        var f = fixture();
        var response = json("POST", "/api/v1/auth/refresh", Map.of("refreshToken", f.refresh()), null);
        assertThat(response.statusCode()).isEqualTo(200);
        String successor = mapper.readTree(response.body()).path("data").path("accessToken").asText();
        introspect(f.access(), true); introspect(successor, true);
        response = json("POST", "/api/v1/auth/logout", Map.of("refreshToken", f.refresh()), null);
        assertThat(response.statusCode()).isEqualTo(200);
        introspect(f.access(), false); introspect(successor, false);
    }

    @Test void passwordChangeAndBothRevocationApisInvalidateOnlyTheirTargetSessionsAfterCommit() throws Exception {
        var f = fixture(); var target = login(f.id(), f.name());
        var response = json("DELETE", "/api/v1/users/me/sessions/" + target.sid(), null, f.access());
        assertThat(response.statusCode()).isEqualTo(200); introspect(target.access(), false); introspect(f.access(), true);
        var another = login(f.id(), f.name());
        response = json("POST", "/api/v1/users/me/sessions/revoke-others", null, f.access());
        assertThat(response.statusCode()).isEqualTo(200); introspect(another.access(), false); introspect(f.access(), true);
        var last = login(f.id(), f.name());
        response = json("PUT", "/api/v1/users/me/password", Map.of("currentPassword", PASSWORD,
                "newPassword", "new fixture password", "confirmPassword", "new fixture password"), f.access());
        assertThat(response.statusCode()).isEqualTo(200); introspect(f.access(), false); introspect(last.access(), false);
    }

    @Test void readFailuresDoNotMasqueradeAsInactiveOrExposePrivateDetails() throws Exception {
        var f = fixture();
        doThrow(new DataAccessResourceFailureException("private-introspection-db")).when(users).findById(f.id());
        error(f.access(), 503, "temporarily_unavailable");
        doThrow(new IllegalStateException("private-introspection-error")).when(users).findById(f.id());
        error(f.access(), 500, "server_error");
    }

    private Fixture fixture() throws Exception {
        String name = "introspection-" + UUID.randomUUID().toString().substring(0, 20);
        int id = jdbc.queryForObject("INSERT INTO \"Users\" (\"UserName\", \"Email\", \"FullName\", \"PasswordHash\") VALUES (?, ?, 'Introspection Fixture', ?) RETURNING \"Id\"",
                Integer.class, name, name + "@example.com", passwords.encode(PASSWORD));
        fixtureIds.add(id); return login(id, name);
    }
    private Fixture login(int id, String name) throws Exception {
        var response = json("POST", "/api/v1/auth/login", Map.of("identifier", name, "password", PASSWORD), null);
        assertThat(response.statusCode()).isEqualTo(200); var data = mapper.readTree(response.body()).path("data");
        String access = data.path("accessToken").asText();
        return new Fixture(id, name, UUID.fromString(jwt.verify(access).getClaimAsString("sid")), access, data.path("refreshToken").asText());
    }
    private JsonNode introspect(String token, boolean active) throws Exception {
        var response = post(token); assertProtocol(response, 200); var body = mapper.readTree(response.body());
        assertThat(body.path("active").asBoolean()).as(response.body()).isEqualTo(active);
        if (!active) assertThat(response.body()).isEqualTo("{\"active\":false}");
        return body;
    }
    private void error(String token, int status, String error) throws Exception {
        var response = post(token); assertProtocol(response, status);
        assertThat(response.body()).isEqualTo("{\"error\":\"" + error + "\"}");
    }
    private HttpResponse<String> post(String token) throws Exception {
        return send("POST", IntrospectionController.PATH, "token=" + URLEncoder.encode(token, StandardCharsets.UTF_8),
                "application/x-www-form-urlencoded", null);
    }
    private HttpResponse<String> json(String method, String path, Object body, String bearer) throws Exception {
        return send(method, path, body == null ? "" : mapper.writeValueAsString(body), "application/json", bearer);
    }
    private HttpResponse<String> send(String method, String path, String body, String type, String bearer) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(java.time.Duration.ofSeconds(15)).header("Content-Type", type);
        if (bearer != null) builder.header("Authorization", "Bearer " + bearer);
        return client.send(builder.method(method, HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    private void assertProtocol(HttpResponse<String> response, int status) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
        assertThat(response.headers().firstValue("Content-Type").orElse("")).startsWith("application/json");
        assertThat(response.headers().firstValue("Set-Cookie")).isEmpty();
    }
    private List<Object> snapshot(int id) {
        return List.of(jdbc.queryForMap("SELECT * FROM \"Users\" WHERE \"Id\" = ?", id),
                jdbc.queryForList("SELECT * FROM \"UserSessions\" WHERE \"UserId\" = ? ORDER BY \"Id\"", id),
                jdbc.queryForList("SELECT * FROM \"RefreshTokens\" WHERE \"UserId\" = ? ORDER BY \"Id\"", id));
    }
    private Map<String, Object> claims(Fixture f, Instant issued) {
        return new HashMap<>(Map.of("iss", "urn:library:user-service", "aud", List.of("library-api"), "sub", Integer.toString(f.id()),
                "sid", f.sid().toString(), "iat", issued.getEpochSecond(), "exp", issued.plusSeconds(3600).getEpochSecond()));
    }
    private String sign(Map<String, Object> claims, String type) throws Exception {
        var object = new JWSObject(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("user-access-v1").type(new JOSEObjectType(type)).build(), new Payload(claims));
        object.sign(new RSASSASigner(KEYS.getPrivate())); return object.serialize();
    }
    private record Fixture(int id, String name, UUID sid, String access, String refresh) {
        @Override public String toString() { return "Fixture[id=" + id + ", tokens=REDACTED]"; }
    }
}
