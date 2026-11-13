package com.library.userservice.user;

import com.library.userservice.common.api.ErrorCode;
import com.library.userservice.common.exception.ApiExceptionHandler;
import com.library.userservice.common.exception.ApplicationException;
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
import com.library.userservice.user.dto.ChangePasswordRequest;
import com.library.userservice.user.persistence.RoleRepository;
import com.library.userservice.user.persistence.User;
import com.library.userservice.user.persistence.UserRepository;
import com.library.userservice.user.persistence.UserStatus;
import com.library.userservice.user.service.ChangePasswordService;
import com.library.userservice.user.web.ChangePasswordController;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.HashMap;
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

@SpringBootTest(classes = ChangePasswordHttpTests.Application.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.config.import=", "debug=false"})
class ChangePasswordHttpTests extends UserSecurityTestKeys {
    @Configuration(proxyBeanMethods = false)
    @TestComponent
    @EnableAutoConfiguration(excludeName = {"org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
            "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration", "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration"})
    @Import({ApiSecurityConfiguration.class, UserSecurityConfiguration.class, JsonConfiguration.class,
            SecurityErrorWriter.class, UserSessionAuthenticator.class, ApiExceptionHandler.class, ChangePasswordController.class})
    static class Application {
        @Bean UserRepository users() { return mock(UserRepository.class); }
        @Bean UserSessionRepository sessions() { return mock(UserSessionRepository.class); }
        @Bean RoleRepository roles() { return mock(RoleRepository.class); }
        @Bean ChangePasswordService passwords() { return mock(ChangePasswordService.class); }
    }

    @Autowired UserRepository users;
    @Autowired UserSessionRepository sessions;
    @Autowired RoleRepository roles;
    @Autowired ChangePasswordService passwords;
    @Autowired UserAccessTokenService tokens;
    @Autowired JsonMapper mapper;
    @LocalServerPort int port;
    private final HttpClient client = HttpClient.newHttpClient();
    private final UUID sid = UUID.randomUUID();
    private User user;
    private UserSession session;
    private String access;

    @BeforeEach void setup() {
        reset(users, sessions, roles, passwords);
        user = new User(); user.setStatus(UserStatus.ACTIVE);
        session = new UserSession(); session.setUserId(3); session.setExpiresAt(Instant.now().plusSeconds(3600));
        when(users.findById(3)).thenReturn(Optional.of(user));
        when(sessions.findById(sid)).thenReturn(Optional.of(session));
        when(roles.findNamesByUserId(3)).thenReturn(List.of());
        access = tokens.issue(3, sid);
    }

    @Test void usesVerifiedPrincipalPreservesPasswordsAndReturnsNullDataWithoutTokens() throws Exception {
        var response = put("", "  mật khẩu 密  ");
        assertCode(response, 200, "PASSWORD_CHANGE_SUCCESS");
        var body = mapper.readTree(response.body()); assertThat(body.size()).isEqualTo(4);
        assertThat(body.path("success").asBoolean()).isTrue(); assertThat(body.path("data").isNull()).isTrue();
        verify(passwords).changePassword(new UserPrincipal(3, sid, List.of()),
                new ChangePasswordRequest("", "  mật khẩu 密  ", "  mật khẩu 密  "));
    }

    @Test void validatesRequiredTypesLengthConfirmationUnknownFieldsAndMalformedBodies() throws Exception {
        for (String field : List.of("currentPassword", "newPassword", "confirmPassword")) {
            var body = validBody(); body.remove(field); assertViolation(body, field, "REQUIRED");
            body = validBody(); body.put(field, null); assertViolation(body, field, "REQUIRED");
            body = validBody(); body.put(field, 123); assertViolation(body, field, "TYPE_MISMATCH");
        }
        for (int length : List.of(0, 5, 129)) {
            var body = validBody(); body.put("newPassword", "a".repeat(length)); body.put("confirmPassword", "a".repeat(length));
            assertViolation(body, "newPassword", "INVALID_LENGTH");
        }
        var body = validBody(); body.put("confirmPassword", "different-test-only");
        assertViolation(body, "confirmPassword", "PASSWORD_MISMATCH");
        for (String field : List.of("userId", "sessionId", "roles", "status", "email")) {
            body = validBody(); body.put(field, "private-uc07-fixture"); assertViolation(body, field, "UNKNOWN_FIELD");
        }
        for (String invalid : List.of("", "{", "null"))
            assertCode(send("PUT", invalid, "application/json", access), 400, "INVALID_REQUEST");
        verifyNoInteractions(passwords);
    }

    @Test void acceptsPolicyBoundariesIncludingWhitespaceOnlyNewPassword() throws Exception {
        for (String password : List.of("      ", "密".repeat(128))) assertCode(put("old-test-only", password), 200, "PASSWORD_CHANGE_SUCCESS");
        verify(passwords, times(2)).changePassword(any(), any());
    }

    @Test void requiresBearerAndAnActiveOwnedSessionBeforeBusinessValidation() throws Exception {
        assertCode(send("PUT", "{}", "application/json", null), 401, "AUTHENTICATION_REQUIRED");
        assertCode(send("PUT", "{}", "application/json", "invalid"), 401, "ACCESS_TOKEN_INVALID");
        user.setStatus(UserStatus.BANNED); assertInactive(); user.setStatus(UserStatus.ACTIVE);
        session.setRevokedAt(Instant.now()); assertInactive(); session.setRevokedAt(null);
        session.setUserId(4); assertInactive(); session.setUserId(3);
        session.setExpiresAt(Instant.now().minusSeconds(1)); assertInactive();
        verifyNoInteractions(passwords);
    }

    @Test void keepsBusinessMvcAndInfrastructureErrorContractsWithoutSensitiveDetails() throws Exception {
        assertCode(send("POST", "{}", "application/json", access), 405, "METHOD_NOT_ALLOWED");
        assertCode(send("PUT", "text", "text/plain", access), 415, "UNSUPPORTED_MEDIA_TYPE");
        for (ErrorCode code : List.of(ErrorCode.PASSWORD_NOT_CONFIGURED, ErrorCode.CURRENT_PASSWORD_INCORRECT, ErrorCode.SESSION_INACTIVE)) {
            doThrow(new ApplicationException(code)).when(passwords).changePassword(any(), any());
            assertCode(put("old-test-only", "new-test-only"), code.status(), code.name());
        }
        doThrow(new DataAccessResourceFailureException("private-uc07-db")).when(passwords).changePassword(any(), any());
        assertCode(put("old-test-only", "new-test-only"), 503, "SERVICE_UNAVAILABLE");
        doThrow(new IllegalStateException("private-uc07-error")).when(passwords).changePassword(any(), any());
        assertCode(put("old-test-only", "new-test-only"), 500, "INTERNAL_ERROR");
    }

    private Map<String, Object> validBody() {
        return new HashMap<>(Map.of("currentPassword", "old-test-only", "newPassword", "new-test-only", "confirmPassword", "new-test-only"));
    }
    private HttpResponse<String> put(String current, String next) throws Exception {
        return send("PUT", mapper.writeValueAsString(new ChangePasswordRequest(current, next, next)), "application/json", access);
    }
    private void assertInactive() throws Exception { assertCode(send("PUT", "{}", "application/json", access), 401, "SESSION_INACTIVE"); }
    private void assertViolation(Map<String, Object> body, String field, String code) throws Exception {
        var response = send("PUT", mapper.writeValueAsString(body), "application/json", access);
        assertCode(response, 400, "VALIDATION_ERROR");
        var errors = mapper.readTree(response.body()).path("data").path("errors");
        assertThat(errors.isEmpty()).isFalse(); boolean found = false;
        for (var error : errors) if (error.path("field").asText().equals(field) && error.path("code").asText().equals(code)) found = true;
        assertThat(found).isTrue();
    }
    private HttpResponse<String> send(String method, String body, String type, String bearer) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/users/me/password?userId=999"))
                .header("X-User-Id", "999");
        if (bearer != null) request.header("Authorization", "Bearer " + bearer);
        request.header("Content-Type", type);
        return client.send(request.method(method, HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    private void assertCode(HttpResponse<String> response, int status, String code) throws Exception {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(mapper.readTree(response.body()).path("messageCode").asText()).isEqualTo(code);
        assertThat(response.headers().firstValue("Set-Cookie")).isEmpty();
        assertThat(response.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
        assertThat(response.body()).doesNotContain("private-uc07", "old-test-only", "new-test-only", "PasswordHash", "TokenHash");
    }
}
