package com.library.userservice.auth;

import static com.library.userservice.auth.web.PasswordResetController.REQUEST_CODE_PATH;
import static com.library.userservice.auth.web.PasswordResetController.RESET_PATH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.library.userservice.auth.dto.ForgotPasswordCodeRequest;
import com.library.userservice.auth.dto.OtpRequestResult;
import com.library.userservice.auth.dto.ResetPasswordRequest;
import com.library.userservice.auth.service.PasswordResetService;
import com.library.userservice.auth.web.PasswordResetController;
import com.library.userservice.common.api.ErrorCode;
import com.library.userservice.common.exception.ApiExceptionHandler;
import com.library.userservice.common.exception.ApplicationException;
import com.library.userservice.config.ApiSecurityConfiguration;
import com.library.userservice.config.JsonConfiguration;
import com.library.userservice.security.AdminActorAuthenticator;
import com.library.userservice.security.SecurityErrorWriter;
import com.library.userservice.security.UserAccessTokenService;
import com.library.userservice.security.UserSessionAuthenticator;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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

@SpringBootTest(classes = PasswordResetHttpTests.Application.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.config.import=", "debug=false"})
class PasswordResetHttpTests {
    private static final String EMAIL_JSON = """
            {"email":" READER@EXAMPLE.COM "}
            """;
    private static final String RESET_JSON = """
            {"email":" READER@EXAMPLE.COM ","verificationCode":"012345",
             "newPassword":"  mật khẩu mới  ","confirmPassword":"  mật khẩu mới  "}
            """;

    @Configuration(proxyBeanMethods = false)
    @TestComponent
    @EnableAutoConfiguration(excludeName = {"org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
            "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
            "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration"})
    @Import({ApiSecurityConfiguration.class, JsonConfiguration.class, SecurityErrorWriter.class,
            ApiExceptionHandler.class, PasswordResetController.class})
    static class Application {
        @Bean UserAccessTokenService tokens() {
            var tokens = mock(UserAccessTokenService.class);
            when(tokens.verify(any())).thenThrow(new ApplicationException(ErrorCode.ACCESS_TOKEN_INVALID));
            return tokens;
        }
        @Bean UserSessionAuthenticator sessions() { return mock(UserSessionAuthenticator.class); }
        @Bean AdminActorAuthenticator actors() { return mock(AdminActorAuthenticator.class); }
    }

    @MockitoBean PasswordResetService passwordReset;
    @Autowired JsonMapper mapper;
    @LocalServerPort int port;
    private final HttpClient client = HttpClient.newHttpClient();

    @Test void bothPathsArePublicAndReturnTheConfirmedSchemas() throws Exception {
        when(passwordReset.requestCode(any())).thenReturn(new OtpRequestResult());
        var issued = send("POST", REQUEST_CODE_PATH, "application/json", EMAIL_JSON);
        assertCode(issued, 200, "PASSWORD_RESET_CODE_SENT");
        assertThat(mapper.readTree(issued.body()).path("data").path("expiresIn").asInt()).isEqualTo(600);
        verify(passwordReset).requestCode(new ForgotPasswordCodeRequest("reader@example.com"));

        var reset = send("POST", RESET_PATH, "application/json", RESET_JSON);
        assertCode(reset, 200, "PASSWORD_RESET_SUCCESS");
        assertThat(mapper.readTree(reset.body()).path("data").isNull()).isTrue();
        verify(passwordReset).reset(new ResetPasswordRequest("reader@example.com", "012345",
                "  mật khẩu mới  ", "  mật khẩu mới  "));
        for (var response : new HttpResponse<?>[]{issued, reset}) {
            assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
            assertThat(response.headers().firstValue("Set-Cookie")).isEmpty();
        }
    }

    @Test void invalidRequestsKeepValidationAndExactSecurityMatchers() throws Exception {
        assertCode(send("POST", REQUEST_CODE_PATH, "application/json", "{}"), 400, "VALIDATION_ERROR");
        assertCode(send("POST", RESET_PATH, "application/json", RESET_JSON.replace("\"012345\"", "123456")),
                400, "VALIDATION_ERROR");
        var mismatch = send("POST", RESET_PATH, "application/json",
                RESET_JSON.replace("\"confirmPassword\":\"  mật khẩu mới  \"", "\"confirmPassword\":\"khác rồi\""));
        assertCode(mismatch, 400, "VALIDATION_ERROR");
        assertThat(mapper.readTree(mismatch.body()).path("data").path("errors").get(0).path("code").asText())
                .isEqualTo("PASSWORD_MISMATCH");
        assertCode(send("POST", RESET_PATH, "application/json", "{"), 400, "INVALID_REQUEST");
        assertCode(send("GET", REQUEST_CODE_PATH, "application/json", ""), 405, "METHOD_NOT_ALLOWED");
        assertCode(send("POST", RESET_PATH, "text/plain", RESET_JSON), 415, "UNSUPPORTED_MEDIA_TYPE");
        assertCode(send("POST", RESET_PATH + "/other", "application/json", RESET_JSON), 401, "ACCESS_TOKEN_INVALID");
        verifyNoInteractions(passwordReset);
    }

    @Test void exposesOnlyContractErrors() throws Exception {
        when(passwordReset.requestCode(any())).thenThrow(new ApplicationException(ErrorCode.PASSWORD_NOT_CONFIGURED),
                new ApplicationException(ErrorCode.OTP_RATE_LIMITED, 61L),
                new ApplicationException(ErrorCode.SERVICE_UNAVAILABLE));
        assertCode(send("POST", REQUEST_CODE_PATH, "application/json", EMAIL_JSON), 409, "PASSWORD_NOT_CONFIGURED");
        var limited = send("POST", REQUEST_CODE_PATH, "application/json", EMAIL_JSON);
        assertCode(limited, 429, "OTP_RATE_LIMITED");
        assertThat(limited.headers().firstValue("Retry-After")).contains("61");
        assertThat(limited.headers().firstValue("Cache-Control")).contains("no-store");
        assertCode(send("POST", REQUEST_CODE_PATH, "application/json", EMAIL_JSON), 503, "SERVICE_UNAVAILABLE");
        doThrow(new ApplicationException(ErrorCode.OTP_ATTEMPTS_EXCEEDED)).when(passwordReset).reset(any());
        assertCode(send("POST", RESET_PATH, "application/json", RESET_JSON), 400, "OTP_ATTEMPTS_EXCEEDED");
    }

    private HttpResponse<String> send(String method, String path, String media, String body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Content-Type", media).header("Authorization", "Bearer expired-or-invalid")
                .method(method, HttpRequest.BodyPublishers.ofString(body)).build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private void assertCode(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(mapper.readTree(response.body()).path("messageCode").asText()).isEqualTo(code);
    }
}
