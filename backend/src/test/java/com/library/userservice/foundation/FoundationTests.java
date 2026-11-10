package com.library.userservice.foundation;

import com.library.userservice.auth.dto.GoogleLinkedResult;
import com.library.userservice.auth.dto.GoogleLoginResult;
import com.library.userservice.auth.dto.GoogleRegistrationRequiredResult;
import com.library.userservice.auth.dto.LoginRequest;
import com.library.userservice.auth.dto.RefreshTokenRequest;
import com.library.userservice.auth.dto.RegisterCodeRequest;
import com.library.userservice.auth.dto.RegisterRequest;
import com.library.userservice.auth.dto.TokenPair;
import com.library.userservice.common.api.ApiResponse;
import com.library.userservice.common.api.ErrorCode;
import com.library.userservice.common.api.SuccessCode;
import com.library.userservice.common.exception.ApiExceptionHandler;
import com.library.userservice.common.exception.ApplicationException;
import com.library.userservice.common.exception.IntrospectionErrorFormatter;
import com.library.userservice.config.JsonConfiguration;
import com.library.userservice.internal.dto.IntrospectionActiveResponse;
import com.library.userservice.internal.dto.IntrospectionInactiveResponse;
import com.library.userservice.internal.dto.IntrospectionRequest;
import com.library.userservice.internal.dto.UserStatusRequest;
import com.library.userservice.session.dto.SessionItem;
import com.library.userservice.session.dto.SessionPage;
import com.library.userservice.session.dto.SessionQuery;
import com.library.userservice.user.dto.ProfilePatchRequest;
import com.library.userservice.user.dto.SetPasswordRequest;
import com.library.userservice.user.dto.UserProfile;
import com.library.userservice.user.persistence.UserStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = FoundationTests.FoundationApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"spring.config.import=", "spring.config.location=classpath:/application.yml", "debug=false"})
class FoundationTests {
    @Configuration(proxyBeanMethods = false)
    @TestComponent
    @EnableAutoConfiguration(excludeName = {
            "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
            "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
            "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration"})
    @Import(JsonConfiguration.class)
    static class FoundationApplication {}

