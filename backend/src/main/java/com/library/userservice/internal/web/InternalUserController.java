package com.library.userservice.internal.web;

import com.library.userservice.common.api.ApiResponse;
import com.library.userservice.common.api.SuccessCode;
import com.library.userservice.internal.dto.InternalUserSummary;
import com.library.userservice.internal.service.InternalUserService;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Hidden // YAML tiếp tục là nguồn hợp đồng OpenAPI.
public class InternalUserController {
    public static final String PATH = "/api/v1/internal/users/{id}";
    private final InternalUserService users;

    public InternalUserController(InternalUserService users) { this.users = users; }

    @GetMapping(value = PATH, produces = "application/json")
    public ApiResponse<InternalUserSummary> get(@PathVariable @Min(value = 1, message = "OUT_OF_RANGE") int id) {
        return ApiResponse.success(SuccessCode.USER_GET_SUCCESS, users.getUser(id));
    }
}
