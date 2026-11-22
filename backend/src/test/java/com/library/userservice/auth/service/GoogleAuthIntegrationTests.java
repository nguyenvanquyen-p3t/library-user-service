package com.library.userservice.auth.service;

import static com.library.userservice.auth.service.GoogleTestJwks.*;
import static com.library.userservice.auth.web.GoogleAuthController.GOOGLE_PATH;
import static com.library.userservice.auth.web.GoogleAuthController.REGISTER_PATH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

import com.library.userservice.security.PasswordService;
import com.library.userservice.security.UserSecurityTestKeys;
import com.nimbusds.jwt.JWTClaimsSet;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.config.import=", "debug=false",
        "app.google.registration-hmac-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
})
@EnabledIfSystemProperty(named = "spring.datasource.url", matches = "jdbc:postgresql://[^/]+/UserDB_test(?:\\?.*)?")
@Import(GoogleAuthIntegrationTests.FakeGoogle.class)
class GoogleAuthIntegrationTests extends UserSecurityTestKeys {
    @TestConfiguration(proxyBeanMethods = false)
    static class FakeGoogle {
        @Bean(destroyMethod = "close")
        GoogleTestJwks testJwks() throws Exception { return new GoogleTestJwks(); }

        @Bean
        @Primary
        GoogleIdTokenVerifier testGoogleVerifier(Clock clock, GoogleTestJwks jwks) {
            return new GoogleIdTokenVerifier(clock, AUDIENCE, PRESENTER, jwks.uri(), Duration.ofSeconds(3));
        }
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired JsonMapper mapper;
    @Autowired PasswordService passwords;
    @Autowired TransactionTemplate transaction;
    @Autowired GoogleTestJwks jwks;
    @MockitoSpyBean LoginSessionIssuer sessionIssuer;
    @LocalServerPort int port;

    private final HttpClient client = HttpClient.newHttpClient();
    private final String prefix = "uc08-" + UUID.randomUUID();
    private final List<String> fixtureEmails = new ArrayList<>();

    @AfterEach void cleanup() {
        for (String email : fixtureEmails) jdbc.update("DELETE FROM \"Users\" WHERE \"Email\" = ?", email);
    }

    @Test void registersNewUserAndAcceptsItsTokensInExistingApis() throws Exception {
        String email = fixtureEmail("-new", "example.com");
        String id = googleId("new", email.toUpperCase(java.util.Locale.ROOT), claims -> claims.claim("name", "  "));
        var begin = assertCode(authenticate(id), 200, "GOOGLE_REGISTRATION_REQUIRED");
        assertThat(begin.path("email").asText()).isEqualTo(email);
        assertThat(begin.path("fullName").isNull()).isTrue();
        assertThat(begin.path("suggestedUserName").asText()).isEqualTo(prefix + "-new");
        assertThat(begin.path("isNewUser").asBoolean()).isTrue();
        assertThat(begin.path("accountLinked").asBoolean()).isFalse();
        assertThat(begin.has("accessToken")).isFalse();
        assertThat(userCount(email)).isZero();

        String registration = begin.path("registrationToken").asText();
        var registered = assertCode(register(registration, " " + prefix + " ", "  Reader Name  "), 201, "GOOGLE_REGISTER_SUCCESS");
        var user = registered.path("user");
        assertThat(user.path("email").asText()).isEqualTo(email);
        assertThat(user.path("userName").asText()).isEqualTo(prefix);
        assertThat(user.path("fullName").asText()).isEqualTo("Reader Name");
        assertThat(user.path("phoneNumber").isNull()).isTrue();
        assertThat(user.path("status").asText()).isEqualTo("ACTIVE");
        assertThat(user.path("roles").get(0).asText()).isEqualTo("READER");
        assertThat(user.path("linkedProviders").get(0).asText()).isEqualTo("GOOGLE");
        assertThat(user.path("hasPassword").asBoolean()).isFalse();
        int userId = user.path("id").asInt();
        assertThat(jdbc.queryForObject("SELECT \"PasswordHash\" FROM \"Users\" WHERE \"Id\" = ?", String.class, userId)).isNull();
        assertThat(count("UserSessions", userId)).isEqualTo(1);
        assertThat(count("RefreshTokens", userId)).isEqualTo(1);

        var pair = registered.path("token");
        assertCode(request("/api/v1/users/me", "GET", null, pair.path("accessToken").asText()), 200, "PROFILE_GET_SUCCESS");
        var rotated = assertCode(post("/api/v1/auth/refresh", Map.of("refreshToken", pair.path("refreshToken").asText())),
                200, "TOKEN_REFRESH_SUCCESS");
        assertCode(post("/api/v1/auth/logout", Map.of("refreshToken", rotated.path("refreshToken").asText())),
                200, "LOGOUT_SUCCESS");
        assertCode(request("/api/v1/users/me", "GET", null, rotated.path("accessToken").asText()), 401, "SESSION_INACTIVE");
        assertCode(register(registration, prefix, "Reader Name"), 409, "GOOGLE_IDENTITY_ALREADY_REGISTERED");
        assertThat(count("UserSessions", userId)).isEqualTo(1);

        var login = assertCode(authenticate(googleId("new", "different@example.com",
                claims -> claims.claim("email_verified", false))), 200, "GOOGLE_LOGIN_SUCCESS");
        assertThat(login.path("isNewUser").asBoolean()).isFalse();
        assertThat(login.path("accountLinked").asBoolean()).isFalse();
        assertThat(count("UserSessions", userId)).isEqualTo(2);
    }

