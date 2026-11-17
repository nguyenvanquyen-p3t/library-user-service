package com.library.userservice.internal;

import com.library.userservice.common.api.ErrorCode;
import com.library.userservice.common.exception.ApiExceptionHandler;
import com.library.userservice.common.exception.ApplicationException;
import com.library.userservice.config.ApiSecurityConfiguration;
import com.library.userservice.config.JsonConfiguration;
import com.library.userservice.internal.dto.InternalUserSummary;
import com.library.userservice.internal.service.InternalUserService;
import com.library.userservice.internal.web.InternalUserController;
import com.library.userservice.security.SecurityErrorWriter;
import com.library.userservice.security.UserAccessTokenService;
import com.library.userservice.security.UserSessionAuthenticator;
import com.library.userservice.user.persistence.UserStatus;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
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

@SpringBootTest(classes = InternalUserHttpTests.Application.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.config.import=", "debug=false"})
class InternalUserHttpTests {
    @Configuration(proxyBeanMethods = false)
    @TestComponent
    @EnableAutoConfiguration(excludeName = {"org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
            "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration", "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration"})
    @Import({ApiSecurityConfiguration.class, JsonConfiguration.class, SecurityErrorWriter.class,
            ApiExceptionHandler.class, InternalUserController.class})
    static class Application {
        @Bean com.library.userservice.security.AdminActorAuthenticator adminActors() { return org.mockito.Mockito.mock(com.library.userservice.security.AdminActorAuthenticator.class); }
        @Bean InternalUserService users() { return mock(InternalUserService.class); }
        @Bean UserAccessTokenService tokens() { return mock(UserAccessTokenService.class); }
        @Bean UserSessionAuthenticator sessions() { return mock(UserSessionAuthenticator.class); }
    }

    @Autowired InternalUserService users;
    @Autowired UserAccessTokenService tokens;
    @Autowired UserSessionAuthenticator sessions;
    @Autowired JsonMapper mapper;
    @LocalServerPort int port;
    private final HttpClient client = HttpClient.newHttpClient();
    private static final String PATH = "/api/v1/internal/users/3";

    @BeforeEach void setup() { reset(users, tokens, sessions); }

    @Test void returnsExactSummaryForActiveAndBannedUsersWithoutAuthentication() throws Exception {
        for (var status : UserStatus.values()) {
            when(users.getUser(3)).thenReturn(new InternalUserSummary(3, "reader", "Nguyễn Văn A", status, List.of()));
            var response = send("GET", PATH);
            assertCode(response, 200, "USER_GET_SUCCESS");
            var body = mapper.readTree(response.body());
            assertThat(body.size()).isEqualTo(4);
            assertThat(body.path("success").asBoolean()).isTrue();
            var data = body.path("data");
            assertThat(data.size()).isEqualTo(5);
            assertThat(data.path("id").isInt()).isTrue();
            assertThat(data.path("id").asInt()).isEqualTo(3);
            assertThat(data.path("userName").asText()).isEqualTo("reader");
            assertThat(data.path("fullName").asText()).isEqualTo("Nguyễn Văn A");
            assertThat(data.path("status").asText()).isEqualTo(status.name());
            assertThat(data.path("roles").toString()).isEqualTo("[]");
        }
        verifyNoInteractions(tokens, sessions);
    }

    @Test void ignoresAuthorizationAndClientIdentityHeaders() throws Exception {
        when(users.getUser(3)).thenReturn(new InternalUserSummary(3, "reader", "Reader", UserStatus.ACTIVE, List.of("READER")));
        for (String authorization : List.of("Bearer invalid", "Basic ignored")) {
            var request = request("GET", PATH + "?userId=999&roles=ADMIN")
                    .header("Authorization", authorization).header("Authorization", "Bearer duplicate")
                    .header("X-User-Id", "999").header("X-Admin-Access-Token", "ignored")
                    .header("Cookie", "accessToken=ignored").GET().build();
            assertCode(client.send(request, HttpResponse.BodyHandlers.ofString()), 200, "USER_GET_SUCCESS");
        }
        verify(users, times(2)).getUser(3);
        verifyNoInteractions(tokens, sessions);
    }

    @Test void validatesPositiveInt32IdBeforeCallingService() throws Exception {
        for (String id : List.of("0", "-1", "-2147483648")) assertValidation(id, "OUT_OF_RANGE");
        for (String id : List.of("abc", "1.5", "2147483648", "-2147483649")) assertValidation(id, "TYPE_MISMATCH");
        verifyNoInteractions(users, tokens, sessions);
    }

    @Test void notFoundAndFailuresUseSafeEnvelopes() throws Exception {
        when(users.getUser(3)).thenThrow(new ApplicationException(ErrorCode.USER_NOT_FOUND));
        assertCode(send("GET", PATH), 404, "USER_NOT_FOUND");
        for (RuntimeException failure : List.of(new DataAccessResourceFailureException("private-fixture"),
                new CannotCreateTransactionException("private-fixture"), new IllegalStateException("private-fixture"))) {
            doThrow(failure).when(users).getUser(3);
            var response = send("GET", PATH);
            boolean unavailable = !(failure instanceof IllegalStateException);
            assertCode(response, unavailable ? 503 : 500, unavailable ? "SERVICE_UNAVAILABLE" : "INTERNAL_ERROR");
            assertThat(response.body()).doesNotContain("private-fixture", "Exception", "stackTrace");
        }
    }

    @Test void unsupportedWriteMethodsReachMvcWithoutAuthentication() throws Exception {
        for (String method : List.of("POST", "PUT", "PATCH", "DELETE")) {
            var response = send(method, PATH);
            assertCode(response, 405, "METHOD_NOT_ALLOWED");
            assertThat(response.headers().firstValue("Allow").orElse("")).contains("GET");
        }
        verifyNoInteractions(users, tokens, sessions);
    }

    @Test void neighboringPathsStillRequireUserAuthentication() throws Exception {
        for (String path : List.of(PATH + "/status", PATH + "/extra", "/api/v1/internal/users",
                "/api/v1/users/me", "/api/v1/internal/other/3")) {
            for (String method : List.of("GET", "PATCH")) {
                var response = send(method, path);
                assertThat(response.statusCode()).isEqualTo(401);
                assertThat(mapper.readTree(response.body()).path("messageCode").asText()).isEqualTo("AUTHENTICATION_REQUIRED");
            }
        }
        verifyNoInteractions(users);
    }

    private void assertValidation(String id, String code) throws Exception {
        var response = send("GET", "/api/v1/internal/users/" + id);
        assertCode(response, 400, "VALIDATION_ERROR");
        var error = mapper.readTree(response.body()).path("data").path("errors").get(0);
        assertThat(error.path("field").asText()).isEqualTo("id");
        assertThat(error.path("code").asText()).isEqualTo(code);
    }

    private HttpRequest.Builder request(String method, String path) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Accept", "application/json").method(method, HttpRequest.BodyPublishers.noBody());
    }

    private HttpResponse<String> send(String method, String path) throws Exception {
        return client.send(request(method, path).build(), HttpResponse.BodyHandlers.ofString());
    }

    private void assertCode(HttpResponse<String> response, int status, String code) throws Exception {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).startsWith("application/json");
        assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
        assertThat(response.headers().firstValue("Set-Cookie")).isEmpty();
        assertThat(response.headers().firstValue("WWW-Authenticate")).isEmpty();
        var body = mapper.readTree(response.body());
        assertThat(body.path("messageCode").asText()).isEqualTo(code);
        assertThat(body.path("success").asBoolean()).isEqualTo(status == 200);
        if (status >= 400 && !code.equals("VALIDATION_ERROR")) assertThat(body.path("data").isNull()).isTrue();
    }
}
