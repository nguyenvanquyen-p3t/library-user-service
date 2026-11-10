package com.library.userservice.config;

import com.library.userservice.security.UserAccessProperties;
import com.library.userservice.security.UserAccessTokenService;
import java.time.Clock;
import java.util.Map;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(UserAccessProperties.class)
public class UserSecurityConfiguration {
    @Bean
    Clock userSecurityClock() { return Clock.systemUTC(); }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new DelegatingPasswordEncoder("argon2id", Map.of("argon2id", new Argon2PasswordEncoder(16, 32, 1, 19456, 2)));
    }

    @Bean
    UserAccessTokenService userAccessTokenService(UserAccessProperties properties, Clock clock) {
        return new UserAccessTokenService(properties, clock);
    }
}