    @Autowired JsonMapper mapper;
    @Autowired LocalValidatorFactoryBean validator;
    MockMvc mvc;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new FixtureController())
                .setControllerAdvice(new ApiExceptionHandler())
                .setMessageConverters(new JacksonJsonHttpMessageConverter(mapper))
                .setValidator(validator).build();
    }

    @Test void normalizesBeforeValidatingAndPreservesSecrets() {
        var request = mapper.readValue("""
                {"userName":"  READER  ","email":"  READER@EXAMPLE.COM  ",
                 "password":"  mật khẩu  ","fullName":"  Nguyễn Văn A  ","verificationCode":"012345"}
                """, RegisterRequest.class);
        assertThat(validator.validate(request)).isEmpty();
        assertThat(request.userName()).isEqualTo("reader");
        assertThat(request.email()).isEqualTo("reader@example.com");
        assertThat(request.fullName()).isEqualTo("Nguyễn Văn A");
        assertThat(request.password()).isEqualTo("  mật khẩu  ");
        assertThat(request.verificationCode()).isEqualTo("012345");
        assertThat(request.toString()).doesNotContain(request.password(), request.verificationCode());
        assertThat(validator.validate(new RegisterCodeRequest(" " + "A".repeat(50) + " ", "a@example.com"))).isEmpty();
        assertThat(validator.validate(new LoginRequest("USER", "x"))).isEmpty();
        assertThat(new RefreshTokenRequest(" XyZ ").refreshToken()).isEqualTo(" XyZ ");
        assertThat(validator.validate(new RegisterRequest("a", "a@example.com", "      ", "A", null, "012345"))).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"{},EMPTY_PATCH", "{\"fullName\":null},REQUIRED", "{\"fullName\":\" \"},NOT_BLANK",
            "{\"phoneNumber\":\"\"},INVALID_FORMAT"})
    void validatesPatchPresence(String json, String code) {
        var violations = validator.validate(mapper.readValue(json, ProfilePatchRequest.class));
        assertThat(violations).extracting(v -> v.getMessageTemplate()).contains(code);
    }

    @Test void patchDistinguishesOmittedNullAndValue() throws Exception {
        var omitted = mapper.readValue("{\"fullName\":\" Name \"}", ProfilePatchRequest.class);
        assertThat(omitted.hasFullName()).isTrue();
        assertThat(omitted.hasPhoneNumber()).isFalse();
        assertThat(omitted.getFullName()).isEqualTo("Name");
        var cleared = mapper.readValue("{\"phoneNumber\":null}", ProfilePatchRequest.class);
        assertThat(cleared.hasPhoneNumber()).isTrue();
        assertThat(cleared.getPhoneNumber()).isNull();
        assertThat(validator.validate(cleared)).isEmpty();
        assertThat(mapper.writeValueAsString(cleared)).doesNotContain("Present", "hasPhoneNumber");
        for (String field : List.of("fullNamePresent", "phoneNumberPresent", "hasFullName", "roles"))
            mvc.perform(post("/__foundation/patch").contentType("application/json")
                            .content("{\"fullName\":\"Name\",\"" + field + "\":true}"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.data.errors[0].code").value("UNKNOWN_FIELD"));
        assertThat(validator.validate(new SetPasswordRequest("012345", "abcdef", "abcdefg")))
                .anyMatch(v -> v.getPropertyPath().toString().equals("confirmPassword") && v.getMessageTemplate().equals("PASSWORD_MISMATCH"));
    }

    @Test void validatesBoundaryLengthsFormatsAndAllAvailableFieldErrors() {
        assertThat(validator.validate(new RegisterCodeRequest("a".repeat(51), "invalid-email")))
                .extracting(v -> v.getMessageTemplate()).contains("INVALID_LENGTH", "INVALID_FORMAT");
        assertThat(validator.validate(new RegisterCodeRequest("bad name", "a@example.com")))
                .extracting(v -> v.getMessageTemplate()).contains("INVALID_FORMAT");
        assertThat(validator.validate(new RegisterCodeRequest("bad@name", "a@example.com"))).isNotEmpty();
        for (String password : List.of("12345", "a".repeat(129)))
            assertThat(validator.validate(new SetPasswordRequest("012345", password, password)))
                    .extracting(v -> v.getMessageTemplate()).contains("INVALID_LENGTH");
        for (String phone : List.of("+12345678", "123456789012345"))
            assertThat(validator.validate(new RegisterRequest("a", "a@example.com", "abcdef", "A", phone, "012345"))).isEmpty();
        for (String phone : List.of("1234567", "1234567890123456", "+12 345678", "１２３４５６７８"))
            assertThat(validator.validate(new RegisterRequest("a", "a@example.com", "abcdef", "A", phone, "012345")))
                    .extracting(v -> v.getMessageTemplate()).contains("INVALID_FORMAT");
        assertThat(validator.validate(new RefreshTokenRequest(""))).isNotEmpty();
        assertThat(validator.validate(new RefreshTokenRequest(" "))).isEmpty();
        assertThat(validator.validate(new SessionQuery())).isEmpty();
        assertThat(validator.validate(new SessionQuery(-1, 101))).hasSize(2);
    }

    @Test void serializesRequiredNullsAndSeparateResponseVariants() {
        var profile = new UserProfile(1, "reader", "reader@example.com", "Reader", null, UserStatus.ACTIVE, List.of(), false, List.of());
        var body = mapper.valueToTree(ApiResponse.success(SuccessCode.PROFILE_GET_SUCCESS, profile));
        assertThat(body.size()).isEqualTo(4);
        assertThat(body.path("data").size()).isEqualTo(9);
        assertThat(body.path("data").has("phoneNumber")).isTrue();
        assertThat(body.path("data").path("phoneNumber").isNull()).isTrue();
        assertThat(body.path("data").path("roles").isArray()).isTrue();
        assertThat(mapper.valueToTree(ApiResponse.success(SuccessCode.LOGOUT_SUCCESS, null)).path("data").isNull()).isTrue();
        var tokens = new TokenPair("access-example", "refresh-example");
        var login = mapper.valueToTree(new GoogleLoginResult(tokens));
        var linked = mapper.valueToTree(new GoogleLinkedResult(tokens));
        var register = mapper.valueToTree(new GoogleRegistrationRequiredResult("a@example.com", null, null, "registration-example"));
        assertThat(login.size()).isEqualTo(6);
        assertThat(login.path("isNewUser").asBoolean()).isFalse();
        assertThat(login.path("accountLinked").asBoolean()).isFalse();
        assertThat(linked.path("accountLinked").asBoolean()).isTrue();
        assertThat(register.size()).isEqualTo(6);
        assertThat(register.path("isNewUser").asBoolean()).isTrue();
        assertThat(register.has("accessToken")).isFalse();
        assertThat(register.path("fullName").isNull()).isTrue();
        assertThat(tokens.toString()).doesNotContain(tokens.accessToken(), tokens.refreshToken());
        var now = Instant.parse("2026-11-03T00:00:00Z");
        var page = new SessionPage(List.of(new SessionItem(UUID.randomUUID(), null, null, now, now, now.plusSeconds(60), true)), 0, 20, 1);
        var item = mapper.valueToTree(page).path("items").get(0);
        assertThat(item.size()).isEqualTo(7);
        assertThat(item.path("clientType").isNull()).isTrue();
        assertThat(item.path("createdAt").asText()).isEqualTo("2026-11-03T00:00:00Z");
    }

    @Test void introspectionFormAndResponseAreSeparateFromEnvelope() {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("token", " token-example "); form.add("token_type_hint", "refresh_token");
        assertThat(IntrospectionRequest.fromForm(form).token()).isEqualTo(" token-example ");
        form.add("token", "another");
        assertThatThrownBy(() -> IntrospectionRequest.fromForm(form)).isInstanceOf(ApplicationException.class);
        form.remove("token"); form.add("token", "");
        assertThatThrownBy(() -> IntrospectionRequest.fromForm(form)).isInstanceOf(ApplicationException.class);
        form.set("token", "example"); form.add("unknown", "value");
        assertThatThrownBy(() -> IntrospectionRequest.fromForm(form)).isInstanceOf(ApplicationException.class);
        assertThat(mapper.writeValueAsString(new IntrospectionInactiveResponse())).isEqualTo("{\"active\":false}");
        var active = mapper.valueToTree(new IntrospectionActiveResponse("3", UUID.randomUUID(), "Bearer", "urn:library:user-service", List.of("library-api"), 3600, 0, List.of()));
        assertThat(active.size()).isEqualTo(9);
        assertThat(active.path("active").asBoolean()).isTrue();
        assertThat(active.path("aud").isArray()).isTrue();
        assertThat(active.has("success")).isFalse();
        for (var reason : IntrospectionErrorFormatter.Reason.values()) {
            var response = IntrospectionErrorFormatter.format(reason);
            assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
            assertThat(response.getHeaders().getContentType().toString()).isEqualTo("application/json");
            if (response.getStatusCode().value() == 401) assertThat(response.getHeaders().getFirst("WWW-Authenticate")).startsWith("Bearer");
            if (response.getStatusCode().value() == 405) assertThat(response.getHeaders().getFirst("Allow")).isEqualTo("POST");
        }
        assertThat(IntrospectionErrorFormatter.format(IntrospectionErrorFormatter.Reason.MISSING_AUTHENTICATION).getBody()).isEqualTo(Map.of());
        assertThat(IntrospectionErrorFormatter.format(IntrospectionErrorFormatter.Reason.INSUFFICIENT_SCOPE).getStatusCode().value()).isEqualTo(401);
        assertThat(IntrospectionErrorFormatter.format(IntrospectionErrorFormatter.Reason.UNAVAILABLE).getBody()).isEqualTo(Map.of("error", "temporarily_unavailable"));
    }

    @ParameterizedTest
    @CsvSource({"{\"userName\":12},TYPE_MISMATCH", "{\"userName\":true},TYPE_MISMATCH",
            "{\"roles\":[]},UNKNOWN_FIELD", "{},REQUIRED", "{\"userName\":null},REQUIRED"})
    void mapsBadJsonFieldsToStableCodes(String json, String code) throws Exception {
        mvc.perform(post("/__foundation/register-code").contentType("application/json").content(json))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.messageCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data.errors[0].code").value(code));
    }

    @Test void rejectsSyntaxMissingBodyAndEnumCoercion() throws Exception {
        for (String json : List.of("{", "")) mvc.perform(post("/__foundation/register-code").contentType("application/json").content(json))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.messageCode").value("INVALID_REQUEST"));
        mvc.perform(post("/__foundation/status").contentType("application/json").content("{\"status\":\"PENDING\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.data.errors[0].code").value("INVALID_VALUE"));
        mvc.perform(post("/__foundation/status").contentType("application/json").content("{\"status\":0}"))
                .andExpect(status().isBadRequest());
    }

    @Test void handlesMethodMediaQueryAndPathErrors() throws Exception {
        mvc.perform(get("/__foundation/register-code")).andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", "POST")).andExpect(jsonPath("$.messageCode").value("METHOD_NOT_ALLOWED"));
        mvc.perform(post("/__foundation/register-code").contentType("text/plain").content("example"))
                .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.messageCode").value("UNSUPPORTED_MEDIA_TYPE"));
        mvc.perform(get("/__foundation/page").param("page", "-1"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.data.errors[0].code").value("OUT_OF_RANGE"));
        mvc.perform(get("/__foundation/page").param("page", "abc"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.data.errors[0].code").value("TYPE_MISMATCH"));
        mvc.perform(get("/__foundation/page"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.data.errors[0].code").value("REQUIRED"));
        mvc.perform(get("/__foundation/session/not-a-uuid"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.data.errors[0].code").value("INVALID_FORMAT"));
    }

    @Test void sanitizesFailuresAndPreservesInfrastructureClassification() throws Exception {
        mvc.perform(get("/__foundation/failure").param("kind", "unexpected"))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.messageCode").value("INTERNAL_ERROR"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret-example"))));
        mvc.perform(get("/__foundation/failure").param("kind", "unavailable"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.messageCode").value("SERVICE_UNAVAILABLE"));
        mvc.perform(get("/__foundation/failure").param("kind", "limited"))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "60"));
        var handler = new ApiExceptionHandler();
        var sql = new SQLException("not for response", "23505");
        var conflict = new org.hibernate.exception.ConstraintViolationException("not for response", sql, "UQ_Users_Email");
        assertThat(handler.handle(new DataIntegrityViolationException("hidden", conflict)).getStatusCode().value()).isEqualTo(409);
        var check = new org.hibernate.exception.ConstraintViolationException("hidden", new SQLException("hidden", "23514"), "UQ_Users_Email");
        assertThat(handler.handle(new DataIntegrityViolationException("hidden", check)).getStatusCode().value()).isEqualTo(500);
    }

    @RestController
    @TestComponent
    static class FixtureController {
        @PostMapping(value = "/__foundation/register-code", consumes = "application/json")
        Object registration(@Valid @RequestBody RegisterCodeRequest request) { return request; }
        @PostMapping(value = "/__foundation/status", consumes = "application/json")
        Object userStatus(@Valid @RequestBody UserStatusRequest request) { return request; }
        @PostMapping(value = "/__foundation/patch", consumes = "application/json")
        Object patch(@Valid @RequestBody ProfilePatchRequest request) { return request; }
        @GetMapping("/__foundation/page")
        Object page(@RequestParam @Min(value = 0, message = "OUT_OF_RANGE") int page) { return Map.of("page", page); }
        @GetMapping("/__foundation/session/{id}") Object session(@PathVariable UUID id) { return Map.of("id", id); }
        @GetMapping("/__foundation/failure") Object failure(@RequestParam String kind) {
            throw switch (kind) {
                case "unavailable" -> new DataAccessResourceFailureException("secret-example");
                case "limited" -> new ApplicationException(ErrorCode.OTP_RATE_LIMITED, 60L);
                default -> new IllegalStateException("secret-example");
            };
        }
    }
}
