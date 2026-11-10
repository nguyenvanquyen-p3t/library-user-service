package com.library.userservice.security;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Map;
import org.springframework.core.io.FileSystemResource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Khóa ngẫu nhiên chỉ cho test, không đọc hoặc thay khóa dự án. */
public abstract class UserSecurityTestKeys {
    protected static final KeyPair KEYS;
    protected static final Path PRIVATE_KEY;
    protected static final Path PUBLIC_KEY;
    static {
        try {
            var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
            KEYS = generator.generateKeyPair();
            Path directory = Files.createTempDirectory("user-service-test-keys-");
            directory.toFile().deleteOnExit();
            PRIVATE_KEY = write(directory.resolve("private.pem"), "PRIVATE KEY", KEYS.getPrivate().getEncoded());
            PUBLIC_KEY = write(directory.resolve("public.pem"), "PUBLIC KEY", KEYS.getPublic().getEncoded());
        } catch (Exception exception) { throw new IllegalStateException("Không tạo được khóa test"); }
    }

    private static Path write(Path file, String type, byte[] bytes) throws Exception {
        Files.writeString(file, "-----BEGIN " + type + "-----\n"
                + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(bytes) + "\n-----END " + type + "-----\n");
        file.toFile().deleteOnExit(); return file;
    }

    protected static UserAccessProperties properties() {
        return new UserAccessProperties("urn:library:user-service", "library-api", "user-access-v1",
                new FileSystemResource(PRIVATE_KEY), Map.of("user-access-v1", new FileSystemResource(PUBLIC_KEY)));
    }

    @DynamicPropertySource
    static void userAccessKeys(DynamicPropertyRegistry registry) {
        registry.add("app.security.user-access.private-key-location", () -> PRIVATE_KEY.toUri().toString());
        registry.add("app.security.user-access.public-keys.user-access-v1", () -> PUBLIC_KEY.toUri().toString());
    }
}
