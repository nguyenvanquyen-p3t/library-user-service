package com.library.userservice.security;

import com.library.userservice.auth.dto.TokenPair;
import com.library.userservice.auth.service.LoginService;
import com.library.userservice.auth.web.LoginController;
import com.library.userservice.common.exception.ApiExceptionHandler;
import com.library.userservice.config.ApiSecurityConfiguration;
import com.library.userservice.config.DocumentationSecurityConfiguration;
import com.library.userservice.config.JsonConfiguration;
import com.library.userservice.config.UserSecurityConfiguration;
import com.library.userservice.session.persistence.UserSession;
import com.library.userservice.session.persistence.UserSessionRepository;
import com.library.userservice.user.persistence.RoleRepository;
import com.library.userservice.user.persistence.User;
import com.library.userservice.user.persistence.UserRepository;
import com.library.userservice.user.persistence.UserStatus;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = UserSecurityHttpTests.Application.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.config.import=", "debug=false"})
class UserSecurityHttpTests extends UserSecurityTestKeys {
    @Configuration(proxyBeanMethods = false)
    @TestComponent
    @EnableAutoConfiguration(excludeName = {"org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
            "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration", "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration"})
    @Import({ApiSecurityConfiguration.class, DocumentationSecurityConfiguration.class, UserSecurityConfiguration.class,
            JsonConfiguration.class, SecurityErrorWriter.class, UserSessionAuthenticator.class, ApiExceptionHandler.class,
            FixtureController.class, LoginController.class})
    static class Application {
        @Bean UserRepository users() { return mock(UserRepository.class); }
        @Bean UserSessionRepository sessions() { return mock(UserSessionRepository.class); }
        @Bean RoleRepository roles() { return mock(RoleRepository.class); }
        @Bean LoginService login() { return mock(LoginService.class); }
    }

    @Autowired UserRepository users;
    @Autowired UserSessionRepository sessions;
    @Autowired RoleRepository roles;
    @Autowired LoginService login;
    @Autowired UserAccessTokenService tokens;
    @Autowired JsonMapper mapper;
    @LocalServerPort int port;
    private final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    private final UUID sid = UUID.randomUUID();
    private User user;
    private UserSession session;
    private String access;

    @BeforeEach void setup() {
        reset(users, sessions, roles, login);
        user = new User(); user.setStatus(UserStatus.ACTIVE);
        session = new UserSession(); session.setId(sid); session.setUserId(3); session.setExpiresAt(Instant.now().plusSeconds(3600));
        when(users.findById(3)).thenReturn(Optional.of(user));
        when(sessions.findById(sid)).thenReturn(Optional.of(session));
        when(roles.findNamesByUserId(3)).thenReturn(List.of("READER"));
        access = tokens.issue(3, sid);
    }

    @Test void usesFreshDatabaseRolesAndImmutablePrincipalWithoutCookiesOrSession() throws Exception {
        var first = get("/__security/principal", access);
        assertThat(first.statusCode()).isEqualTo(200);
        var principal = mapper.readTree(first.body());
        assertThat(principal.path("userId").asInt()).isEqualTo(3);
        assertThat(principal.path("sessionId").asText()).isEqualTo(sid.toString());
        assertThat(principal.path("roles").get(0).asText()).isEqualTo("READER");
        assertThat(first.headers().firstValue("Set-Cookie")).isEmpty();
        assertCode(get("/__security/admin", access), 403, "ACCESS_DENIED");
        when(roles.findNamesByUserId(3)).thenReturn(List.of("ADMIN", "READER"));
        assertThat(get("/__security/admin", access).statusCode()).isEqualTo(200);
        when(roles.findNamesByUserId(3)).thenReturn(List.of());
        assertThat(mapper.readTree(get("/__security/principal", access).body()).path("roles").isEmpty()).isTrue();
        assertCode(get("/__security/principal", null), 401, "AUTHENTICATION_REQUIRED");
        assertCode(get("/__security/principal?access_token=" + access, null), 401, "AUTHENTICATION_REQUIRED");
        var cookie = request("GET", "/__security/principal", null).header("Cookie", "accessToken=" + access).header("X-User-Id", "3");
        assertCode(send(cookie), 401, "AUTHENTICATION_REQUIRED");
    }

