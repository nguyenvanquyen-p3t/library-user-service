package com.library.userservice.user.web;

import com.library.userservice.common.api.ApiResponse;
import com.library.userservice.common.api.SuccessCode;
import com.library.userservice.security.UserPrincipal;
import com.library.userservice.user.dto.ChangePasswordRequest;
import com.library.userservice.user.service.ChangePasswordService;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Hidden // Hợp đồng OpenAPI được duy trì trong YAML.
public class ChangePasswordController {
    private final ChangePasswordService passwords;

    public ChangePasswordController(ChangePasswordService passwords) { this.passwords = passwords; }

    @PutMapping(value = "/api/v1/users/me/password", consumes = "application/json", produces = "application/json")
    public ApiResponse<Void> change(@AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ChangePasswordRequest request) {
        passwords.changePassword(principal, request);
        return ApiResponse.success(SuccessCode.PASSWORD_CHANGE_SUCCESS, null);
    }
}
