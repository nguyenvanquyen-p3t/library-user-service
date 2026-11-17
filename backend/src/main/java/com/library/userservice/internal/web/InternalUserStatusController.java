package com.library.userservice.internal.web;

import com.library.userservice.common.api.ApiResponse;
import com.library.userservice.common.api.SuccessCode;
import com.library.userservice.internal.dto.UserStatusRequest;
import com.library.userservice.internal.dto.UserStatusResult;
import com.library.userservice.internal.service.InternalUserStatusService;
import com.library.userservice.security.AdminActor;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Hidden
public class InternalUserStatusController {
    public static final String PATH = "/api/v1/internal/users/{id}/status";
    private final InternalUserStatusService users;
    public InternalUserStatusController(InternalUserStatusService users) { this.users = users; }

    @PatchMapping(value = PATH, consumes = "application/json", produces = "application/json")
    public ApiResponse<UserStatusResult> update(@AuthenticationPrincipal AdminActor actor,
            @PathVariable @Min(value = 1, message = "OUT_OF_RANGE") int id, @RequestBody @Valid UserStatusRequest request) {
        return ApiResponse.success(SuccessCode.USER_STATUS_UPDATE_SUCCESS, users.updateStatus(actor, id, request));
    }
}