    @Test void linksGmailAndWorkspaceWhilePreservingPasswordAndRoles() throws Exception {
        String gmail = fixtureEmail("-gmail", "gmail.com");
        String workspace = fixtureEmail("-hosted", "example.com");
        String originalHash = passwords.encode("existing-password");
        int gmailUser = createLocal(prefix + "-gmail", gmail, originalHash, "ACTIVE");
        int workspaceUser = createLocal(prefix + "-hosted", workspace, originalHash, "ACTIVE");
        jdbc.update("""
                INSERT INTO "UserRoles" ("UserId", "RoleId")
                SELECT ?, "Id" FROM "Roles" WHERE "Name" = 'LIBRARIAN'
                """, gmailUser);
        var linked = assertCode(authenticate(googleId("gmail", gmail, claims -> {})), 200, "GOOGLE_ACCOUNT_LINKED");
        assertThat(linked.path("isNewUser").asBoolean()).isFalse();
        assertThat(linked.path("accountLinked").asBoolean()).isTrue();
        assertCode(authenticate(googleId("hosted", workspace, claims -> claims.claim("hd", "example.com"))),
                200, "GOOGLE_ACCOUNT_LINKED");
        assertThat(count("UserIdentities", gmailUser)).isEqualTo(1);
        assertThat(count("UserIdentities", workspaceUser)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT \"PasswordHash\" FROM \"Users\" WHERE \"Id\" = ?", String.class, gmailUser))
                .isEqualTo(originalHash);
        assertThat(jdbc.queryForObject("""
                SELECT r."Name" FROM "Roles" r JOIN "UserRoles" ur ON r."Id" = ur."RoleId" WHERE ur."UserId" = ?
                """, String.class, gmailUser)).isEqualTo("LIBRARIAN");
        assertCode(post("/api/v1/auth/login", Map.of("identifier", gmail, "password", "existing-password")),
                200, "LOGIN_SUCCESS");
    }

