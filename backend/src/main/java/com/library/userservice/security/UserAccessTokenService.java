package com.library.userservice.security;

import com.library.userservice.common.api.ErrorCode;
import com.library.userservice.common.exception.ApplicationException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/** Chỉ ký/xác minh user access token; không kiểm tra database hoặc Service JWT. */
public class UserAccessTokenService {
    private final UserAccessProperties properties;
    private final Clock clock;
    private final JWSSigner signer;
    private final Map<String, NimbusJwtDecoder> decoders;

    public UserAccessTokenService(UserAccessProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        try {
            var factory = KeyFactory.getInstance("RSA");
            var privateKey = (RSAPrivateKey) factory.generatePrivate(new PKCS8EncodedKeySpec(
                    pem(properties.privateKeyLocation(), "PRIVATE KEY")));
            var publicKeys = new HashMap<String, RSAPublicKey>();
            for (var entry : properties.publicKeys().entrySet()) {
                var key = (RSAPublicKey) factory.generatePublic(new X509EncodedKeySpec(pem(entry.getValue(), "PUBLIC KEY")));
                if (key.getModulus().bitLength() < 2048) throw new IllegalArgumentException();
                publicKeys.put(entry.getKey(), key);
            }
            var activePublicKey = publicKeys.get(properties.activeKeyId());
            var signature = Signature.getInstance("SHA256withRSA");
            byte[] probe = "user-access-key-check".getBytes(StandardCharsets.US_ASCII);
            signature.initSign(privateKey); signature.update(probe);
            byte[] signed = signature.sign();
            signature.initVerify(activePublicKey); signature.update(probe);
            if (!signature.verify(signed)) throw new IllegalArgumentException();
            signer = new RSASSASigner(privateKey);
            var configured = new HashMap<String, NimbusJwtDecoder>();
            publicKeys.forEach((kid, key) -> {
                var decoder = NimbusJwtDecoder.withPublicKey(key).signatureAlgorithm(SignatureAlgorithm.RS256)
                        .validateType(false).build();
                decoder.setJwtValidator(this::validateClaims);
                configured.put(kid, decoder);
            });
            decoders = Map.copyOf(configured);
        } catch (Exception exception) {
            // Không đưa PEM, đường dẫn, exception gốc hoặc thông tin khóa vào log khởi động.
            throw new IllegalStateException("Không nạp được cặp khóa user access token hợp lệ");
        }
    }

    public String issue(int userId, UUID sessionId) {
        if (userId <= 0 || sessionId == null) throw new IllegalArgumentException("Danh tính token không hợp lệ");
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        var claims = Map.<String, Object>of("iss", properties.issuer(), "aud", List.of(properties.audience()),
                "sub", Integer.toString(userId), "sid", sessionId.toString(),
                "iat", now.getEpochSecond(), "exp", now.plusSeconds(3600).getEpochSecond());
        var header = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(properties.activeKeyId()).type(new JOSEObjectType("at+jwt")).build();
        // Nimbus JWTClaimsSet thu gọn aud một phần tử thành string; Payload giữ mảng theo hợp đồng.
        var jwt = new JWSObject(header, new Payload(claims));
        try { jwt.sign(signer); return jwt.serialize(); }
        catch (com.nimbusds.jose.JOSEException exception) { throw new IllegalStateException("Không ký được user access token"); }
    }

    public Jwt verify(String token) {
        try {
            var parsed = SignedJWT.parse(token);
            var header = parsed.getHeader();
            if (!JWSAlgorithm.RS256.equals(header.getAlgorithm())
                    || !new JOSEObjectType("at+jwt").equals(header.getType())
                    || header.getJWK() != null || header.getJWKURL() != null || header.getX509CertURL() != null)
                throw new IllegalArgumentException();
            var decoder = decoders.get(header.getKeyID());
            if (decoder == null) throw new IllegalArgumentException();
            // Kiểm tra kiểu JSON trước khi Nimbus/Spring chuyển đổi claim chuẩn.
            var raw = parsed.getPayload().toJSONObject();
            if (!(raw.get("iss") instanceof String) || !(raw.get("sub") instanceof String sub)
                    || !sub.matches("[1-9][0-9]*") || Integer.parseInt(sub) <= 0
                    || !(raw.get("sid") instanceof String sid) || !UUID.fromString(sid).toString().equals(sid)
                    || !(raw.get("aud") instanceof List<?> aud) || aud.isEmpty()
                    || aud.stream().anyMatch(value -> !(value instanceof String))
                    || !integer(raw.get("iat")) || !integer(raw.get("exp"))
                    || (raw.containsKey("nbf") && !integer(raw.get("nbf")))) throw new IllegalArgumentException();
            return decoder.decode(token);
        } catch (Exception exception) {
            throw new ApplicationException(ErrorCode.ACCESS_TOKEN_INVALID);
        }
    }

    private OAuth2TokenValidatorResult validateClaims(Jwt jwt) {
        Instant now = clock.instant();
        Instant issued = jwt.getIssuedAt(), expires = jwt.getExpiresAt(), notBefore = jwt.getNotBefore();
        if (!properties.issuer().equals(jwt.getClaimAsString("iss")) || !jwt.getAudience().contains(properties.audience())
                || issued == null || expires == null || !expires.isAfter(issued)
                || issued.isAfter(now.plusSeconds(30)) || !now.isBefore(expires.plusSeconds(30))
                || (notBefore != null && notBefore.isAfter(now.plusSeconds(30))))
            return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
        return OAuth2TokenValidatorResult.success();
    }

    private static boolean integer(Object value) { return value instanceof Long || value instanceof Integer; }

    private static byte[] pem(Resource resource, String type) throws Exception {
        String begin = "-----BEGIN " + type + "-----", end = "-----END " + type + "-----";
        String text;
        try (var input = resource.getInputStream()) { text = new String(input.readAllBytes(), StandardCharsets.US_ASCII).strip(); }
        if (!text.startsWith(begin) || !text.endsWith(end)) throw new IllegalArgumentException();
        return Base64.getDecoder().decode(text.substring(begin.length(), text.length() - end.length()).replaceAll("\\s", ""));
    }
}
