package com.library.userservice.auth.web;

import com.library.userservice.auth.dto.OtpRequestResult;
import com.library.userservice.auth.dto.RegisterCodeRequest;
import com.library.userservice.auth.dto.RegisterRequest;
import com.library.userservice.auth.service.RegistrationService;
import com.library.userservice.common.api.ApiResponse;
import com.library.userservice.common.api.SuccessCode;
import com.library.userservice.user.dto.UserProfile;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Hidden
public class RegistrationController {
    public static final String REGISTER_PATH = "/api/v1/auth/register";
    public static final String REQUEST_CODE_PATH = REGISTER_PATH + "/request-code";
    private final RegistrationService registration;

    public RegistrationController(RegistrationService registration) {
        this.registration = registration;
    }

    @PostMapping(value = REQUEST_CODE_PATH, consumes = "application/json", produces = "application/json")
    public ApiResponse<OtpRequestResult> requestCode(@Valid @RequestBody RegisterCodeRequest request) {
        return ApiResponse.success(SuccessCode.REGISTER_CODE_SENT, registration.requestCode(request));
    }

    @PostMapping(value = REGISTER_PATH, consumes = "application/json", produces = "application/json")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserProfile> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.success(SuccessCode.REGISTER_SUCCESS, registration.register(request));
    }
}
