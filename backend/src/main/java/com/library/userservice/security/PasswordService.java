package com.library.userservice.security;

import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class PasswordService {
    private final PasswordEncoder encoder;
    private final String dummyHash;

    public PasswordService(PasswordEncoder encoder) {
        this.encoder = encoder;
        byte[] dummy = new byte[32]; new SecureRandom().nextBytes(dummy);
        dummyHash = encoder.encode(Base64.getEncoder().encodeToString(dummy));
    }

    public boolean matches(String password, String hash) {
        if (hash == null || !hash.matches("\\{argon2id}\\$argon2id\\$v=19\\$m=19456,t=2,p=1\\$[A-Za-z0-9+/]{22}\\$[A-Za-z0-9+/]{43}")) {
            encoder.matches(password, dummyHash);
            return false;
        }
        try { return encoder.matches(password, hash); }
        catch (IllegalArgumentException exception) {
            encoder.matches(password, dummyHash);
            return false;
        }
    }
}
