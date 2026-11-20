package com.library.userservice.auth.service;

import static com.library.userservice.auth.persistence.VerificationPurpose.PASSWORD_RESET;
import static com.library.userservice.auth.persistence.VerificationPurpose.REGISTER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.library.userservice.common.api.ErrorCode;
import com.library.userservice.common.exception.ApplicationException;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class OtpHashTests {
    @Test void hashIsBoundToKeyEmailPurposeAndExactDigits() {
        byte[] key = new byte[32];
        var hasher = new OtpHash(Base64.getEncoder().encodeToString(key));
        String hash = hasher.encode("reader@example.com", REGISTER, "012345");
        key[0] = 1;
        var differentKey = new OtpHash(Base64.getEncoder().encodeToString(key));
        assertThat(hash).matches("hmac-sha256-v1:[a-f0-9]{64}");
        assertThat(hasher.matches("reader@example.com", REGISTER, "012345", hash)).isTrue();
        assertThat(hasher.matches("reader@example.com", REGISTER, "12345", hash)).isFalse();
        assertThat(hasher.matches("other@example.com", REGISTER, "012345", hash)).isFalse();
        assertThat(hasher.matches("reader@example.com", PASSWORD_RESET, "012345", hash)).isFalse();
        assertThat(differentKey.matches("reader@example.com", REGISTER, "012345", hash)).isFalse();
    }

    @Test void missingMalformedOrShortKeyIsUnavailable() {
        for (String configuration : new String[] {"", "not!base64", "dGVzdA=="}) {
            var hasher = new OtpHash(configuration);
            assertThatThrownBy(() -> hasher.encode("reader@example.com", REGISTER, "012345"))
                    .isInstanceOfSatisfying(ApplicationException.class,
                            error -> assertThat(error.code()).isEqualTo(ErrorCode.SERVICE_UNAVAILABLE));
        }
    }
}
