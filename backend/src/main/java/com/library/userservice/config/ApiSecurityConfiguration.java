package com.library.userservice.config;

import com.library.userservice.common.api.ErrorCode;
import com.library.userservice.security.SecurityErrorWriter;
import com.library.userservice.security.IntrospectionProtocolFilter;
import com.library.userservice.internal.web.IntrospectionController;
import com.library.userservice.internal.web.InternalUserController;
import com.library.userservice.internal.web.InternalUserStatusController;
import com.library.userservice.auth.web.GoogleAuthController;
import com.library.userservice.auth.web.PasswordResetController;
import com.library.userservice.auth.web.RegistrationController;
import com.library.userservice.security.AdminActorAuthenticationFilter;
import com.library.userservice.security.AdminActorAuthenticator;
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
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class ApiSecurityConfiguration {
    @Bean
    @Order(2)
    SecurityFilterChain publicTokenSecurityFilterChain(HttpSecurity http) throws Exception {
        // Khớp mọi method để MVC trả đúng 405/415, kể cả khi client gửi Bearer cũ.
        return stateless(http).securityMatcher("/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout",
                        RegistrationController.REQUEST_CODE_PATH, RegistrationController.REGISTER_PATH,
                        PasswordResetController.REQUEST_CODE_PATH, PasswordResetController.RESET_PATH,
                        GoogleAuthController.GOOGLE_PATH, GoogleAuthController.REGISTER_PATH)
                .headers(headers -> headers.addHeaderWriter((request, response) -> response.setHeader("Cache-Control", "no-store")))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll()).build();
    }

    @Bean
    @Order(3)
    SecurityFilterChain introspectionSecurityFilterChain(HttpSecurity http, JsonMapper mapper) throws Exception {
        // Chỉ hoãn Service JWT; user token trong form vẫn được xác minh đầy đủ ở service.
        return stateless(http).securityMatcher(IntrospectionController.PATH)
                .headers(headers -> headers.addHeaderWriter((request, response) -> response.setHeader("Cache-Control", "no-store")))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .addFilterBefore(new IntrospectionProtocolFilter(mapper), AnonymousAuthenticationFilter.class).build();
    }

    @Bean
    @Order(4)
    SecurityFilterChain internalUserSecurityFilterChain(HttpSecurity http) throws Exception {
        // Giai đoạn chức năng tin tưởng service gọi; chỉ khớp operation tra cứu user.
        return stateless(http).securityMatcher(InternalUserController.PATH)
                .headers(headers -> headers.addHeaderWriter((request, response) -> response.setHeader("Cache-Control", "no-store")))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll()).build();
    }

    @Bean
    @Order(5)
    SecurityFilterChain internalUserStatusSecurityFilterChain(HttpSecurity http, AdminActorAuthenticator actors,
            SecurityErrorWriter errors) throws Exception {
        return stateless(http).securityMatcher(InternalUserStatusController.PATH)
                .headers(headers -> headers.addHeaderWriter((request, response) -> response.setHeader("Cache-Control", "no-store")))
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> errors.write(response, ErrorCode.AUTHENTICATION_REQUIRED))
                        .accessDeniedHandler((request, response, exception) -> errors.write(response, ErrorCode.ACCESS_DENIED)))
                .addFilterBefore(new AdminActorAuthenticationFilter(actors, errors), AnonymousAuthenticationFilter.class).build();
    }

    @Bean
    @Order(6)
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
