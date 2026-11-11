package com.library.userservice.auth.web;

import com.library.userservice.auth.dto.RefreshTokenRequest;
import com.library.userservice.auth.service.LogoutService;
import com.library.userservice.common.api.ApiResponse;
import com.library.userservice.common.api.SuccessCode;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Hidden // Hợp đồng OpenAPI được duy trì trong YAML.
public class LogoutController {
    private final LogoutService logout;

    public LogoutController(LogoutService logout) { this.logout = logout; }

    @PostMapping(value = "/api/v1/auth/logout", consumes = "application/json", produces = "application/json")
    public ResponseEntity<ApiResponse<Void>> logout(@Valid @RequestBody RefreshTokenRequest request) {
        logout.logout(request);
        return ResponseEntity.ok().header("Cache-Control", "no-store")
                .body(ApiResponse.success(SuccessCode.LOGOUT_SUCCESS, null));
    }
}
