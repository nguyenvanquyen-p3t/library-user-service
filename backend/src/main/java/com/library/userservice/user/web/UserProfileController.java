package com.library.userservice.user.web;

import com.library.userservice.common.api.ApiResponse;
import com.library.userservice.common.api.SuccessCode;
import com.library.userservice.security.UserPrincipal;
import com.library.userservice.user.dto.ProfilePatchRequest;
import com.library.userservice.user.dto.UserProfile;
import com.library.userservice.user.service.UserProfileService;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Hidden // Hợp đồng OpenAPI được duy trì trong YAML.
public class UserProfileController {
    private final UserProfileService profiles;

    public UserProfileController(UserProfileService profiles) { this.profiles = profiles; }

    @GetMapping(value = "/api/v1/users/me", produces = "application/json")
    public ApiResponse<UserProfile> get(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(SuccessCode.PROFILE_GET_SUCCESS, profiles.getProfile(principal));
    }

    @PatchMapping(value = "/api/v1/users/me", consumes = "application/json", produces = "application/json")
    public ApiResponse<UserProfile> patch(@AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ProfilePatchRequest request) {
        return ApiResponse.success(SuccessCode.PROFILE_UPDATE_SUCCESS, profiles.updateProfile(principal, request));
    }
}