    @Test void rejectsMissingInvalidAndInactiveAuthenticationWithSafeEnvelopes() throws Exception {
        assertCode(get("/__security/principal", null), 401, "AUTHENTICATION_REQUIRED");
        assertCode(get("/__security/principal", "invalid"), 401, "ACCESS_TOKEN_INVALID");
        var duplicate = request("GET", "/__security/principal", access).header("Authorization", "Bearer " + access);
        assertCode(send(duplicate), 401, "ACCESS_TOKEN_INVALID");
        user.setStatus(UserStatus.BANNED);
        assertCode(get("/__security/principal", access), 401, "SESSION_INACTIVE");
        user.setStatus(UserStatus.ACTIVE); session.setRevokedAt(Instant.now());
        assertCode(get("/__security/principal", access), 401, "SESSION_INACTIVE");
        session.setRevokedAt(null); session.setExpiresAt(Instant.now().minusMillis(1));
        assertCode(get("/__security/principal", access), 401, "SESSION_INACTIVE");
        session.setExpiresAt(Instant.now().plusSeconds(30)); session.setUserId(4);
        assertCode(get("/__security/principal", access), 401, "SESSION_INACTIVE");
        when(sessions.findById(sid)).thenReturn(Optional.empty());
        assertCode(get("/__security/principal", access), 401, "SESSION_INACTIVE");
        when(users.findById(3)).thenReturn(Optional.empty());
        assertCode(get("/__security/principal", access), 401, "SESSION_INACTIVE");
    }

    @Test void infrastructureErrorsAreNotMisreportedAsInvalidTokens() throws Exception {
        when(users.findById(3)).thenThrow(new DataAccessResourceFailureException("private-fixture-detail"));
        var result = get("/__security/principal", access);
        assertCode(result, 503, "SERVICE_UNAVAILABLE");
        assertThat(result.body()).doesNotContain("private-fixture-detail");
        doThrow(new IllegalStateException("private-fixture-detail")).when(users).findById(3);
        assertCode(get("/__security/principal", access), 500, "INTERNAL_ERROR");
    }

    @Test void publicLoginIgnoresStaleBearerAndPreservesMvcValidationAndErrors() throws Exception {
        when(login.login(any())).thenReturn(new TokenPair("fixture-access", "fixture-refresh"));
        var result = send(request("POST", "/api/v1/auth/login", "expired-fixture").header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"identifier\":\" Reader \",\"password\":\"  mật khẩu  \"}")));
        assertThat(result.statusCode()).isEqualTo(200);
        assertThat(result.headers().firstValue("Cache-Control")).contains("no-store");
        assertThat(result.headers().firstValue("Set-Cookie")).isEmpty();
        verify(login).login(argThat(value -> value.identifier().equals("reader") && value.password().equals("  mật khẩu  ")));
        assertCode(get("/api/v1/auth/login", null), 405, "METHOD_NOT_ALLOWED");
        for (String body : List.of("", "{")) assertCode(post(body, "application/json"), 400, "INVALID_REQUEST");
        for (String body : List.of("{}", "{\"identifier\":5,\"password\":\"x\"}",
                "{\"identifier\":\"reader\",\"password\":\"x\",\"roles\":[]}"))
            assertCode(post(body, "application/json"), 400, "VALIDATION_ERROR");
        assertCode(post("fixture", "text/plain"), 415, "UNSUPPORTED_MEDIA_TYPE");
        assertCode(get("/api/v1/auth/refresh", null), 401, "AUTHENTICATION_REQUIRED");
        assertCode(get("/login", null), 401, "AUTHENTICATION_REQUIRED");
        assertCode(send(request("GET", "/__security/principal", null).header("Authorization", "Basic Zml4dHVyZQ==")), 401, "ACCESS_TOKEN_INVALID");
    }

    private HttpResponse<String> post(String body, String type) throws Exception {
        return send(request("POST", "/api/v1/auth/login", null).header("Content-Type", type).POST(HttpRequest.BodyPublishers.ofString(body)));
    }
    private HttpResponse<String> get(String path, String token) throws Exception { return send(request("GET", path, token)); }
    private HttpRequest.Builder request(String method, String path, String token) {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).header("Accept", "application/json")
                .method(method, HttpRequest.BodyPublishers.noBody());
        if (token != null) builder.header("Authorization", "Bearer " + token);
        return builder;
    }
    private HttpResponse<String> send(HttpRequest.Builder builder) throws Exception { return client.send(builder.build(), HttpResponse.BodyHandlers.ofString()); }
    private void assertCode(HttpResponse<String> result, int status, String code) throws Exception {
        assertThat(result.statusCode()).isEqualTo(status);
        assertThat(mapper.readTree(result.body()).path("messageCode").asText()).isEqualTo(code);
        assertThat(result.headers().firstValue("Set-Cookie")).isEmpty();
    }

    @RestController
    @TestComponent
    static class FixtureController {
        @GetMapping("/__security/principal") UserPrincipal principal(Authentication auth) { return (UserPrincipal) auth.getPrincipal(); }
        @PreAuthorize("hasRole('ADMIN')")
        @GetMapping("/__security/admin") Map<String, Boolean> admin() { return Map.of("allowed", true); }
    }
}
