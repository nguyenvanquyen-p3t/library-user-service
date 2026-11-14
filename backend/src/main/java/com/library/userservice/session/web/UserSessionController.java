package com.library.userservice.session.web;

import com.library.userservice.common.api.ApiResponse;
import com.library.userservice.common.api.SuccessCode;
import com.library.userservice.security.UserPrincipal;
import com.library.userservice.session.dto.RevokeOthersResult;
import com.library.userservice.session.dto.SessionPage;
import com.library.userservice.session.dto.SessionQuery;
import com.library.userservice.session.service.UserSessionService;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Hidden // YAML tiếp tục là nguồn hợp đồng OpenAPI.
public class UserSessionController {
    private final UserSessionService sessions;
    public UserSessionController(UserSessionService sessions) { this.sessions = sessions; }

    @GetMapping(value = "/api/v1/users/me/sessions", produces = "application/json")
    public ApiResponse<SessionPage> list(@AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "OUT_OF_RANGE") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "OUT_OF_RANGE")
            @Max(value = 100, message = "OUT_OF_RANGE") int size) {
        return ApiResponse.success(SuccessCode.SESSION_LIST_SUCCESS, sessions.list(principal, new SessionQuery(page, size)));
    }

    @DeleteMapping(value = "/api/v1/users/me/sessions/{sessionId}", produces = "application/json")
    public ApiResponse<Void> revoke(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable @Pattern(regexp = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}",
                    message = "INVALID_FORMAT") String sessionId) {
        sessions.revoke(principal, UUID.fromString(sessionId));
        return ApiResponse.success(SuccessCode.SESSION_REVOKE_SUCCESS, null);
    }

    @PostMapping(value = "/api/v1/users/me/sessions/revoke-others", produces = "application/json")
    public ApiResponse<RevokeOthersResult> revokeOthers(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(SuccessCode.OTHER_SESSIONS_REVOKED, sessions.revokeOthers(principal));
    }
}
