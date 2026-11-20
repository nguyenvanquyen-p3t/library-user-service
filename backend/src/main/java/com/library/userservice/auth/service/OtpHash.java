package com.library.userservice.auth.service;

import com.library.userservice.auth.persistence.VerificationPurpose;
import com.library.userservice.common.api.ErrorCode;
import com.library.userservice.common.exception.ApplicationException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class OtpHash {
    private final SecretKeySpec key;

    public OtpHash(@Value("${app.otp.hmac-key:}") String configuredKey) {
        key = decodeKey(configuredKey);
    }

    void requireConfigured() {
        if (key == null) throw new ApplicationException(ErrorCode.SERVICE_UNAVAILABLE);
    }

    String encode(String email, VerificationPurpose purpose, String code) {
        requireConfigured();
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            var input = (purpose.name() + "\n" + email + "\n" + code).getBytes(StandardCharsets.UTF_8);
            return "hmac-sha256-v1:" + HexFormat.of().formatHex(mac.doFinal(input));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Không thể tính hash OTP", exception);
        }
    }

    boolean matches(String email, VerificationPurpose purpose, String code, String storedHash) {
        var expected = encode(email, purpose, code);
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                storedHash.getBytes(StandardCharsets.UTF_8));
    }

    private static SecretKeySpec decodeKey(String value) {
        try {
            byte[] bytes = Base64.getDecoder().decode(value);
            return bytes.length == 32 ? new SecretKeySpec(bytes, "HmacSHA256") : null;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
