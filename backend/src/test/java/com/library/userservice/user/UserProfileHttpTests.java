package com.library.userservice.user;

import com.library.userservice.common.exception.ApiExceptionHandler;
import com.library.userservice.config.ApiSecurityConfiguration;
import com.library.userservice.config.JsonConfiguration;
import com.library.userservice.config.UserSecurityConfiguration;
import com.library.userservice.security.SecurityErrorWriter;
import com.library.userservice.security.UserAccessTokenService;
import com.library.userservice.security.UserPrincipal;
import com.library.userservice.security.UserSecurityTestKeys;
import com.library.userservice.security.UserSessionAuthenticator;
import com.library.userservice.session.persistence.UserSession;
import com.library.userservice.session.persistence.UserSessionRepository;
import com.library.userservice.user.dto.UserProfile;
import com.library.userservice.user.persistence.RoleRepository;
import com.library.userservice.user.persistence.User;
import com.library.userservice.user.persistence.UserRepository;
import com.library.userservice.user.persistence.UserStatus;
import com.library.userservice.user.service.UserProfileService;
import com.library.userservice.user.web.UserProfileController;
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
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = UserProfileHttpTests.Application.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.config.import=", "debug=false"})
class UserProfileHttpTests extends UserSecurityTestKeys {
    @Configuration(proxyBeanMethods = false)
    @TestComponent
    @EnableAutoConfiguration(excludeName = {"org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
            "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration", "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration"})
    @Import({ApiSecurityConfiguration.class, UserSecurityConfiguration.class, JsonConfiguration.class,
            SecurityErrorWriter.class, UserSessionAuthenticator.class, ApiExceptionHandler.class, UserProfileController.class})
    static class Application {
        @Bean UserRepository users() { return mock(UserRepository.class); }
        @Bean UserSessionRepository sessions() { return mock(UserSessionRepository.class); }
        @Bean RoleRepository roles() { return mock(RoleRepository.class); }
        @Bean UserProfileService profiles() { return mock(UserProfileService.class); }
    }

    @Autowired UserRepository users;
    @Autowired UserSessionRepository sessions;
    @Autowired RoleRepository roles;
    @Autowired UserProfileService profiles;
    @Autowired UserAccessTokenService tokens;
    @Autowired JsonMapper mapper;
    @LocalServerPort int port;
    private final HttpClient client = HttpClient.newHttpClient();
    private final UUID sid = UUID.randomUUID();
    private User user;
    private UserSession session;
    private String access;

    @BeforeEach void setup() {
        reset(users, sessions, roles, profiles);
        user = new User(); user.setStatus(UserStatus.ACTIVE);
        session = new UserSession(); session.setUserId(3); session.setExpiresAt(Instant.now().plusSeconds(3600));
        when(users.findById(3)).thenReturn(Optional.of(user));
        when(sessions.findById(sid)).thenReturn(Optional.of(session));
        when(roles.findNamesByUserId(3)).thenReturn(List.of());
        var result = new UserProfile(3, "reader", "reader@example.com", "Reader", null,
                UserStatus.ACTIVE, List.of(), false, List.of());
        when(profiles.getProfile(any())).thenReturn(result);
        when(profiles.updateProfile(any(), any())).thenReturn(result);
        access = tokens.issue(3, sid);
    }

    @Test void bothOperationsUseVerifiedPrincipalAndReturnExactEnvelope() throws Exception {
        for (String method : List.of("GET", "PATCH")) {
            var response = send(method, method.equals("GET") ? null : "{\"phoneNumber\":null}", "application/json", access);
            assertCode(response, 200, method.equals("GET") ? "PROFILE_GET_SUCCESS" : "PROFILE_UPDATE_SUCCESS");
            var body = mapper.readTree(response.body());
            assertThat(body.size()).isEqualTo(4); assertThat(body.path("success").asBoolean()).isTrue();
            assertThat(body.path("data").size()).isEqualTo(9);
            assertThat(body.path("data").path("phoneNumber").isNull()).isTrue();
            assertThat(body.path("data").path("roles").isEmpty()).isTrue();
            assertThat(body.path("data").path("linkedProviders").isEmpty()).isTrue();
        }
        verify(profiles).getProfile(new UserPrincipal(3, sid, List.of()));
        verify(profiles).updateProfile(eq(new UserPrincipal(3, sid, List.of())),
                argThat(request -> request.hasPhoneNumber() && request.getPhoneNumber() == null && !request.hasFullName()));
    }

