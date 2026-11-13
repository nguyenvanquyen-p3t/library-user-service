package com.library.userservice.security;

import com.library.userservice.common.api.ErrorCode;
import com.library.userservice.common.exception.ApplicationException;
import com.library.userservice.config.UserSecurityConfiguration;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import java.nio.file.Files;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.FileSystemResource;
import static org.assertj.core.api.Assertions.*;

class UserSecurityTests extends UserSecurityTestKeys {
    private static final Instant NOW = Instant.parse("2026-11-10T00:00:00Z");
    private static final UUID SID = UUID.randomUUID();
    private static final JWSHeader HEADER = new JWSHeader.Builder(JWSAlgorithm.RS256)
            .type(new JOSEObjectType("at+jwt")).keyID("user-access-v1").build();

    private UserAccessTokenService service(Instant now) { return new UserAccessTokenService(properties(), Clock.fixed(now, ZoneOffset.UTC)); }

    @Test void argon2PreservesUnicodeWhitespaceAndLongPasswordsWithFreshSalts() {
        var encoder = new UserSecurityConfiguration().passwordEncoder();
        var passwords = new PasswordService(encoder);
        String password = "  mật khẩu " + "密".repeat(100) + " ";
        String first = passwords.encode(password), second = passwords.encode(password);
        assertThat(first.equals(second)).isFalse();
        assertThat(first.startsWith("{argon2id}$argon2id$v=19$m=19456,t=2,p=1$")).isTrue();
        assertThat(passwords.matches(password, first)).isTrue();
        assertThat(passwords.matches(password.strip(), first)).isFalse();
        assertThat(passwords.matches(password.substring(0, 72), first)).isFalse();
        assertThat(passwords.matches(password, null)).isFalse();
        assertThat(passwords.matches(password, "{noop}example")).isFalse();
        assertThat(passwords.matches(password, "{argon2id}$argon2id$broken")).isFalse();
    }

    @Test void opaqueRefreshTokensAreRandomAndOnlyTheirStableHashIsStored() {
        var generator = new RefreshTokenGenerator();
        String first = generator.generate(), second = generator.generate();
        assertThat(first.matches("[A-Za-z0-9_-]{43}")).isTrue();
        assertThat(first.equals(second)).isFalse();
        assertThat(generator.hash(first).matches("[a-f0-9]{64}")).isTrue();
        assertThat(generator.hash(first).equals(generator.hash(first))).isTrue();
        assertThat(generator.hash(first).equals(generator.hash(second))).isFalse();
    }

    @Test void signedJwtRoundTripsWithExactContractAndNoRoleSnapshot() {
        var service = service(NOW);
        var jwt = service.verify(service.issue(3, SID));
        assertThat(jwt.getHeaders()).containsEntry("alg", "RS256").containsEntry("typ", "at+jwt").containsEntry("kid", "user-access-v1");
        assertThat(jwt.getClaims().keySet()).containsExactlyInAnyOrder("iss", "aud", "sub", "sid", "iat", "exp");
        assertThat(jwt.getSubject()).isEqualTo("3");
        assertThat(jwt.getClaimAsString("sid")).isEqualTo(SID.toString());
        assertThat(jwt.getExpiresAt()).isEqualTo(jwt.getIssuedAt().plusSeconds(3600));
    }

    @Test void sessionExpiryHasNoSkewAndPrincipalRolesCannotBeMutated() {
        var users = org.mockito.Mockito.mock(com.library.userservice.user.persistence.UserRepository.class);
        var sessions = org.mockito.Mockito.mock(com.library.userservice.session.persistence.UserSessionRepository.class);
        var roles = org.mockito.Mockito.mock(com.library.userservice.user.persistence.RoleRepository.class);
        var user = new com.library.userservice.user.persistence.User();
        user.setStatus(com.library.userservice.user.persistence.UserStatus.ACTIVE);
        var session = new com.library.userservice.session.persistence.UserSession();
        session.setId(SID); session.setUserId(3); session.setExpiresAt(NOW);
        org.mockito.Mockito.when(users.findById(3)).thenReturn(java.util.Optional.of(user));
        org.mockito.Mockito.when(sessions.findById(SID)).thenReturn(java.util.Optional.of(session));
        org.mockito.Mockito.when(roles.findNamesByUserId(3)).thenReturn(List.of("READER"));
        var auth = new UserSessionAuthenticator(users, sessions, roles, Clock.fixed(NOW, ZoneOffset.UTC));
        var token = service(NOW).verify(service(NOW).issue(3, SID));
        assertThatThrownBy(() -> auth.authenticate(token)).isInstanceOfSatisfying(ApplicationException.class,
                exception -> assertThat(exception.code()).isEqualTo(ErrorCode.SESSION_INACTIVE));
        session.setExpiresAt(NOW.plusNanos(1));
        var principal = auth.authenticate(token);
        assertThatThrownBy(() -> principal.roles().add("ADMIN")).isInstanceOf(UnsupportedOperationException.class);
        assertThat(principal.roles()).containsExactly("READER");
    }

