package com.library.userservice.config;

import java.io.IOException;

import io.swagger.v3.core.util.Yaml;
import io.swagger.v3.oas.models.OpenAPI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfiguration {

    @Bean
    OpenAPI userServiceOpenApi() throws IOException {
        var resource = new ClassPathResource("openapi/user-service.yaml");
        try (var input = resource.getInputStream()) {
            return Yaml.mapper().readValue(input, OpenAPI.class);
        }
    }
}
