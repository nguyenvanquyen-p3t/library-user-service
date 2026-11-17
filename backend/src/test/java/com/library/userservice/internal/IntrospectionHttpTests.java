package com.library.userservice.internal;

import com.library.userservice.common.exception.ApiExceptionHandler;
import com.library.userservice.config.ApiSecurityConfiguration;
import com.library.userservice.config.JsonConfiguration;
import com.library.userservice.config.UserSecurityConfiguration;
import com.library.userservice.internal.service.IntrospectionService;
import com.library.userservice.internal.web.IntrospectionController;
import com.library.userservice.security.SecurityErrorWriter;
import com.library.userservice.security.UserAccessTokenService;
import com.library.userservice.security.UserSecurityTestKeys;
import com.library.userservice.security.UserSessionAuthenticator;
import com.library.userservice.session.persistence.UserSession;
import com.library.userservice.session.persistence.UserSessionRepository;
import com.library.userservice.user.persistence.RoleRepository;
import com.library.userservice.user.persistence.User;
import com.library.userservice.user.persistence.UserRepository;
import com.library.userservice.user.persistence.UserStatus;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.CannotCreateTransactionException;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = IntrospectionHttpTests.Application.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.config.import=", "debug=false"})
class IntrospectionHttpTests extends UserSecurityTestKeys {
    @Configuration(proxyBeanMethods = false)
    @TestComponent
    @EnableAutoConfiguration(excludeName = {"org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
            "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration", "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration"})
    @Import({ApiSecurityConfiguration.class, UserSecurityConfiguration.class, JsonConfiguration.class, SecurityErrorWriter.class,
            UserSessionAuthenticator.class, ApiExceptionHandler.class, IntrospectionController.class, IntrospectionService.class})
    static class Application {
        @Bean com.library.userservice.security.AdminActorAuthenticator adminActors() { return org.mockito.Mockito.mock(com.library.userservice.security.AdminActorAuthenticator.class); }
        @Bean UserRepository users() { return mock(UserRepository.class); }
        @Bean UserSessionRepository sessions() { return mock(UserSessionRepository.class); }
        @Bean RoleRepository roles() { return mock(RoleRepository.class); }
    }
    @Autowired UserRepository users;
    @Autowired UserSessionRepository sessions;
    @Autowired RoleRepository roles;
    @Autowired UserAccessTokenService jwt;
    @Autowired JsonMapper mapper;
    @LocalServerPort int port;
    private final HttpClient client = HttpClient.newHttpClient();
    private final UUID sid = UUID.randomUUID();
    private String token;
    private User user;
    private UserSession session;
    private static final String PATH = IntrospectionController.PATH;
    private static final String FORM = "application/x-www-form-urlencoded";

    @BeforeEach void setup() {
        reset(users, sessions, roles);
        user = new User(); user.setStatus(UserStatus.ACTIVE);
        session = new UserSession(); session.setUserId(3); session.setExpiresAt(Instant.now().plusSeconds(3600));
        when(users.findById(3)).thenReturn(Optional.of(user)); when(sessions.findById(sid)).thenReturn(Optional.of(session));
        when(roles.findNamesByUserId(3)).thenReturn(List.of("ADMIN", "READER")); token = jwt.issue(3, sid);
    }

    @Test void returnsExactActiveSchemaWithoutServiceAuthenticationAndIgnoresAuthorizationAndHint() throws Exception {
        var decoded = jwt.verify(token);
        for (String authorization : List.of("", "Bearer invalid", "Basic ignored")) {
            var response = send("POST", PATH, "token=" + token + "&token_type_hint=refresh_token", FORM, authorization);
            assertHeaders(response, 200); var body = mapper.readTree(response.body());
            assertThat(body.size()).isEqualTo(9); assertThat(body.path("active").asBoolean()).isTrue();
            assertThat(body.path("sub").asText()).isEqualTo("3"); assertThat(body.path("sid").asText()).isEqualTo(sid.toString());
            assertThat(body.path("token_type").asText()).isEqualTo("Bearer");
            assertThat(body.path("iss").asText()).isEqualTo("urn:library:user-service");
            assertThat(body.path("aud").toString()).isEqualTo("[\"library-api\"]");
            assertThat(body.path("exp").asLong()).isEqualTo(decoded.getExpiresAt().getEpochSecond());
            assertThat(body.path("iat").asLong()).isEqualTo(decoded.getIssuedAt().getEpochSecond());
            assertThat(body.path("roles").toString()).isEqualTo("[\"ADMIN\",\"READER\"]");
        }
        when(roles.findNamesByUserId(3)).thenReturn(List.of());
        assertThat(mapper.readTree(post("token=" + token).body()).path("roles").isEmpty()).isTrue();
    }