    @Test void rejectsInvalidHeadersUnknownKeysAndOtherAlgorithmsOrSignatures() throws Exception {
        var claims = claims();
        reject(sign(claims, new JWSHeader.Builder(JWSAlgorithm.RS256).type(new JOSEObjectType("JWT")).keyID("user-access-v1").build()));
        reject(sign(claims, new JWSHeader.Builder(JWSAlgorithm.RS256).type(new JOSEObjectType("at+jwt")).keyID("unknown").build()));
        reject(sign(claims, new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("user-access-v1").build()));
        var hs = new JWSObject(new JWSHeader.Builder(JWSAlgorithm.HS256).type(new JOSEObjectType("at+jwt")).keyID("user-access-v1").build(), new Payload(claims));
        hs.sign(new MACSigner(new byte[32])); reject(hs.serialize());
        var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
        var other = new JWSObject(HEADER, new Payload(claims)); other.sign(new RSASSASigner(generator.generateKeyPair().getPrivate()));
        reject(other.serialize());
        reject("not-a-jwt"); reject(new RefreshTokenGenerator().generate());
    }

    @Test void rejectsMissingWrongTypeAndWrongValueClaimsBeforeCoercion() throws Exception {
        for (String claim : List.of("iss", "aud", "sub", "sid", "iat", "exp")) {
            var values = claims(); values.remove(claim); reject(sign(values, HEADER));
        }
        Map<String, Object> bad = Map.of("iss", "wrong", "aud", List.of("wrong"), "sub", "0", "sid", "not-a-uuid",
                "iat", NOW.toString(), "exp", NOW.plusSeconds(3600).toString());
        for (var entry : bad.entrySet()) {
            var values = claims(); values.put(entry.getKey(), entry.getValue()); reject(sign(values, HEADER));
        }
        for (Object subject : List.of(3, "2147483648", "03", "-1")) {
            var values = claims(); values.put("sub", subject); reject(sign(values, HEADER));
        }
        for (Object audience : List.of("library-api", List.of(3), List.of())) {
            var values = claims(); values.put("aud", audience); reject(sign(values, HEADER));
        }
        var fractional = claims(); fractional.put("exp", NOW.getEpochSecond() + 3600.5); reject(sign(fractional, HEADER));
    }

    @Test void enforcesExactThirtySecondClockSkewBoundaries() throws Exception {
        String token = sign(claims(), HEADER);
        assertThat(service(NOW.plusSeconds(3629)).verify(token).getSubject()).isEqualTo("3");
        assertThatThrownBy(() -> service(NOW.plusSeconds(3630)).verify(token)).isInstanceOf(ApplicationException.class);
        assertThat(service(NOW.minusSeconds(30)).verify(token).getSubject()).isEqualTo("3");
        assertThatThrownBy(() -> service(NOW.minusSeconds(31)).verify(token)).isInstanceOf(ApplicationException.class);
        var nbf = claims(); nbf.put("nbf", NOW.plusSeconds(31).getEpochSecond()); reject(sign(nbf, HEADER));
        var invalid = claims(); invalid.put("exp", NOW.getEpochSecond()); reject(sign(invalid, HEADER));
    }

    @Test void failsClosedOnMissingMalformedOrMismatchedKeys(@TempDir java.nio.file.Path directory) throws Exception {
        var missing = new FileSystemResource(directory.resolve("missing.pem"));
        var brokenFile = directory.resolve("broken.pem"); Files.writeString(brokenFile, "invalid fixture");
        var broken = new FileSystemResource(brokenFile);
        for (var resource : List.of(missing, broken, new FileSystemResource(PUBLIC_KEY))) {
            var props = new UserAccessProperties("issuer", "aud", "key", resource, Map.of("key", new FileSystemResource(PUBLIC_KEY)));
            assertThatThrownBy(() -> new UserAccessTokenService(props, Clock.systemUTC()))
                    .isInstanceOf(IllegalStateException.class).hasMessage("Không nạp được cặp khóa user access token hợp lệ").hasNoCause();
        }
        var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
        var other = directory.resolve("other.pem");
        Files.writeString(other, "-----BEGIN PUBLIC KEY-----\n" + java.util.Base64.getEncoder().encodeToString(generator.generateKeyPair().getPublic().getEncoded()) + "\n-----END PUBLIC KEY-----");
        var mismatch = new UserAccessProperties("issuer", "aud", "key", new FileSystemResource(PRIVATE_KEY), Map.of("key", new FileSystemResource(other)));
        assertThatThrownBy(() -> new UserAccessTokenService(mismatch, Clock.systemUTC())).isInstanceOf(IllegalStateException.class).hasNoCause();
    }

    private Map<String, Object> claims() {
        return new HashMap<>(Map.of("iss", "urn:library:user-service", "aud", List.of("library-api"), "sub", "3",
                "sid", SID.toString(), "iat", NOW.getEpochSecond(), "exp", NOW.plusSeconds(3600).getEpochSecond()));
    }
    private String sign(Map<String, Object> claims, JWSHeader header) throws Exception {
        var object = new JWSObject(header, new Payload(claims)); object.sign(new RSASSASigner(KEYS.getPrivate())); return object.serialize();
    }
    private void reject(String token) {
        assertThatThrownBy(() -> service(NOW).verify(token)).isInstanceOfSatisfying(ApplicationException.class,
                exception -> assertThat(exception.code()).isEqualTo(ErrorCode.ACCESS_TOKEN_INVALID));
    }
}
