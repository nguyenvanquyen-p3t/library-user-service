package com.library.userservice.auth.web;

import com.library.userservice.auth.dto.GoogleRegisterRequest;
import com.library.userservice.auth.dto.GoogleRegistrationResult;
import com.library.userservice.auth.dto.GoogleRequest;
import com.library.userservice.auth.service.GoogleAuthService;
import com.library.userservice.common.api.ApiResponse;
import com.library.userservice.common.api.SuccessCode;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Hidden
public class GoogleAuthController {
    public static final String GOOGLE_PATH = "/api/v1/auth/google";
    public static final String REGISTER_PATH = GOOGLE_PATH + "/register";
    private final GoogleAuthService google;

    public GoogleAuthController(GoogleAuthService google) {
        this.google = google;
    }

    @PostMapping(value = GOOGLE_PATH, consumes = "application/json", produces = "application/json")
    public ApiResponse<?> authenticate(@Valid @RequestBody GoogleRequest request) {
        return google.authenticate(request);
    }

    @PostMapping(value = REGISTER_PATH, consumes = "application/json", produces = "application/json")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<GoogleRegistrationResult> register(@Valid @RequestBody GoogleRegisterRequest request) {
        return ApiResponse.success(SuccessCode.GOOGLE_REGISTER_SUCCESS, google.register(request));
    }
}
