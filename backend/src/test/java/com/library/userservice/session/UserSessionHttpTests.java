package com.library.userservice.session;

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
import com.library.userservice.session.dto.RevokeOthersResult;
import com.library.userservice.session.dto.SessionItem;
import com.library.userservice.session.dto.SessionPage;
import com.library.userservice.session.dto.SessionQuery;
import com.library.userservice.session.persistence.UserSession;
import com.library.userservice.session.persistence.UserSessionRepository;
import com.library.userservice.session.service.UserSessionService;
import com.library.userservice.session.web.UserSessionController;
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
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = UserSessionHttpTests.Application.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.config.import=", "debug=false"})
class UserSessionHttpTests extends UserSecurityTestKeys {
    @Configuration(proxyBeanMethods = false)
    @TestComponent
    @EnableAutoConfiguration(excludeName = {"org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
            "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration", "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration"})
    @Import({ApiSecurityConfiguration.class, UserSecurityConfiguration.class, JsonConfiguration.class,
            SecurityErrorWriter.class, UserSessionAuthenticator.class, ApiExceptionHandler.class, UserSessionController.class})
    static class Application {
        @Bean com.library.userservice.security.AdminActorAuthenticator adminActors() { return org.mockito.Mockito.mock(com.library.userservice.security.AdminActorAuthenticator.class); }
        @Bean UserRepository users() { return mock(UserRepository.class); }
        @Bean UserSessionRepository sessions() { return mock(UserSessionRepository.class); }
        @Bean RoleRepository roles() { return mock(RoleRepository.class); }
        @Bean UserSessionService management() { return mock(UserSessionService.class); }
    }

    @Autowired UserRepository users;
    @Autowired UserSessionRepository sessions;
    @Autowired RoleRepository roles;
    @Autowired UserSessionService management;
    @Autowired UserAccessTokenService jwt;
    @Autowired JsonMapper mapper;
    @LocalServerPort int port;
    private final HttpClient client = HttpClient.newHttpClient();
    private final UUID sid = UUID.randomUUID(), target = UUID.randomUUID();
    private static final String BASE = "/api/v1/users/me/sessions";
    private User user;
    private UserSession session;
    private String access;

    @BeforeEach void setup() {
        reset(users, sessions, roles, management);
        user = new User(); user.setStatus(UserStatus.ACTIVE);
        session = new UserSession(); session.setUserId(3); session.setExpiresAt(Instant.now().plusSeconds(3600));
        when(users.findById(3)).thenReturn(Optional.of(user)); when(sessions.findById(sid)).thenReturn(Optional.of(session));
        when(roles.findNamesByUserId(3)).thenReturn(List.of());
        when(management.list(any(), any())).thenReturn(new SessionPage(List.of(new SessionItem(sid, null, null,
                Instant.now(), Instant.now(), session.getExpiresAt(), true)), 0, 20, 1));
        when(management.revokeOthers(any())).thenReturn(new RevokeOthersResult(2));
        access = jwt.issue(3, sid);
    }

    @Test void allOperationsUseVerifiedPrincipalAndExactResponseSchemas() throws Exception {
        var response = send("GET", BASE + "?userId=999", access);
        assertCode(response, 200, "SESSION_LIST_SUCCESS");
        var body = mapper.readTree(response.body()); assertThat(body.size()).isEqualTo(4);
        var data = body.path("data"); assertThat(data.size()).isEqualTo(4);
        var item = data.path("items").get(0); assertThat(item.size()).isEqualTo(7);
        assertThat(item.path("deviceName").isNull()).isTrue(); assertThat(item.path("clientType").isNull()).isTrue();
        assertThat(item.path("isCurrent").asBoolean()).isTrue();
        response = send("DELETE", BASE + "/" + target, access); assertCode(response, 200, "SESSION_REVOKE_SUCCESS");
        assertThat(mapper.readTree(response.body()).path("data").isNull()).isTrue();
        response = send("POST", BASE + "/revoke-others?currentSessionId=" + target, access);
        assertCode(response, 200, "OTHER_SESSIONS_REVOKED");
        assertThat(mapper.readTree(response.body()).path("data").size()).isEqualTo(1);
        assertThat(mapper.readTree(response.body()).path("data").path("revokedCount").asLong()).isEqualTo(2);
        var principal = new UserPrincipal(3, sid, List.of());
        verify(management).list(principal, new SessionQuery()); verify(management).revoke(principal, target);
        verify(management).revokeOthers(principal);
        assertThat(response.headers().firstValue("Set-Cookie")).isEmpty();
    }

