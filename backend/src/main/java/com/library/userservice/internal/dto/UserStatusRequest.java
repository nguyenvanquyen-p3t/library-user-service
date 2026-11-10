package com.library.userservice.internal.dto;

import com.library.userservice.user.persistence.UserStatus;
import jakarta.validation.constraints.NotNull;

public record UserStatusRequest(@NotNull(message = "REQUIRED") UserStatus status) {}