    @Test void rejectsNonAuthoritativeUnverifiedAndConflictingIdentitiesAndBannedUsers() throws Exception {
        String thirdParty = fixtureEmail("-third", "example.com");
        String gmail = fixtureEmail("-gmail", "gmail.com");
        String hash = passwords.encode("existing-password");
        int thirdUser = createLocal(prefix + "-third", thirdParty, hash, "ACTIVE");
        int gmailUser = createLocal(prefix + "-gmail", gmail, hash, "BANNED");
        assertCode(authenticate(googleId("third", thirdParty, claims -> {})), 401, "GOOGLE_TOKEN_INVALID");
        assertCode(authenticate(googleId("unverified", gmail, claims -> claims.claim("email_verified", false))),
                401, "GOOGLE_TOKEN_INVALID");
        assertCode(authenticate(googleId("banned", gmail, claims -> {})), 403, "ACCOUNT_BANNED");
        jdbc.update("""
                INSERT INTO "UserIdentities" ("UserId", "Provider", "ProviderUserId") VALUES (?, 'GOOGLE', ?)
                """, gmailUser, prefix + "-linked");
        assertCode(authenticate(googleId("linked", gmail, claims -> {})), 403, "ACCOUNT_BANNED");
        assertCode(authenticate(googleId("other", gmail, claims -> {})), 401, "GOOGLE_TOKEN_INVALID");
        assertThat(count("UserIdentities", thirdUser)).isZero();
        assertThat(count("UserIdentities", gmailUser)).isEqualTo(1);
        assertThat(count("UserSessions", thirdUser)).isZero();
        assertThat(count("UserSessions", gmailUser)).isZero();
    }

    @Test void detectsEmailAndUsernameConflictsAndAllowsCorrectedData() throws Exception {
        String email = fixtureEmail("-new", "example.com");
        String takenEmail = fixtureEmail("-used", "example.com");
        createLocal(prefix + "-used", takenEmail, null, "ACTIVE");
        String registration = begin("new", email);
        assertCode(register(registration, prefix + "-used", "Reader Name"), 409, "USERNAME_ALREADY_EXISTS");
        assertThat(userCount(email)).isZero();
        assertCode(register(registration, prefix, "Reader Name"), 201, "GOOGLE_REGISTER_SUCCESS");

        String racedEmail = fixtureEmail("-raced", "example.com");
        String racedRegistration = begin("raced", racedEmail);
        int existing = createLocal(prefix + "-raced", racedEmail, null, "ACTIVE");
        assertCode(register(racedRegistration, prefix + "-spare", "Reader Name"), 409, "EMAIL_ALREADY_EXISTS");
        assertThat(count("UserIdentities", existing)).isZero();
        assertThat(count("UserSessions", existing)).isZero();
    }