    @Test void invalidTokensAreInactiveAndWhitespaceIsNotTrimmed() throws Exception {
        for (String value : List.of("not-a-jwt", "opaque-refresh-token", " " + token + " ", "   ")) {
            var response = post("token=" + URLEncoder.encode(value, StandardCharsets.UTF_8));
            assertHeaders(response, 200); assertThat(response.body()).isEqualTo("{\"active\":false}");
        }
        verifyNoInteractions(users, sessions, roles);
    }

    @Test void inactiveUserAndSessionReturnOnlyFalse() throws Exception {
        user.setStatus(UserStatus.BANNED); inactive(); user.setStatus(UserStatus.ACTIVE);
        session.setUserId(4); inactive(); session.setUserId(3);
        session.setRevokedAt(Instant.now()); inactive(); session.setRevokedAt(null);
        session.setExpiresAt(Instant.now().minusSeconds(1)); inactive(); session.setExpiresAt(Instant.now().plusSeconds(3600));
        when(sessions.findById(sid)).thenReturn(Optional.empty()); inactive();
        when(users.findById(3)).thenReturn(Optional.empty()); inactive();
    }

    @Test void rejectsMalformedMissingDuplicateAndUnknownFormFieldsWithoutUsingQueryOrCookie() throws Exception {
        for (String body : List.of("", "token=", "token_type_hint=access_token", "token=a&token=b",
                "token=a&token_type_hint=a&token_type_hint=b", "token=a&userId=3", "token=%ZZ")) {
            var response = post(body); assertError(response, 400, "invalid_request");
        }
        assertError(send("POST", PATH + "?token=" + token, "token_type_hint=access_token", FORM, ""), 400, "invalid_request");
        assertThat(send("POST", PATH + "?token=" + token, "token=invalid", FORM, "").body()).isEqualTo("{\"active\":false}");
        // Body là nguồn token duy nhất; query không thay thế hoặc ghi đè form.
        assertThat(post("token=" + token + "&token_type_hint=").statusCode()).isEqualTo(200);
        assertError(send("POST", PATH, "{\"token\":\"x\"}", "application/json", ""), 415, "invalid_request");
        assertError(send("POST", PATH, "token=x", "text/plain", ""), 415, "invalid_request");
    }

    @Test void unsupportedMethodsUseProtocolErrorsIncludingOptionsAndHead() throws Exception {
        for (String method : List.of("GET", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD")) {
            var response = send(method, PATH, "", null, "Bearer ignored"); assertHeaders(response, 405);
            assertThat(response.headers().firstValue("Allow")).contains("POST");
            if (method.equals("HEAD")) assertThat(response.body()).isEmpty();
            else assertThat(response.body()).isEqualTo("{\"error\":\"invalid_request\"}");
        }
        verifyNoInteractions(users, sessions, roles);
    }

    @Test void infrastructureAndUnexpectedErrorsRemainErrorsWithoutPrivateDetails() throws Exception {
        for (RuntimeException error : List.of(new DataAccessResourceFailureException("private-db"),
                new CannotCreateTransactionException("private-transaction"), new IllegalStateException("private-error"))) {
            doThrow(error).when(users).findById(3);
            boolean unavailable = !(error instanceof IllegalStateException);
            assertError(post("token=" + token), unavailable ? 503 : 500, unavailable ? "temporarily_unavailable" : "server_error");
        }
    }

    @Test void neighboringInternalAndUserPathsStillRequireBearer() throws Exception {
        for (String path : List.of("/api/v1/internal/users/3/status", "/api/v1/users/me", PATH + "/extra")) {
            var response = send("GET", path, "", null, "");
            assertThat(response.statusCode()).isEqualTo(401);
            assertThat(mapper.readTree(response.body()).path("messageCode").asText()).isEqualTo("AUTHENTICATION_REQUIRED");
        }
    }

    private void inactive() throws Exception { var response = post("token=" + token); assertHeaders(response, 200); assertThat(response.body()).isEqualTo("{\"active\":false}"); }
    private HttpResponse<String> post(String body) throws Exception { return send("POST", PATH, body, FORM, ""); }
    private HttpResponse<String> send(String method, String path, String body, String type, String authorization) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).header("Accept", "application/json")
                .header("Cookie", "token=" + token);
        if (type != null) builder.header("Content-Type", type);
        if (!authorization.isEmpty()) builder.header("Authorization", authorization);
        return client.send(builder.method(method, HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    private void assertError(HttpResponse<String> response, int status, String error) {
        assertHeaders(response, status); assertThat(response.body()).isEqualTo("{\"error\":\"" + error + "\"}");
    }
    private void assertHeaders(HttpResponse<String> response, int status) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).startsWith("application/json");
        assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
        assertThat(response.headers().firstValue("Set-Cookie")).isEmpty();
        assertThat(response.headers().firstValue("WWW-Authenticate")).isEmpty();
    }
}
