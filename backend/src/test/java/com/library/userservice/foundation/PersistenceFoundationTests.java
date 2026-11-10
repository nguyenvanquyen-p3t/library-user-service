package com.library.userservice.foundation;

import com.library.userservice.auth.persistence.RefreshToken;
import com.library.userservice.auth.persistence.RefreshTokenRepository;
import com.library.userservice.auth.persistence.UserIdentity;
import com.library.userservice.auth.persistence.UserIdentityRepository;
import com.library.userservice.auth.persistence.VerificationCode;
import com.library.userservice.auth.persistence.VerificationCodeRepository;
import com.library.userservice.auth.persistence.VerificationPurpose;
import com.library.userservice.session.persistence.UserSession;
import com.library.userservice.session.persistence.UserSessionRepository;
import com.library.userservice.user.persistence.RoleRepository;
import com.library.userservice.user.persistence.User;
import com.library.userservice.user.persistence.UserRepository;
import com.library.userservice.user.persistence.UserRole;
import com.library.userservice.user.persistence.UserRoleId;
import com.library.userservice.user.persistence.UserRoleRepository;
import com.library.userservice.user.persistence.UserStatus;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;

/** Chỉ bật khi caller chọn rõ database test; mọi fixture được rollback. */
@SpringBootTest
@Transactional
@EnabledIfSystemProperty(named = "spring.datasource.url", matches = "jdbc:postgresql://[^/]+/UserDB_test(?:\\?.*)?")
class PersistenceFoundationTests {
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired UserRoleRepository userRoles;
    @Autowired UserIdentityRepository identities;
    @Autowired VerificationCodeRepository codes;
    @Autowired RefreshTokenRepository tokens;
    @Autowired UserSessionRepository sessions;

    @Test void mapsAllSevenTablesAndDatabaseGeneratedTimestamps() {
        var user = user();
        assertThat(user.getCreatedAt()).isNotNull();
        assertThat(user.getUpdatedAt()).isNotNull();
        var old = user.getUpdatedAt();
        user.setFullName("Tên đã cập nhật");
        em.flush();
        assertThat(user.getUpdatedAt()).isAfter(old);
        assertThat(jdbc.queryForObject("SELECT \"UpdatedAt\" FROM \"Users\" WHERE \"Id\" = ?", java.sql.Timestamp.class, user.getId()).toInstant())
                .isEqualTo(user.getUpdatedAt());
        var changed = user.getUpdatedAt();
        jdbc.update("UPDATE \"Users\" SET \"UpdatedAt\" = statement_timestamp() WHERE \"Id\" = ?", user.getId());
        em.refresh(user);
        assertThat(user.getUpdatedAt()).isEqualTo(changed);
        em.flush();
        assertThat(user.getUpdatedAt()).isEqualTo(changed);

        var reader = roles.findByName("READER").orElseThrow();
        userRoles.save(new UserRole(new UserRoleId(user.getId(), reader.getId())));
        var identity = new UserIdentity();
        identity.setUserId(user.getId()); identity.setProvider("GOOGLE"); identity.setProviderUserId("subject-" + UUID.randomUUID());
        identities.save(identity);
        var session = session(user, UUID.randomUUID(), Instant.now().plusSeconds(3600));
        var token = new RefreshToken();
        token.setUserId(user.getId()); token.setSessionId(session.getId()); token.setTokenHash("fixture-" + UUID.randomUUID());
        token.setExpiresAt(Instant.now().plusSeconds(600)); tokens.save(token);
        var code = new VerificationCode();
        code.setEmail(user.getEmail()); code.setPurpose(VerificationPurpose.REGISTER); code.setCodeHash("fixture-code");
        code.setExpiresAt(Instant.now().plusSeconds(600)); code.setAttemptCount(0); codes.save(code);
        em.flush(); em.clear();

        assertThat(users.findByUserName(user.getUserName())).isPresent();
        assertThat(users.findByEmail(user.getEmail()).orElseThrow().getPasswordHash()).isNull();
        assertThat(userRoles.findById(new UserRoleId(user.getId(), reader.getId()))).isPresent();
        assertThat(userRoles.findByIdUserId(user.getId())).hasSize(1);
        assertThat(identities.findByProviderAndProviderUserId("GOOGLE", identity.getProviderUserId())).isPresent();
        assertThat(codes.findByEmailAndPurposeOrderByCreatedAtDescIdDesc(user.getEmail(), VerificationPurpose.REGISTER)).hasSize(1);
        assertThat(tokens.findByTokenHash(token.getTokenHash()).orElseThrow().getSessionId()).isEqualTo(session.getId());
        var restored = sessions.findById(session.getId()).orElseThrow();
        assertThat(restored.getCreatedAt()).isNotNull();
        assertThat(restored.getLastSeenAt()).isEqualTo(restored.getCreatedAt());
        assertThat(restored.getClientType()).isNull();
        restored.setLastSeenAt(Instant.now()); em.flush();
        assertThat(restored.getLastSeenAt()).isAfterOrEqualTo(restored.getCreatedAt());
    }

