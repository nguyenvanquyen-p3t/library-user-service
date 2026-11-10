package com.library.userservice.auth.web;

import com.library.userservice.auth.dto.LoginRequest;
import com.library.userservice.auth.dto.TokenPair;
import com.library.userservice.auth.service.LoginService;
import com.library.userservice.common.api.ApiResponse;
import com.library.userservice.common.api.SuccessCode;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Hidden // YAML tiếp tục là hợp đồng duy nhất; tránh springdoc ghi đè operation đã xác nhận.
public class LoginController {
    private final LoginService login;
    public LoginController(LoginService login) { this.login = login; }

    @PostMapping(value = "/api/v1/auth/login", consumes = "application/json", produces = "application/json")
    public ResponseEntity<ApiResponse<TokenPair>> login(@Valid @RequestBody LoginRequest request) {
        var tokens = login.login(request);
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(ApiResponse.success(SuccessCode.LOGIN_SUCCESS, tokens));
    }
}
