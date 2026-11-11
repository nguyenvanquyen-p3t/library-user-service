package com.library.userservice.documentation;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashSet;
import java.util.Set;

import com.library.userservice.config.DocumentationSecurityConfiguration;
import com.library.userservice.config.OpenApiConfiguration;
import com.library.userservice.config.JsonConfiguration;
import com.library.userservice.config.ApiSecurityConfiguration;
import com.library.userservice.security.SecurityErrorWriter;
import com.library.userservice.security.UserAccessTokenService;
import com.library.userservice.security.UserSessionAuthenticator;
import org.springframework.context.annotation.Bean;
import com.library.userservice.common.exception.ApiExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.ApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = ApiDocumentationTests.DocumentationApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.config.location=classpath:/application.yml",
                "spring.config.import=",
                "debug=false",
                "spring.security.user.name=documentation-test",
                "spring.security.user.password=documentation-test-only"
        })
class ApiDocumentationTests {

    @Configuration(proxyBeanMethods = false)
    @TestComponent
    @EnableAutoConfiguration(excludeName = {
            "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
            "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
            "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration"
    })
    @Import({OpenApiConfiguration.class, DocumentationSecurityConfiguration.class, ApiSecurityConfiguration.class,
            SecurityErrorWriter.class, JsonConfiguration.class, ApiExceptionHandler.class})
    static class DocumentationApplication {
        @Bean UserAccessTokenService tokens() { return org.mockito.Mockito.mock(UserAccessTokenService.class); }
        @Bean UserSessionAuthenticator sessions() { return org.mockito.Mockito.mock(UserSessionAuthenticator.class); }
    }

    @LocalServerPort
    int port;

    @Autowired
    ApplicationContext context;

    private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER).build();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void publishesAllTwentyDesignedOperationsAndTheirSecurity() throws Exception {
        var result = get("/v3/api-docs");
        assertThat(result.statusCode()).isEqualTo(200);
        var document = mapper.readTree(result.body());
        assertThat(document.path("openapi").asText()).startsWith("3.0.");
        Set<String> operationIds = new HashSet<>();
        int count = 0;
        for (var path : document.path("paths")) {
            for (var operation : path) {
                count++;
                assertThat(operation.path("x-implementation-status").asText()).isEqualTo(
                        Set.of("login", "refreshTokens", "logout").contains(operation.path("operationId").asText())
                                ? "IMPLEMENTED" : "DESIGN_ONLY");
                assertThat(operationIds.add(operation.path("operationId").asText())).isTrue();
            }
        }
        assertThat(count).isEqualTo(20);
        var paths = document.path("paths");
        for (String publicPath : new String[]{"/auth/login", "/auth/refresh", "/auth/logout"}) {
            assertThat(paths.path("/api/v1" + publicPath).path("post").path("security").isEmpty()).isTrue();
        }
        assertThat(paths.path("/api/v1/users/me").path("get").path("security").get(0).has("UserAccessToken")).isTrue();
        var adminSecurity = paths.path("/api/v1/internal/users/{id}/status").path("patch").path("security");
        assertThat(adminSecurity.size()).isEqualTo(1);
        assertThat(adminSecurity.get(0).has("ServiceJwt")).isTrue();
        assertThat(adminSecurity.get(0).has("AdminAccessToken")).isTrue();
        var introspection = paths.path("/api/v1/internal/oauth2/introspect").path("post");
        assertThat(introspection.path("requestBody").path("content").has("application/x-www-form-urlencoded")).isTrue();
        assertThat(introspection.path("responses").path("401").path("headers").has("WWW-Authenticate")).isTrue();
        for (JsonNode response : introspection.path("responses")) {
            assertThat(response.path("headers").has("Cache-Control")).isTrue();
        }
        var schemas = document.path("components").path("schemas");
        assertThat(schemas.path("ProfilePatchRequest").path("additionalProperties").asBoolean()).isFalse();
        assertThat(schemas.path("ProfilePatchRequest").path("minProperties").asInt()).isEqualTo(1);
        assertThat(schemas.path("UserProfile").path("properties").path("phoneNumber").path("nullable").asBoolean()).isTrue();
        assertThat(schemas.path("TokenPair").path("properties").path("expiresIn").path("enum").get(0).asInt()).isEqualTo(3600);
        assertThat(schemas.path("SessionItem").path("properties").path("clientType").path("enum").get(2).isNull()).isTrue();
        var yaml = get("/v3/api-docs.yaml");
        assertThat(yaml.statusCode()).isEqualTo(200);
        assertThat(yaml.body()).contains("introspectUserAccessToken");
    }

    @Test
    void servesSwaggerUiWithExecutionAndExternalValidationDisabled() throws Exception {
        assertThat(get("/swagger-ui.html").statusCode()).isEqualTo(302);
        var ui = get("/swagger-ui/index.html");
        assertThat(ui.statusCode()).isEqualTo(200);
        assertThat(ui.body()).contains("swagger-ui-bundle.js");
        assertThat(get("/swagger-ui/swagger-ui-bundle.js").statusCode()).isEqualTo(200);
        var result = get("/v3/api-docs/swagger-config");
        assertThat(result.statusCode()).isEqualTo(200);
        var config = mapper.readTree(result.body());
        assertThat(config.path("url").asText()).isEqualTo("/v3/api-docs");
        assertThat(config.has("supportedSubmitMethods")).isTrue();
        assertThat(config.path("supportedSubmitMethods").isEmpty()).isTrue();
        assertThat(config.path("validatorUrl").asText()).isEmpty();
        assertThat(config.path("persistAuthorization").asBoolean()).isFalse();
    }

    @Test
    void documentationContextDoesNotLoadDatabaseOrEnvironmentFile() {
        assertThat(context.getBeanNamesForType(javax.sql.DataSource.class)).isEmpty();
        assertThat(context.getBeanNamesForType(org.flywaydb.core.Flyway.class)).isEmpty();
        assertThat(context.getEnvironment().getProperty("spring.config.import")).isEmpty();
    }

    @Test
    void documentationAllowlistKeepsOtherPathsProtected() throws Exception {
        assertThat(get("/api/v1/internal/users/3").statusCode()).isEqualTo(401);
        assertThat(get("/api/v1/users/me").statusCode()).isEqualTo(401);
    }

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                        .header("Accept", path.endsWith(".yaml") ? "*/*" : "application/json")
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