    @Test void validatesPagingLimitsTypesOverflowAndUuidBeforeCallingService() throws Exception {
        for (var entry : Map.of("?page=-1", "OUT_OF_RANGE", "?size=0", "OUT_OF_RANGE", "?size=101", "OUT_OF_RANGE",
                "?page=no", "TYPE_MISMATCH", "?page=2147483648", "TYPE_MISMATCH", "?size=1.5", "TYPE_MISMATCH").entrySet()) {
            var response = send("GET", BASE + entry.getKey(), access); assertCode(response, 400, "VALIDATION_ERROR");
            assertThat(mapper.readTree(response.body()).path("data").path("errors").get(0).path("code").asText()).isEqualTo(entry.getValue());
        }
        for (String id : List.of("not-a-uuid", "1-1-1-1-1")) {
            var response = send("DELETE", BASE + "/" + id, access); assertCode(response, 400, "VALIDATION_ERROR");
            var violation = mapper.readTree(response.body()).path("data").path("errors").get(0);
            assertThat(violation.path("field").asText()).isEqualTo("sessionId");
            assertThat(violation.path("code").asText()).isEqualTo("INVALID_FORMAT");
        }
        verifyNoInteractions(management);
    }

    @Test void bindsCustomAndMaximumPagingAndUppercaseUuid() throws Exception {
        assertCode(send("GET", BASE + "?page=2147483647&size=100", access), 200, "SESSION_LIST_SUCCESS");
        verify(management).list(any(), eq(new SessionQuery(Integer.MAX_VALUE, 100)));
        assertCode(send("DELETE", BASE + "/" + target.toString().toUpperCase(java.util.Locale.ROOT), access), 200, "SESSION_REVOKE_SUCCESS");
        verify(management).revoke(any(), eq(target));
    }

    @Test void protectsEveryOperationWithBearerAndCurrentSession() throws Exception {
        for (String method : List.of("GET", "DELETE", "POST")) {
            String path = method.equals("GET") ? BASE : BASE + (method.equals("DELETE") ? "/" + target : "/revoke-others");
            assertCode(send(method, path, null), 401, "AUTHENTICATION_REQUIRED");
            assertCode(send(method, path, "invalid"), 401, "ACCESS_TOKEN_INVALID");
            user.setStatus(UserStatus.BANNED); assertCode(send(method, path, access), 401, "SESSION_INACTIVE"); user.setStatus(UserStatus.ACTIVE);
            session.setRevokedAt(Instant.now()); assertCode(send(method, path, access), 401, "SESSION_INACTIVE"); session.setRevokedAt(null);
            session.setUserId(4); assertCode(send(method, path, access), 401, "SESSION_INACTIVE"); session.setUserId(3);
            session.setExpiresAt(Instant.now().minusSeconds(1)); assertCode(send(method, path, access), 401, "SESSION_INACTIVE");
            session.setExpiresAt(Instant.now().plusSeconds(3600));
        }
        verifyNoInteractions(management);
    }

    @Test void returnsBusinessMethodAndInfrastructureErrorsWithoutPrivateDetails() throws Exception {
        for (var code : List.of(ErrorCode.SESSION_NOT_FOUND, ErrorCode.CURRENT_SESSION_REVOKE_NOT_ALLOWED, ErrorCode.SESSION_INACTIVE)) {
            doThrow(new ApplicationException(code)).when(management).revoke(any(), any());
            assertCode(send("DELETE", BASE + "/" + target, access), code.status(), code.name());
        }
        assertCode(send("PUT", BASE, access), 405, "METHOD_NOT_ALLOWED");
        when(management.list(any(), any())).thenThrow(new DataAccessResourceFailureException("private-sessions-db"));
        assertCode(send("GET", BASE, access), 503, "SERVICE_UNAVAILABLE");
        when(management.revokeOthers(any())).thenThrow(new IllegalStateException("private-sessions-error"));
        assertCode(send("POST", BASE + "/revoke-others", access), 500, "INTERNAL_ERROR");
    }

    private HttpResponse<String> send(String method, String path, String bearer) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).header("X-User-Id", "999");
        if (bearer != null) request.header("Authorization", "Bearer " + bearer);
        return client.send(request.method(method, HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
    }
    private void assertCode(HttpResponse<String> response, int status, String code) throws Exception {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(mapper.readTree(response.body()).path("messageCode").asText()).isEqualTo(code);
        assertThat(response.body()).doesNotContain("private-sessions", "PasswordHash", "TokenHash", "UserAgent");
    }
}
