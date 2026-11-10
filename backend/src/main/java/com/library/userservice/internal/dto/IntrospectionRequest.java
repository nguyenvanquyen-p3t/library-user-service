package com.library.userservice.internal.dto;

import java.util.Set;
import org.springframework.util.MultiValueMap;
import com.library.userservice.common.api.ErrorCode;
import com.library.userservice.common.exception.ApplicationException;

/** Chỉ kiểm tra form; xác thực/quyền Gateway phải hoàn tất trước khi gọi. */
public record IntrospectionRequest(String token, String token_type_hint) {
    public IntrospectionRequest {
        if (token == null || token.isEmpty()) throw new ApplicationException(ErrorCode.INVALID_REQUEST);
    }
    public static IntrospectionRequest fromForm(MultiValueMap<String, String> form) {
        if (!Set.of("token", "token_type_hint").containsAll(form.keySet())
                || form.values().stream().anyMatch(values -> values == null || values.size() != 1))
            throw new ApplicationException(ErrorCode.INVALID_REQUEST);
        return new IntrospectionRequest(form.getFirst("token"), form.getFirst("token_type_hint"));
    }
    @Override public String toString() { return "IntrospectionRequest[REDACTED]"; }
}

