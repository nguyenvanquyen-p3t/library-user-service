package com.library.userservice.auth.service;

import com.library.userservice.common.api.ErrorCode;
import com.library.userservice.common.exception.ApplicationException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.RemoteKeySourceException;
import com.nimbusds.jwt.SignedJWT;
import java.net.http.HttpClient;
import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
class GoogleIdTokenVerifier {
    private static final String JWKS_URI = "https://www.googleapis.com/oauth2/v3/certs";
    private static final Set<String> ISSUERS = Set.of("accounts.google.com", "https://accounts.google.com");
    private final Clock clock;
    private final Set<String> audiences;
    private final Set<String> presenters;
    private final NimbusJwtDecoder decoder;

    @Autowired
    GoogleIdTokenVerifier(Clock clock, @Value("${app.google.allowed-client-ids:}") String audiences,
            @Value("${app.google.allowed-presenter-client-ids:}") String presenters) {
        this(clock, audiences, presenters, JWKS_URI, Duration.ofSeconds(3));
    }

    GoogleIdTokenVerifier(Clock clock, String audiences, String presenters, String jwksUri, Duration timeout) {
        this.clock = clock;
        this.audiences = clientIds(audiences);
        this.presenters = clientIds(presenters);
        var http = HttpClient.newBuilder().connectTimeout(timeout).build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(timeout);
        decoder = NimbusJwtDecoder.withJwkSetUri(jwksUri)
                .restOperations(new RestTemplate(factory))
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();
        // Kiểm tra claim dưới đây bằng kiểu JSON gốc và đồng hồ được inject.
        decoder.setJwtValidator(jwt -> OAuth2TokenValidatorResult.success());
    }

    void requireConfigured() {
        if (audiences.isEmpty()) throw new ApplicationException(ErrorCode.SERVICE_UNAVAILABLE);
    }

    Identity verify(String token) {
        requireConfigured();
        try {
            var jwt = SignedJWT.parse(token);
            var header = jwt.getHeader();
            if (!jwt.serialize().equals(token) || !JWSAlgorithm.RS256.equals(header.getAlgorithm())
                    || header.getKeyID() == null || header.getKeyID().isBlank()
                    || header.getJWK() != null || header.getJWKURL() != null || header.getX509CertURL() != null)
                throw invalid();
            // Sàng lọc claim bắt buộc trước khi tải khóa. Chỉ trả danh tính sau khi xác minh chữ ký.
            var claims = jwt.getPayload().toJSONObject();
            if (!(claims.get("iss") instanceof String issuer) || !ISSUERS.contains(issuer)
                    || !(claims.get("aud") instanceof String audience) || !audiences.contains(audience)
                    || !(claims.get("sub") instanceof String sub) || !validSubject(sub)
                    || (claims.containsKey("azp") && (!(claims.get("azp") instanceof String presenter)
                            || (!audience.equals(presenter) && !presenters.contains(presenter)))))
                throw invalid();
            var issued = numericDate(claims.get("iat"));
            var expires = numericDate(claims.get("exp"));
            var now = clock.instant();
            if (!expires.isAfter(issued) || issued.isAfter(now.plusSeconds(30)) || !now.isBefore(expires)
                    || (claims.containsKey("nbf") && now.isBefore(numericDate(claims.get("nbf")))))
                throw invalid();

            decoder.decode(token);
            requireUnexpired(expires);
            var emailVerified = claims.get("email_verified");
            return new Identity(sub, text(claims.get("email")),
                    Boolean.TRUE.equals(emailVerified) || "true".equals(emailVerified),
                    claims.get("hd") instanceof String domain && !domain.isBlank(),
                    text(claims.get("name")), expires);
        } catch (BadJwtException | ParseException | java.time.DateTimeException exception) {
            throw invalid();
        } catch (JwtException exception) {
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof RemoteKeySourceException)
                    throw new ApplicationException(ErrorCode.SERVICE_UNAVAILABLE);
            }
            throw invalid();
        }
    }

    void requireUnexpired(Instant expires) {
        if (!clock.instant().isBefore(expires)) throw invalid();
    }

    static boolean validSubject(String subject) {
        return subject.matches("[!-~]{1,255}");
    }

    private static Instant numericDate(Object value) {
        if (value instanceof Integer || value instanceof Long)
            return Instant.ofEpochSecond(((Number) value).longValue());
        throw invalid();
    }

    private static String text(Object value) {
        return value instanceof String string ? string : null;
    }

    private static Set<String> clientIds(String config) {
        return Arrays.stream(config.split(",")).map(String::strip).filter(id -> !id.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    private static ApplicationException invalid() {
        return new ApplicationException(ErrorCode.GOOGLE_TOKEN_INVALID);
    }

    record Identity(String subject, String email, boolean emailVerified, boolean hostedDomain,
            String fullName, Instant expiresAt) {
        @Override public String toString() { return "GoogleIdentity[REDACTED]"; }
    }
}
