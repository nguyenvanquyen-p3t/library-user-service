package com.library.userservice.auth.web;

import com.library.userservice.auth.dto.RefreshTokenRequest;
import com.library.userservice.auth.dto.TokenPair;
import com.library.userservice.auth.service.RefreshService;
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
public class RefreshController {
    private final RefreshService refresh;

    public RefreshController(RefreshService refresh) { this.refresh = refresh; }

    @PostMapping(value = "/api/v1/auth/refresh", consumes = "application/json", produces = "application/json")
    public ResponseEntity<ApiResponse<TokenPair>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        var tokens = refresh.refresh(request);
        return ResponseEntity.ok().header("Cache-Control", "no-store")
                .body(ApiResponse.success(SuccessCode.TOKEN_REFRESH_SUCCESS, tokens));
    }
}
