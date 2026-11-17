package com.library.userservice.internal.service;

import com.library.userservice.auth.persistence.RefreshTokenRepository;
import com.library.userservice.common.api.ErrorCode;
import com.library.userservice.common.exception.ApplicationException;
import com.library.userservice.internal.dto.UserStatusRequest;
import com.library.userservice.internal.dto.UserStatusResult;
import com.library.userservice.security.AdminActor;
import com.library.userservice.security.AdminActorAuthenticator;
import com.library.userservice.session.persistence.UserSessionRepository;
import com.library.userservice.user.persistence.RoleRepository;
import com.library.userservice.user.persistence.User;
import com.library.userservice.user.persistence.UserRepository;
import com.library.userservice.user.persistence.UserStatus;
import java.time.Clock;
import java.util.HashMap;
import java.util.UUID;
import java.util.stream.IntStream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class InternalUserStatusService {
    private final UserRepository users;
    private final RoleRepository roles;
    private final UserSessionRepository sessions;
    private final RefreshTokenRepository tokens;
    private final AdminActorAuthenticator actors;
    private final UserStatusAudit audit;
    private final Clock clock;

    public InternalUserStatusService(UserRepository users, RoleRepository roles, UserSessionRepository sessions,
            RefreshTokenRepository tokens, AdminActorAuthenticator actors, UserStatusAudit audit, Clock clock) {
        this.users = users; this.roles = roles; this.sessions = sessions; this.tokens = tokens;
        this.actors = actors; this.audit = audit; this.clock = clock;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public UserStatusResult updateStatus(AdminActor actor, int id, UserStatusRequest request) {
        var locked = new HashMap<Integer, User>();
        // Không đọc entity trước khóa; thứ tự chung tránh deadlock khi hai ADMIN thao tác chéo.
        IntStream.of(actor.userId(), id).distinct().sorted().forEach(userId ->
                users.findByIdForUpdate(userId).ifPresent(user -> locked.put(userId, user)));
        var now = clock.instant();
        actors.authenticateAt(actor.accessToken(), now);
        var target = locked.get(id);
        if (target == null) throw new ApplicationException(ErrorCode.USER_NOT_FOUND);
        if (request.status() == UserStatus.BANNED && roles.findNamesByUserId(id).contains("ADMIN"))
            throw new ApplicationException(ErrorCode.ACCESS_DENIED);
        var before = target.getStatus();
        if (before != request.status()) {
            target.setStatus(request.status());
            users.save(target);
            if (request.status() == UserStatus.BANNED) {
                tokens.revokeActiveByUser(id, now);
                sessions.revokeActiveByUser(id, now, "ACCOUNT_BANNED");
            }
        }
        var result = new UserStatusResult(id, target.getStatus());
        var requestId = UUID.randomUUID();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                // Log ngoài database không nguyên tử/bền vững; lỗi log không đảo kết quả commit.
                try { audit.record(requestId, actor.userId(), id, before, result.status(), now); }
                catch (RuntimeException ignored) { /* Không đưa exception chứa thông tin nội bộ ra response. */ }
            }
        });
        return result;
    }
}
