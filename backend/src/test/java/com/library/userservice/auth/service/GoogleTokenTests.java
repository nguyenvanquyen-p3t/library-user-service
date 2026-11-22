package com.library.userservice.auth.service;

import static com.library.userservice.auth.service.GoogleTestJwks.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.library.userservice.common.api.ErrorCode;
import com.library.userservice.common.exception.ApplicationException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.SignedJWT;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import org.junit.jupiter.api.Test;

class GoogleTokenTests {
    private static final Instant NOW = Instant.parse("2026-11-22T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test void verifiesOnlyTrustedSignatureIssuerAudiencePresenterAndTime() throws Exception {
        try (var jwks = new GoogleTestJwks()) {
            var verifier = verifier(jwks, Duration.ofSeconds(3));
            String good = token(INITIAL, INITIAL.getKeyID(), NOW, claims -> claims
                    .issuer("accounts.google.com").claim("azp", PRESENTER)
                    .claim("email", "reader@example.com").claim("email_verified", true).claim("hd", "example.com"));
            var identity = verifier.verify(good);
            assertThat(identity.subject()).isEqualTo("google-test-user");
            assertThat(identity.emailVerified()).isTrue();
            assertThat(identity.hostedDomain()).isTrue();

            for (String invalid : new String[]{
                    token(ROTATED, INITIAL.getKeyID(), NOW, claims -> {}),
                    token(INITIAL, "unknown-key", NOW, claims -> {}),
                    token(INITIAL, INITIAL.getKeyID(), NOW, claims -> claims.issuer("https://example.com")),
                    token(INITIAL, INITIAL.getKeyID(), NOW, claims -> claims.audience("different-app")),
                    token(INITIAL, INITIAL.getKeyID(), NOW, claims -> claims.claim("azp", "different-presenter")),
                    token(INITIAL, INITIAL.getKeyID(), NOW, claims -> claims.subject(" ")),
                    token(INITIAL, INITIAL.getKeyID(), NOW, claims -> claims.expirationTime(Date.from(NOW))),
                    token(INITIAL, INITIAL.getKeyID(), NOW, claims -> claims.issueTime(Date.from(NOW.plusSeconds(31)))),
                    " " + good}) {
                assertThatThrownBy(() -> verifier.verify(invalid))
                        .isInstanceOfSatisfying(ApplicationException.class,
                                failure -> assertThat(failure.code()).isEqualTo(ErrorCode.GOOGLE_TOKEN_INVALID));
            }
        }
    }

    @Test void refreshesJwksWhenGoogleRotatesTheKey() throws Exception {
        try (var jwks = new GoogleTestJwks()) {
            var verifier = verifier(jwks, Duration.ofSeconds(3));
            verifier.verify(token(INITIAL, INITIAL.getKeyID(), NOW, claims -> claims.claim("azp", AUDIENCE)));
            jwks.served = ROTATED;
            var identity = verifier.verify(token(ROTATED, ROTATED.getKeyID(), NOW, claims -> claims.subject("rotated-user")));
            assertThat(identity.subject()).isEqualTo("rotated-user");
        }
    }

    @Test void distinguishesTimeoutAndUpstreamHttpErrorsFromInvalidCredentials() throws Exception {
        String good = token(INITIAL, INITIAL.getKeyID(), NOW, claims -> {});
        try (var jwks = new GoogleTestJwks()) {
            jwks.responseStatus = 503;
            assertUnavailable(verifier(jwks, Duration.ofSeconds(3)), good);
        }
        try (var jwks = new GoogleTestJwks()) {
            jwks.delayResponse = true;
            assertUnavailable(verifier(jwks, Duration.ofMillis(250)), good);
        }
    }

    @Test void registrationTokenUsesTheOriginalExpiryAndRequiresItsOwnSignatureAndPurpose() throws Exception {
        String secret = Base64.getEncoder().encodeToString(new byte[32]);
        var tokens = new GoogleRegistrationToken(CLOCK, secret);
        String value = tokens.issue("subject", "reader@example.com");
        assertThat(tokens.verify(value).email()).isEqualTo("reader@example.com");
        var justBeforeExpiry = new GoogleRegistrationToken(Clock.offset(CLOCK, Duration.ofSeconds(599)), secret);
        assertThat(justBeforeExpiry.verify(value).expiresAt()).isEqualTo(NOW.plusSeconds(600));
        var atExpiry = new GoogleRegistrationToken(Clock.offset(CLOCK, Duration.ofSeconds(600)), secret);
        assertRegistrationInvalid(atExpiry, value);

        var parts = value.split("\\.");
        char replacement = parts[2].charAt(0) == 'A' ? 'B' : 'A';
        assertRegistrationInvalid(tokens, parts[0] + "." + parts[1] + "." + replacement + parts[2].substring(1));
        assertRegistrationInvalid(tokens, token(INITIAL, INITIAL.getKeyID(), NOW, claims -> {}));
        var jwt = SignedJWT.parse(value);
        var wrongPurpose = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256)
                .type(JOSEObjectType.JWT).build(), jwt.getJWTClaimsSet());
        wrongPurpose.sign(new MACSigner(new byte[32]));
        assertRegistrationInvalid(tokens, wrongPurpose.serialize());
    }

    @Test void requiresAnExplicitAllowlistAndASeparateValidRegistrationKey() throws Exception {
        try (var jwks = new GoogleTestJwks()) {
            var verifier = new GoogleIdTokenVerifier(CLOCK, " , ", PRESENTER, jwks.uri(), Duration.ofSeconds(3));
            assertUnavailable(verifier, token(INITIAL, INITIAL.getKeyID(), NOW, claims -> {}));
        }
        assertThatThrownBy(() -> new GoogleRegistrationToken(CLOCK, "invalid").requireConfigured())
                .isInstanceOfSatisfying(ApplicationException.class,
                        failure -> assertThat(failure.code()).isEqualTo(ErrorCode.SERVICE_UNAVAILABLE));
    }

    private static GoogleIdTokenVerifier verifier(GoogleTestJwks jwks, Duration timeout) {
        return new GoogleIdTokenVerifier(CLOCK, "other-test-audience, " + AUDIENCE, PRESENTER, jwks.uri(), timeout);
    }

    private static void assertUnavailable(GoogleIdTokenVerifier verifier, String token) {
        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOfSatisfying(ApplicationException.class,
                        failure -> assertThat(failure.code()).isEqualTo(ErrorCode.SERVICE_UNAVAILABLE));
    }

    private static void assertRegistrationInvalid(GoogleRegistrationToken tokens, String token) {
        assertThatThrownBy(() -> tokens.verify(token))
                .isInstanceOfSatisfying(ApplicationException.class,
                        failure -> assertThat(failure.code()).isEqualTo(ErrorCode.REGISTRATION_TOKEN_INVALID));
    }
}