    @Test void admitsOnlyOneConcurrentRegistrationAndOneIdentityPerLocalUser() throws Exception {
        String email = fixtureEmail("-new", "example.com");
        String registration = begin("new", email);
        String gmail = fixtureEmail("-gmail", "gmail.com");
        int local = createLocal(prefix + "-gmail", gmail, passwords.encode("existing-password"), "ACTIVE");
        String firstId = googleId("first", gmail, claims -> {});
        String secondId = googleId("second", gmail, claims -> {});
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var start = new CountDownLatch(1);
            var first = executor.submit(() -> { await(start); return register(registration, prefix, "Reader Name"); });
            var second = executor.submit(() -> { await(start); return register(registration, prefix, "Reader Name"); });
            start.countDown();
            var results = List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
            assertThat(results.stream().map(HttpResponse::statusCode)).containsExactlyInAnyOrder(201, 409);
            assertCode(results.stream().filter(result -> result.statusCode() == 409).findFirst().orElseThrow(),
                    409, "GOOGLE_IDENTITY_ALREADY_REGISTERED");
            int created = jdbc.queryForObject("SELECT \"Id\" FROM \"Users\" WHERE \"Email\" = ?", Integer.class, email);
            assertThat(count("UserIdentities", created)).isEqualTo(1);
            assertThat(count("UserSessions", created)).isEqualTo(1);

            var linkStart = new CountDownLatch(1);
            var linkFirst = executor.submit(() -> { await(linkStart); return authenticate(firstId); });
            var linkSecond = executor.submit(() -> { await(linkStart); return authenticate(secondId); });
            linkStart.countDown();
            var links = List.of(linkFirst.get(15, TimeUnit.SECONDS), linkSecond.get(15, TimeUnit.SECONDS));
            assertThat(links.stream().map(HttpResponse::statusCode)).containsExactlyInAnyOrder(200, 401);
            assertThat(count("UserIdentities", local)).isEqualTo(1);
            assertThat(count("UserSessions", local)).isEqualTo(1);
        }
    }

    @Test void checksAccountStatusAgainAfterWaitingForTheUserLock() throws Exception {
        String gmail = fixtureEmail("-gmail", "gmail.com");
        int user = createLocal(prefix, gmail, passwords.encode("existing-password"), "ACTIVE");
        String id = googleId("waiting", gmail, claims -> {});
        var changed = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var lock = executor.submit(() -> transaction.executeWithoutResult(status -> {
                jdbc.update("UPDATE \"Users\" SET \"Status\" = 'BANNED' WHERE \"Id\" = ?", user);
                changed.countDown();
                await(release);
            }));
            try {
                await(changed);
                var pending = executor.submit(() -> authenticate(id));
                assertUserLockWaits();
                release.countDown();
                lock.get(15, TimeUnit.SECONDS);
                assertCode(pending.get(15, TimeUnit.SECONDS), 403, "ACCOUNT_BANNED");
            } finally {
                release.countDown();
            }
        }
        assertThat(count("UserIdentities", user)).isZero();
        assertThat(count("UserSessions", user)).isZero();
    }

    @Test void rollsBackRegistrationOnSessionOrCommitFailureAndAllowsRetry() throws Exception {
        String email = fixtureEmail("-new", "example.com");
        String registration = begin("new", email);
        doThrow(new IllegalStateException("Lỗi tạo phiên kiểm thử")).when(sessionIssuer).issue(anyInt());
        assertCode(register(registration, prefix, "Reader Name"), 500, "INTERNAL_ERROR");
        assertThat(userCount(email)).isZero();
        assertNoIdentity("new");
        reset(sessionIssuer);

        doAnswer(call -> {
            var pair = call.callRealMethod();
            jdbc.execute("""
                    CREATE TEMP TABLE uc08_commit_fixture
                        (id integer UNIQUE DEFERRABLE INITIALLY DEFERRED) ON COMMIT DROP
                    """);
            jdbc.update("INSERT INTO uc08_commit_fixture VALUES (1), (1)");
            return pair;
        }).when(sessionIssuer).issue(anyInt());
        assertCode(register(registration, prefix, "Reader Name"), 500, "INTERNAL_ERROR");
        assertThat(userCount(email)).isZero();
        assertNoIdentity("new");
        reset(sessionIssuer);

        assertCode(register(registration, prefix, "Reader Name"), 201, "GOOGLE_REGISTER_SUCCESS");
        assertThat(userCount(email)).isEqualTo(1);
    }

    @Test void exposesOnlyTwoPublicRoutesAndUsesTheSharedErrors() throws Exception {
        assertCode(post(GOOGLE_PATH, Map.of()), 400, "VALIDATION_ERROR");
        assertCode(post(REGISTER_PATH, Map.of("registrationToken", "invalid", "userName", prefix, "fullName", "Name",
                "email", "client@example.com")), 400, "VALIDATION_ERROR");
        assertCode(request(GOOGLE_PATH, "GET", null, "stale"), 405, "METHOD_NOT_ALLOWED");
        assertCode(request(GOOGLE_PATH, "POST", HttpRequest.BodyPublishers.ofString("idToken=ignored"), "stale"),
                415, "UNSUPPORTED_MEDIA_TYPE");
        assertCode(request(GOOGLE_PATH + "/other", "GET", null, null), 401, "AUTHENTICATION_REQUIRED");
        assertCode(authenticate("invalid"), 401, "GOOGLE_TOKEN_INVALID");
        assertCode(register("invalid", prefix, "Reader Name"), 401, "REGISTRATION_TOKEN_INVALID");

        String unknownKey = token(ROTATED, ROTATED.getKeyID(), Instant.now(), claims -> {});
        jwks.responseStatus = 503;
        try {
            assertCode(authenticate(unknownKey), 503, "SERVICE_UNAVAILABLE");
        } finally {
            jwks.responseStatus = 200;
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }

    private void assertUserLockWaits() throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            int waiting = jdbc.queryForObject("""
                    SELECT count(*) FROM pg_stat_activity WHERE datname = current_database()
                    AND pid <> pg_backend_pid() AND wait_event_type = 'Lock'
                    AND query LIKE '%"Users"%for%update%'
                    """, Integer.class);
            if (waiting > 0) return;
            Thread.sleep(25);
        }
        throw new AssertionError("Google login không chờ khóa user");
    }

    private int createLocal(String userName, String email, String hash, String status) {
        return jdbc.queryForObject("""
                INSERT INTO "Users" ("UserName", "Email", "FullName", "PasswordHash", "Status")
                VALUES (?, ?, 'Local Reader', ?, ?) RETURNING "Id"
                """, Integer.class, userName, email, hash, status);
    }

    private String fixtureEmail(String suffix, String domain) {
        String email = prefix + suffix + "@" + domain;
        fixtureEmails.add(email);
        return email;
    }

    private int userCount(String email) {
        return jdbc.queryForObject("SELECT count(*) FROM \"Users\" WHERE \"Email\" = ?", Integer.class, email);
    }

    private int count(String table, int user) {
        return jdbc.queryForObject("SELECT count(*) FROM \"" + table + "\" WHERE \"UserId\" = ?", Integer.class, user);
    }

    private void assertNoIdentity(String subjectSuffix) {
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM "UserIdentities" WHERE "Provider" = 'GOOGLE' AND "ProviderUserId" = ?
                """, Integer.class, prefix + "-" + subjectSuffix)).isZero();
    }

    private String googleId(String subjectSuffix, String email, Consumer<JWTClaimsSet.Builder> extra) throws Exception {
        return token(INITIAL, INITIAL.getKeyID(), Instant.now(), claims -> {
            claims.subject(prefix + "-" + subjectSuffix).claim("email", email).claim("email_verified", true);
            extra.accept(claims);
        });
    }

    private String begin(String subjectSuffix, String email) throws Exception {
        return assertCode(authenticate(googleId(subjectSuffix, email, claims -> {})), 200, "GOOGLE_REGISTRATION_REQUIRED")
                .path("registrationToken").asText();
    }

    private HttpResponse<String> authenticate(String idToken) throws Exception {
        return request(GOOGLE_PATH, "POST", Map.of("idToken", idToken), "stale");
    }

    private HttpResponse<String> register(String registration, String userName, String fullName) throws Exception {
        return post(REGISTER_PATH, Map.of("registrationToken", registration, "userName", userName, "fullName", fullName));
    }

    private HttpResponse<String> post(String path, Map<String, String> body) throws Exception {
        return request(path, "POST", body, null);
    }

    private HttpResponse<String> request(String path, String method, Object body, String bearer) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(12));
        if (bearer != null) builder.header("Authorization", "Bearer " + bearer);
        if (body instanceof HttpRequest.BodyPublisher raw)
            builder.header("Content-Type", "text/plain").method(method, raw);
        else if (body != null)
            builder.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
        else builder.method(method, HttpRequest.BodyPublishers.noBody());
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode assertCode(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).as("HTTP status for %s", code).isEqualTo(status);
        var root = mapper.readTree(response.body());
        assertThat(root.path("messageCode").asText()).isEqualTo(code);
        String path = response.uri().getPath();
        if (GOOGLE_PATH.equals(path) || REGISTER_PATH.equals(path))
            assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
        return root.path("data");
    }
}
