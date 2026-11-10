package com.library.userservice.security;

import com.library.userservice.common.api.ApiResponse;
import com.library.userservice.common.api.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class SecurityErrorWriter {
    private final JsonMapper mapper;
    public SecurityErrorWriter(JsonMapper mapper) { this.mapper = mapper; }

    public void write(HttpServletResponse response, ErrorCode code) throws IOException {
        response.setStatus(code.status());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        if (code.status() == 401) response.setHeader("WWW-Authenticate", "Bearer");
        response.getWriter().write(mapper.writeValueAsString(ApiResponse.failure(code, null)));
    }
}
