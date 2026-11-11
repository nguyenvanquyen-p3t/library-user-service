package com.library.userservice.config;

import com.library.userservice.common.api.ErrorCode;
import com.library.userservice.security.SecurityErrorWriter;
import com.library.userservice.security.UserAccessTokenService;
import com.library.userservice.security.UserBearerAuthenticationFilter;
import com.library.userservice.security.UserSessionAuthenticator;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class ApiSecurityConfiguration {
    @Bean
    @Order(2)
    SecurityFilterChain publicTokenSecurityFilterChain(HttpSecurity http) throws Exception {
        // Match mọi method để MVC trả đúng 405/415; Bearer cũ không chặn API dùng refresh token.
        return stateless(http).securityMatcher("/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout")
                .headers(headers -> headers.addHeaderWriter((request, response) -> response.setHeader("Cache-Control", "no-store")))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll()).build();
    }

    @Bean
    @Order(3)
    SecurityFilterChain userApiSecurityFilterChain(HttpSecurity http, UserAccessTokenService tokens,
            UserSessionAuthenticator sessions, SecurityErrorWriter errors) throws Exception {
        return stateless(http)
                .authorizeHttpRequests(auth -> auth.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> errors.write(response, ErrorCode.AUTHENTICATION_REQUIRED))
                        .accessDeniedHandler((request, response, exception) -> errors.write(response, ErrorCode.ACCESS_DENIED)))
                .addFilterBefore(new UserBearerAuthenticationFilter(tokens, sessions, errors), AnonymousAuthenticationFilter.class)
                .build();
    }

    private HttpSecurity stateless(HttpSecurity http) throws Exception {
        return http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable).httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable).logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable);
    }
}
