package com.library.userservice.security;

import com.library.userservice.common.exception.IntrospectionErrorFormatter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

/** Chỉ gắn vào chain introspection; chưa xác thực danh tính/quyền service. */
public class IntrospectionProtocolFilter extends OncePerRequestFilter {
    private final JsonMapper mapper;
    public IntrospectionProtocolFilter(JsonMapper mapper) { this.mapper = mapper; }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("Cache-Control", "no-store");
        if (!"POST".equals(request.getMethod())) {
            var error = IntrospectionErrorFormatter.format(IntrospectionErrorFormatter.Reason.METHOD_NOT_ALLOWED);
            response.setStatus(error.getStatusCode().value());
            error.getHeaders().forEach((name, values) -> values.forEach(value -> response.addHeader(name, value)));
            if (!"HEAD".equals(request.getMethod()))
                response.getWriter().write(mapper.writeValueAsString(error.getBody()));
            return;
        }
        chain.doFilter(request, response);
    }
}
