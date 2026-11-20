package com.library.userservice.auth;

import static com.library.userservice.auth.web.RegistrationController.REGISTER_PATH;
import static com.library.userservice.auth.web.RegistrationController.REQUEST_CODE_PATH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.library.userservice.auth.dto.OtpRequestResult;
import com.library.userservice.auth.dto.RegisterCodeRequest;
import com.library.userservice.auth.service.RegistrationService;
import com.library.userservice.auth.web.RegistrationController;
import com.library.userservice.common.api.ErrorCode;
import com.library.userservice.common.exception.ApiExceptionHandler;
import com.library.userservice.common.exception.ApplicationException;
import com.library.userservice.config.ApiSecurityConfiguration;
import com.library.userservice.config.JsonConfiguration;
import com.library.userservice.security.AdminActorAuthenticator;
import com.library.userservice.security.SecurityErrorWriter;
import com.library.userservice.security.UserAccessTokenService;
import com.library.userservice.security.UserSessionAuthenticator;
import com.library.userservice.user.dto.UserProfile;
import com.library.userservice.user.persistence.UserStatus;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(classes = RegistrationHttpTests.Application.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.config.import=", "debug=false"})
class RegistrationHttpTests {
    private static final String REQUEST_CODE = """
            {"userName":" Reader01 ","email":" READER@EXAMPLE.COM "}
            """;
    private static final String REGISTER = """
            {"userName":" Reader02 ","email":" READER@EXAMPLE.COM ","fullName":" Reader Two ",
             "password":"  mật khẩu  ","verificationCode":"012345","phoneNumber":null}
            """;

    @Configuration(proxyBeanMethods = false)
    @TestComponent
    @EnableAutoConfiguration(excludeName = {"org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
            "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
            "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration"})
    @Import({ApiSecurityConfiguration.class, JsonConfiguration.class, SecurityErrorWriter.class,
            ApiExceptionHandler.class, RegistrationController.class})
    static class Application {
        @Bean UserAccessTokenService tokens() { return mock(UserAccessTokenService.class); }
        @Bean UserSessionAuthenticator sessions() { return mock(UserSessionAuthenticator.class); }
        @Bean AdminActorAuthenticator actors() { return mock(AdminActorAuthenticator.class); }
    }

    @MockitoBean RegistrationService registration;
    @Autowired JsonMapper mapper;
    @LocalServerPort int port;
    private final HttpClient client = HttpClient.newHttpClient();

    @Test void bothPathsArePublicAndReturnConfirmedSchemas() throws Exception {
        when(registration.requestCode(any())).thenReturn(new OtpRequestResult());
        when(registration.register(any())).thenReturn(new UserProfile(1, "reader02", "reader@example.com", "Reader Two",
                null, UserStatus.ACTIVE, List.of("READER"), true, List.of()));
        var sent = send("POST", REQUEST_CODE_PATH, "application/json", REQUEST_CODE, null);
        assertCode(sent, 200, "REGISTER_CODE_SENT");
        assertThat(mapper.readTree(sent.body()).path("data").path("expiresIn").asInt()).isEqualTo(600);
        verify(registration).requestCode(new RegisterCodeRequest("reader01", "reader@example.com"));
        var created = send("POST", REGISTER_PATH, "application/json", REGISTER, "invalid-bearer");
        assertCode(created, 201, "REGISTER_SUCCESS");
        assertThat(mapper.readTree(created.body()).path("data").size()).isEqualTo(9);
        assertThat(created.headers().firstValue("Set-Cookie")).isEmpty();
        verify(registration).register(argThat(request -> request.password().equals("  mật khẩu  ")
                && request.verificationCode().equals("012345") && request.userName().equals("reader02")));
    }

    @Test void invalidRequestsUseExistingValidationAndOnlyExactPathsArePublic() throws Exception {
        assertCode(send("POST", REQUEST_CODE_PATH, "application/json", "{}", null), 400, "VALIDATION_ERROR");
        assertCode(send("POST", REGISTER_PATH, "application/json", REGISTER.replace("\"012345\"", "123456"), null),
                400, "VALIDATION_ERROR");
        assertCode(send("POST", REGISTER_PATH, "application/json", "{", null), 400, "INVALID_REQUEST");
        assertCode(send("POST", REQUEST_CODE_PATH, "text/plain", REQUEST_CODE, null), 415, "UNSUPPORTED_MEDIA_TYPE");
        assertCode(send("GET", REGISTER_PATH, "application/json", "", null), 405, "METHOD_NOT_ALLOWED");
        assertCode(send("POST", REGISTER_PATH + "/different", "application/json", REGISTER, null),
                401, "AUTHENTICATION_REQUIRED");
        verifyNoInteractions(registration);
    }

    @Test void mapsRateLimitAndSmtpFailureToContractErrors() throws Exception {
        when(registration.requestCode(any())).thenThrow(new ApplicationException(ErrorCode.OTP_RATE_LIMITED, 61L),
                new ApplicationException(ErrorCode.SERVICE_UNAVAILABLE));
        var limited = send("POST", REQUEST_CODE_PATH, "application/json", REQUEST_CODE, null);
        assertCode(limited, 429, "OTP_RATE_LIMITED");
        assertThat(limited.headers().firstValue("Retry-After")).contains("61");
        assertCode(send("POST", REQUEST_CODE_PATH, "application/json", REQUEST_CODE, null), 503, "SERVICE_UNAVAILABLE");
    }

    private HttpResponse<String> send(String method, String path, String contentType, String body, String access) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Content-Type", contentType).method(method, HttpRequest.BodyPublishers.ofString(body));
        if (access != null) request.header("Authorization", "Bearer " + access);
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private void assertCode(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(mapper.readTree(response.body()).path("messageCode").asText()).isEqualTo(code);
    }
}