    @Test void filtersAndOrdersActiveSessionsWithCallerTimeAndPagination() {
        var user = user();
        var a = session(user, UUID.fromString("00000000-0000-0000-0000-000000000001"), Instant.now().plusSeconds(3600));
        var b = session(user, UUID.fromString("00000000-0000-0000-0000-000000000002"), Instant.now().plusSeconds(3600));
        var revoked = session(user, UUID.randomUUID(), Instant.now().plusSeconds(3600));
        revoked.setRevokedAt(Instant.now()); revoked.setRevocationReason("LOGOUT");
        em.flush();
        var boundary = a.getExpiresAt();
        var first = sessions.findActiveByUserId(user.getId(), Instant.now(), PageRequest.of(0, 1));
        assertThat(first.getTotalElements()).isEqualTo(2);
        assertThat(first.getContent()).extracting(UserSession::getId).containsExactly(a.getId());
        assertThat(sessions.findActiveByUserId(user.getId(), Instant.now(), PageRequest.of(1, 1)).getContent())
                .extracting(UserSession::getId).containsExactly(b.getId());
        assertThat(sessions.findActiveByUserId(user.getId(), Instant.now(), PageRequest.of(9, 1)).getContent()).isEmpty();
        assertThat(sessions.findActiveByUserId(user.getId(), boundary.plusSeconds(60), PageRequest.of(0, 20)).getContent()).isEmpty();
        assertThat(sessions.findActiveByUserId(user().getId(), Instant.now(), PageRequest.of(0, 20)).getContent()).isEmpty();
    }

    @Test void rejectsTokenWithMismatchedSessionOwner() {
        var owner = user(); var other = user();
        var session = session(owner, UUID.randomUUID(), Instant.now().plusSeconds(3600));
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO "RefreshTokens" ("UserId", "SessionId", "TokenHash", "ExpiresAt")
                VALUES (?, ?, ?, CURRENT_TIMESTAMP + INTERVAL '1 hour')
                """, other.getId(), session.getId(), "fixture-" + UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test void rejectsRotationAcrossSessions() {
        var user = user();
        var a = session(user, UUID.randomUUID(), Instant.now().plusSeconds(3600));
        var b = session(user, UUID.randomUUID(), Instant.now().plusSeconds(3600));
        var first = token(user, a); var second = token(user, b); em.flush();
        assertThatThrownBy(() -> jdbc.update("""
                UPDATE "RefreshTokens" SET "RevokedAt" = statement_timestamp(), "ReplacedByTokenId" = ? WHERE "Id" = ?
                """, second.getId(), first.getId())).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test void tokenSessionCannotBeReassignedByOrmOrDatabase() {
        var user = user();
        var a = session(user, UUID.randomUUID(), Instant.now().plusSeconds(3600));
        var b = session(user, UUID.randomUUID(), Instant.now().plusSeconds(3600));
        var token = token(user, a); em.flush();
        assertThatThrownBy(() -> token.setSessionId(b.getId())).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE \"RefreshTokens\" SET \"SessionId\" = ? WHERE \"Id\" = ?", b.getId(), token.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private User user() {
        var user = new User();
        user.setUserName("fixture-" + UUID.randomUUID()); user.setEmail(user.getUserName() + "@example.com");
        user.setFullName("Fixture"); user.setStatus(UserStatus.ACTIVE);
        users.save(user); em.flush(); return user;
    }
    private UserSession session(User user, UUID id, Instant expiresAt) {
        var session = new UserSession(); session.setId(id); session.setUserId(user.getId()); session.setExpiresAt(expiresAt);
        em.persist(session); em.flush(); return session;
    }
    private RefreshToken token(User user, UserSession session) {
        var token = new RefreshToken(); token.setUserId(user.getId()); token.setSessionId(session.getId());
        token.setTokenHash("fixture-" + UUID.randomUUID()); token.setExpiresAt(Instant.now().plusSeconds(600));
        return tokens.save(token);
    }
}
