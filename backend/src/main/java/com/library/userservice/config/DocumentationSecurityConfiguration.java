package com.library.userservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
public class DocumentationSecurityConfiguration {

    @Bean
    @Order(1)
    SecurityFilterChain documentationSecurityFilterChain(HttpSecurity http) throws Exception {
        var paths = PathPatternRequestMatcher.withDefaults();
        return http
                .securityMatcher(new OrRequestMatcher(
                        paths.matcher(HttpMethod.GET, "/swagger-ui.html"), paths.matcher(HttpMethod.GET, "/swagger-ui/**"),
                        paths.matcher(HttpMethod.GET, "/v3/api-docs"), paths.matcher(HttpMethod.GET, "/v3/api-docs.yaml"),
                        paths.matcher(HttpMethod.GET, "/v3/api-docs/swagger-config")))
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable).httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable).logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .build();
    }
}
