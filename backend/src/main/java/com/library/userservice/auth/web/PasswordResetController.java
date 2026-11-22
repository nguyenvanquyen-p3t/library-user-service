package com.library.userservice.auth.web;

import com.library.userservice.auth.dto.ForgotPasswordCodeRequest;
import com.library.userservice.auth.dto.OtpRequestResult;
import com.library.userservice.auth.dto.ResetPasswordRequest;
import com.library.userservice.auth.service.PasswordResetService;
import com.library.userservice.common.api.ApiResponse;
import com.library.userservice.common.api.SuccessCode;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Hidden
public class PasswordResetController {
    public static final String REQUEST_CODE_PATH = "/api/v1/auth/forgot-password/request-code";
    public static final String RESET_PATH = "/api/v1/auth/reset-password";

    private final PasswordResetService passwordReset;

    public PasswordResetController(PasswordResetService passwordReset) {
        this.passwordReset = passwordReset;
    }

    @PostMapping(value = REQUEST_CODE_PATH, consumes = "application/json", produces = "application/json")
    public ApiResponse<OtpRequestResult> requestCode(@Valid @RequestBody ForgotPasswordCodeRequest request) {
        return ApiResponse.success(SuccessCode.PASSWORD_RESET_CODE_SENT, passwordReset.requestCode(request));
    }

    @PostMapping(value = RESET_PATH, consumes = "application/json", produces = "application/json")
    public ApiResponse<Void> reset(@Valid @RequestBody ResetPasswordRequest request) {
        passwordReset.reset(request);
        return ApiResponse.success(SuccessCode.PASSWORD_RESET_SUCCESS, null);
    }
}
