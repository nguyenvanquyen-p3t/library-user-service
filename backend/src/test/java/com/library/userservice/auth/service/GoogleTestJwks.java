package com.library.userservice.auth.service;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

final class GoogleTestJwks implements AutoCloseable {
    static final String AUDIENCE = "library-test-web";
    static final String PRESENTER = "library-test-android";
    static final RSAKey INITIAL = key("test-google-initial");
    static final RSAKey ROTATED = key("test-google-rotated");

    private final HttpServer server;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    volatile RSAKey served = INITIAL;
    volatile int responseStatus = 200;
    final CountDownLatch releaseResponse = new CountDownLatch(1);
    volatile boolean delayResponse;

    GoogleTestJwks() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(executor);
        server.createContext("/jwks", exchange -> {
            try (exchange) {
                if (delayResponse) releaseResponse.await();
                var body = new JWKSet(served.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(responseStatus, body.length);
                exchange.getResponseBody().write(body);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        });
        server.start();
    }

    String uri() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/jwks";
    }

    static String token(RSAKey key, String kid, Instant now, Consumer<JWTClaimsSet.Builder> claims) throws Exception {
        var builder = new JWTClaimsSet.Builder().issuer("https://accounts.google.com").audience(AUDIENCE)
                .subject("google-test-user").issueTime(Date.from(now.minusSeconds(60)))
                .expirationTime(Date.from(now.plusSeconds(3600)));
        claims.accept(builder);
        var signed = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).type(JOSEObjectType.JWT)
                .keyID(kid).build(), builder.build());
        signed.sign(new RSASSASigner(key));
        return signed.serialize();
    }

    private static RSAKey key(String id) {
        try {
            return new RSAKeyGenerator(2048).keyID(id).generate();
        } catch (com.nimbusds.jose.JOSEException exception) {
            throw new IllegalStateException("Không tạo được khóa Google cho test");
        }
    }

    @Override
    public void close() {
        releaseResponse.countDown();
        server.stop(0);
        executor.close();
    }
}
