package com.library.userservice.internal;

import com.library.userservice.common.api.ErrorCode;
import com.library.userservice.common.exception.*;
import com.library.userservice.config.*;
import com.library.userservice.internal.dto.*;
import com.library.userservice.internal.service.InternalUserStatusService;
import com.library.userservice.internal.web.InternalUserStatusController;
import com.library.userservice.security.*;
import com.library.userservice.session.persistence.*;
import com.library.userservice.user.persistence.*;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.dao.DataAccessResourceFailureException;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = InternalUserStatusHttpTests.Application.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.config.import=", "debug=false"})
class InternalUserStatusHttpTests extends UserSecurityTestKeys {
    @Configuration(proxyBeanMethods = false)
    @TestComponent
    @EnableAutoConfiguration(excludeName = {"org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
            "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration", "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration"})
    @Import({ApiSecurityConfiguration.class, UserSecurityConfiguration.class, JsonConfiguration.class, SecurityErrorWriter.class,
            ApiExceptionHandler.class, AdminActorAuthenticator.class, UserSessionAuthenticator.class, InternalUserStatusController.class})
    static class Application {
        @Bean UserRepository users() { return mock(UserRepository.class); }
        @Bean UserSessionRepository sessions() { return mock(UserSessionRepository.class); }
        @Bean RoleRepository roles() { return mock(RoleRepository.class); }
        @Bean InternalUserStatusService status() { return mock(InternalUserStatusService.class); }
    }
    @Autowired UserRepository users;
    @Autowired UserSessionRepository sessions;
    @Autowired RoleRepository roles;
    @Autowired InternalUserStatusService status;
    @Autowired UserAccessTokenService jwt;
    @Autowired JsonMapper mapper;
    @LocalServerPort int port;
    final HttpClient client = HttpClient.newHttpClient();
    final UUID sid = UUID.randomUUID();
    static final String PATH = "/api/v1/internal/users/3/status";
    String access;
    User actor;
    UserSession session;

    @BeforeEach void setup() {
        reset(users, sessions, roles, status);
        actor = new User(); actor.setStatus(UserStatus.ACTIVE);
        session = new UserSession(); session.setUserId(9); session.setExpiresAt(Instant.now().plusSeconds(3600));
        when(users.findById(9)).thenReturn(Optional.of(actor));
        when(sessions.findById(sid)).thenReturn(Optional.of(session));
        when(roles.findNamesByUserId(9)).thenReturn(List.of("ADMIN"));
        access = jwt.issue(9, sid);
        when(status.updateStatus(any(), eq(3), any())).thenReturn(new UserStatusResult(3, UserStatus.BANNED));
    }

    @Test void rawActorJwtWorksWithoutServiceJwtAndAuthorizationIsIgnored() throws Exception {
        var request = builder("PATCH", PATH, "{\"status\":\"BANNED\"}", access)
                .header("Authorization", "Basic ignored").header("Authorization", "Bearer duplicate")
                .header("X-User-Id", "999").build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        code(response, 200, "USER_STATUS_UPDATE_SUCCESS");
        assertThat(mapper.readTree(response.body()).path("data").size()).isEqualTo(2);
        verify(status).updateStatus(argThat(a -> a.userId() == 9 && a.sessionId().equals(sid)), eq(3), eq(new UserStatusRequest(UserStatus.BANNED)));
    }

    @Test void missingMalformedAndRepeatedActorHeadersAreRejectedBeforeBusinessValidation() throws Exception {
        code(send("PATCH", PATH, "broken", null), 401, "AUTHENTICATION_REQUIRED");
        for (String token : List.of("", "invalid", "Bearer " + access, access + "x", "refresh-fixture"))
            code(send("PATCH", PATH, "{}", token), 401, "ADMIN_ACTOR_INVALID");
        var repeated = builder("PATCH", PATH, "{}", access).header("X-Admin-Access-Token", access).build();
        code(client.send(repeated, HttpResponse.BodyHandlers.ofString()), 401, "ADMIN_ACTOR_INVALID");
        var cookie = builder("PATCH", PATH + "?accessToken=" + access, "{}", null).header("Cookie", "accessToken=" + access).build();
        code(client.send(cookie, HttpResponse.BodyHandlers.ofString()), 401, "AUTHENTICATION_REQUIRED");
        verifyNoInteractions(status);
    }