    @Test void validatesPatchPresenceNullTypesFormatsAndForbiddenFieldsBeforeService() throws Exception {
        var invalid = Map.ofEntries(
                Map.entry("{}", "EMPTY_PATCH"),
                Map.entry("{\"fullName\":null}", "REQUIRED"),
                Map.entry("{\"fullName\":\"   \"}", "NOT_BLANK"),
                Map.entry("{\"fullName\":\"" + "a".repeat(151) + "\"}", "INVALID_LENGTH"),
                Map.entry("{\"fullName\":5}", "TYPE_MISMATCH"),
                Map.entry("{\"phoneNumber\":false}", "TYPE_MISMATCH"),
                Map.entry("{\"phoneNumber\":\"\"}", "INVALID_FORMAT"),
                Map.entry("{\"phoneNumber\":\"+12 345678\"}", "INVALID_FORMAT"),
                Map.entry("{\"phoneNumber\":\"１２３４５６７８\"}", "INVALID_FORMAT"));
        for (var entry : invalid.entrySet()) assertViolation(entry.getKey(), entry.getValue());
        for (String field : List.of("roles", "status", "password", "userId", "email", "userName"))
            assertViolation("{\"fullName\":\"Name\",\"" + field + "\":\"private-fixture\"}", "UNKNOWN_FIELD");
        for (String body : List.of("", "{", "null")) assertCode(send("PATCH", body, "application/json", access), 400, "INVALID_REQUEST");
        verifyNoInteractions(profiles);
    }

    @Test void normalizesOnlyNameAndPreservesPresenceAndPhone() throws Exception {
        assertCode(send("PATCH", "{\"fullName\":\"  Nguyễn Văn A  \"}", "application/json", access), 200, "PROFILE_UPDATE_SUCCESS");
        verify(profiles).updateProfile(any(), argThat(request -> request.getFullName().equals("Nguyễn Văn A")
                && request.hasFullName() && !request.hasPhoneNumber()));
        assertCode(send("PATCH", "{\"phoneNumber\":\"+12345678\"}", "application/json", access), 200, "PROFILE_UPDATE_SUCCESS");
        verify(profiles).updateProfile(any(), argThat(request -> !request.hasFullName()
                && request.getPhoneNumber().equals("+12345678")));
    }

    @Test void refusesMissingInvalidAndInactiveAuthenticationOnBothOperations() throws Exception {
        for (String method : List.of("GET", "PATCH")) {
            assertCode(send(method, "{}", "application/json", null), 401, "AUTHENTICATION_REQUIRED");
            assertCode(send(method, "{}", "application/json", "invalid"), 401, "ACCESS_TOKEN_INVALID");
            user.setStatus(UserStatus.BANNED);
            assertCode(send(method, "{}", "application/json", access), 401, "SESSION_INACTIVE");
            user.setStatus(UserStatus.ACTIVE); session.setRevokedAt(Instant.now());
            assertCode(send(method, "{}", "application/json", access), 401, "SESSION_INACTIVE");
            session.setRevokedAt(null); session.setUserId(4);
            assertCode(send(method, "{}", "application/json", access), 401, "SESSION_INACTIVE");
            session.setUserId(3); session.setExpiresAt(Instant.now().minusSeconds(1));
            assertCode(send(method, "{}", "application/json", access), 401, "SESSION_INACTIVE");
            session.setExpiresAt(Instant.now().plusSeconds(3600));
        }
        verifyNoInteractions(profiles);
    }

    @Test void preservesMvcAndInfrastructureErrorsWithoutPrivateDetails() throws Exception {
        assertCode(send("POST", "{}", "application/json", access), 405, "METHOD_NOT_ALLOWED");
        assertCode(send("PATCH", "text", "text/plain", access), 415, "UNSUPPORTED_MEDIA_TYPE");
        when(profiles.getProfile(any())).thenThrow(new DataAccessResourceFailureException("private-fixture"));
        assertCode(send("GET", null, null, access), 503, "SERVICE_UNAVAILABLE");
        when(profiles.updateProfile(any(), any())).thenThrow(new IllegalStateException("private-fixture"));
        assertCode(send("PATCH", "{\"phoneNumber\":null}", "application/json", access), 500, "INTERNAL_ERROR");
    }

    private void assertViolation(String body, String code) throws Exception {
        var response = send("PATCH", body, "application/json", access); assertCode(response, 400, "VALIDATION_ERROR");
        var errors = mapper.readTree(response.body()).path("data").path("errors");
        assertThat(errors.isEmpty()).isFalse();
        assertThat(errors.get(0).path("code").asText()).isEqualTo(code);
        assertThat(errors.get(0).size()).isEqualTo(3);
    }

    private HttpResponse<String> send(String method, String body, String type, String bearer) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/users/me?userId=999"))
                .header("X-User-Id", "999");
        if (bearer != null) request.header("Authorization", "Bearer " + bearer);
        if (type != null) request.header("Content-Type", type);
        return client.send(request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private void assertCode(HttpResponse<String> response, int status, String code) throws Exception {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(mapper.readTree(response.body()).path("messageCode").asText()).isEqualTo(code);
        assertThat(response.headers().firstValue("Set-Cookie")).isEmpty();
        assertThat(response.body()).doesNotContain("private-fixture");
    }
}
