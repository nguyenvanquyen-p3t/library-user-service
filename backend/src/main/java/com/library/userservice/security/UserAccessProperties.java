package com.library.userservice.security;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

@ConfigurationProperties("app.security.user-access")
public record UserAccessProperties(String issuer, String audience, String activeKeyId,
        Resource privateKeyLocation, Map<String, Resource> publicKeys) {
    public UserAccessProperties {
        if (issuer == null || issuer.isBlank() || audience == null || audience.isBlank()
                || activeKeyId == null || activeKeyId.isBlank() || privateKeyLocation == null
                || publicKeys == null || !publicKeys.containsKey(activeKeyId))
            throw new IllegalArgumentException("Cấu hình user access token chưa đầy đủ");
        publicKeys = Map.copyOf(publicKeys);
    }
}