    @Test void actorSessionAndCurrentRolesAreCheckedWithSeparateErrors() throws Exception {
        when(roles.findNamesByUserId(9)).thenReturn(List.of("READER"));
        code(send("PATCH", PATH, "{}", access), 403, "ACCESS_DENIED");
        when(roles.findNamesByUserId(9)).thenReturn(List.of("ADMIN")); actor.setStatus(UserStatus.BANNED);
        code(send("PATCH", PATH, "{}", access), 403, "ACCESS_DENIED"); actor.setStatus(UserStatus.ACTIVE);
        session.setExpiresAt(Instant.now().minusSeconds(1));
        code(send("PATCH", PATH, "{}", access), 401, "ADMIN_ACTOR_INVALID"); session.setExpiresAt(Instant.now().plusSeconds(60));
        session.setRevokedAt(Instant.now()); code(send("PATCH", PATH, "{}", access), 401, "ADMIN_ACTOR_INVALID");
        session.setRevokedAt(null); session.setUserId(8); code(send("PATCH", PATH, "{}", access), 401, "ADMIN_ACTOR_INVALID");
        when(users.findById(9)).thenReturn(Optional.empty()); code(send("PATCH", PATH, "{}", access), 401, "ADMIN_ACTOR_INVALID");
        verifyNoInteractions(status);
    }

    @Test void validatesBodyIdAndProtocolAndKeepsNeighboringPathsProtected() throws Exception {
        for (String body : List.of("{}", "{\"status\":null}", "{\"status\":\"PENDING\"}", "{\"status\":1}",
                "{\"status\":\"ACTIVE\",\"roles\":[\"ADMIN\"]}")) code(send("PATCH", PATH, body, access), 400, "VALIDATION_ERROR");
        code(send("PATCH", PATH, "{", access), 400, "INVALID_REQUEST");
        code(send("PATCH", PATH, "", access), 400, "INVALID_REQUEST");
        for (String id : List.of("0", "-1", "abc", "2147483648"))
            code(send("PATCH", "/api/v1/internal/users/" + id + "/status", "{\"status\":\"ACTIVE\"}", access), 400, "VALIDATION_ERROR");
        code(send("POST", PATH, "{}", access), 405, "METHOD_NOT_ALLOWED");
        var text = HttpRequest.newBuilder(URI.create("http://localhost:" + port + PATH)).header("X-Admin-Access-Token", access)
                .header("Content-Type", "text/plain").method("PATCH", HttpRequest.BodyPublishers.ofString("{}")).build();
        code(client.send(text, HttpResponse.BodyHandlers.ofString()), 415, "UNSUPPORTED_MEDIA_TYPE");
        code(send("PATCH", PATH + "/extra", "{}", access), 401, "AUTHENTICATION_REQUIRED");
        verifyNoInteractions(status);
    }

    @Test void databaseFailuresAreNotActorErrorsAndServiceErrorsKeepSafeEnvelopes() throws Exception {
        when(users.findById(9)).thenThrow(new DataAccessResourceFailureException("private-fixture"));
        code(send("PATCH", PATH, "{}", access), 503, "SERVICE_UNAVAILABLE");
        doReturn(Optional.of(actor)).when(users).findById(9);
        for (var error : List.of(ErrorCode.USER_NOT_FOUND, ErrorCode.ACCESS_DENIED, ErrorCode.ADMIN_ACTOR_INVALID,
                ErrorCode.SERVICE_UNAVAILABLE, ErrorCode.INTERNAL_ERROR)) {
            doThrow(new ApplicationException(error)).when(status).updateStatus(any(), anyInt(), any());
            code(send("PATCH", PATH, "{\"status\":\"ACTIVE\"}", access), error.status(), error.name());
        }
    }

    HttpRequest.Builder builder(String method, String path, String body, String token) {
        var b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body));
        if (token != null) b.header("X-Admin-Access-Token", token);
        return b;
    }
    HttpResponse<String> send(String method, String path, String body, String token) throws Exception {
        return client.send(builder(method, path, body, token).build(), HttpResponse.BodyHandlers.ofString());
    }
    void code(HttpResponse<String> response, int expected, String code) throws Exception {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(expected);
        assertThat(mapper.readTree(response.body()).path("messageCode").asText()).isEqualTo(code);
        assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
        assertThat(response.headers().firstValue("Set-Cookie")).isEmpty();
        assertThat(response.body()).doesNotContain(access, "private-fixture", "PasswordHash", "TokenHash");
    }
}
