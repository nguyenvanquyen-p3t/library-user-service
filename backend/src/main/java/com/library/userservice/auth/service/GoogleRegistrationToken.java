package com.library.userservice.auth.service;

import com.library.userservice.common.api.ErrorCode;
import com.library.userservice.common.exception.ApplicationException;
import com.library.userservice.common.validation.Input;
import com.library.userservice.common.validation.InputRules;
import com.library.userservice.common.validation.Normalization;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.text.ParseException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class GoogleRegistrationToken {
    private static final JOSEObjectType TYPE = new JOSEObjectType("google-registration+jwt");
    private static final String ISSUER = "urn:library:user-service";
    private static final String AUDIENCE = "library-google-registration";
    private static final long TTL_SECONDS = 600;
    private final Clock clock;
    private final byte[] key;

    GoogleRegistrationToken(Clock clock, @Value("${app.google.registration-hmac-key:}") String configuredKey) {
        this.clock = clock;
        key = decodeKey(configuredKey);
    }

    void requireConfigured() {
        if (key == null) throw new ApplicationException(ErrorCode.SERVICE_UNAVAILABLE);
    }

    String issue(String subject, String email) {
        requireConfigured();
        var now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        var claims = new JWTClaimsSet.Builder()
                .issuer(ISSUER).audience(AUDIENCE).subject(subject).claim("email", email)
                .issueTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(TTL_SECONDS))).build();
        var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256).type(TYPE).build(), claims);
        try {
            jwt.sign(new MACSigner(key));
            return jwt.serialize();
        } catch (JOSEException exception) {
            throw new IllegalStateException("Không ký được token đăng ký Google");
        }
    }

    Registration verify(String value) {
        requireConfigured();
        try {
            var jwt = SignedJWT.parse(value);
            var header = jwt.getHeader();
            if (!jwt.serialize().equals(value) || !JWSAlgorithm.HS256.equals(header.getAlgorithm())
                    || !TYPE.equals(header.getType()) || !jwt.verify(new MACVerifier(key)))
                throw invalid();
            var claims = jwt.getJWTClaimsSet();
            var subject = claims.getSubject();
            var email = claims.getStringClaim("email");
            var issued = claims.getIssueTime();
            var expires = claims.getExpirationTime();
            if (!ISSUER.equals(claims.getIssuer()) || !List.of(AUDIENCE).equals(claims.getAudience())
                    || subject == null || !GoogleIdTokenVerifier.validSubject(subject)
                    || InputRules.check(email, Input.Kind.EMAIL, true) != null
                    || !email.equals(Normalization.identifier(email)) || issued == null || expires == null
                    || issued.toInstant().isAfter(clock.instant())
                    || !issued.toInstant().plusSeconds(TTL_SECONDS).equals(expires.toInstant()))
                throw invalid();
            requireUnexpired(expires.toInstant());
            return new Registration(subject, email, expires.toInstant());
        } catch (ParseException | JOSEException exception) {
            throw invalid();
        }
    }

    void requireUnexpired(Instant expires) {
        if (!clock.instant().isBefore(expires)) throw invalid();
    }

    private static byte[] decodeKey(String value) {
        try {
            byte[] decoded = Base64.getDecoder().decode(value);
            return decoded.length == 32 ? decoded : null;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static ApplicationException invalid() {
        return new ApplicationException(ErrorCode.REGISTRATION_TOKEN_INVALID);
    }

    record Registration(String subject, String email, Instant expiresAt) {
        @Override public String toString() { return "GoogleRegistration[REDACTED]"; }
    }
}
