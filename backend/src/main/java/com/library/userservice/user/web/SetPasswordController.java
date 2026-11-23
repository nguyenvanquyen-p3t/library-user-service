package com.library.userservice.user.web;

import com.library.userservice.auth.dto.OtpRequestResult;
import com.library.userservice.auth.service.SetPasswordService;
import com.library.userservice.common.api.ApiResponse;
import com.library.userservice.common.api.SuccessCode;
import com.library.userservice.security.UserPrincipal;
import com.library.userservice.user.dto.SetPasswordRequest;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Hidden
public class SetPasswordController {
    public static final String REQUEST_CODE_PATH = "/api/v1/users/me/password/request-code";
    public static final String SET_PASSWORD_PATH = "/api/v1/users/me/password/set";

    private final SetPasswordService passwords;

    public SetPasswordController(SetPasswordService passwords) {
        this.passwords = passwords;
    }

    @PostMapping(value = REQUEST_CODE_PATH, produces = "application/json")
    public ApiResponse<OtpRequestResult> requestCode(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(SuccessCode.SET_PASSWORD_CODE_SENT, passwords.requestCode(principal));
    }

    @PostMapping(value = SET_PASSWORD_PATH, consumes = "application/json", produces = "application/json")
    public ApiResponse<Void> setPassword(@AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody SetPasswordRequest request) {
        passwords.setPassword(principal, request);
        return ApiResponse.success(SuccessCode.PASSWORD_SET_SUCCESS, null);
    }
}
