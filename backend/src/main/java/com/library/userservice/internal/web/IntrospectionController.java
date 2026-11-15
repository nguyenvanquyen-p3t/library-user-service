package com.library.userservice.internal.web;

import com.library.userservice.internal.dto.IntrospectionRequest;
import com.library.userservice.internal.service.IntrospectionService;
import com.library.userservice.common.api.ErrorCode;
import com.library.userservice.common.exception.ApplicationException;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@Hidden
@RestController
public class IntrospectionController {
    public static final String PATH = "/api/v1/internal/oauth2/introspect";
    private final IntrospectionService service;
    private final FormHttpMessageConverter forms = new FormHttpMessageConverter();

    public IntrospectionController(IntrospectionService service) { this.service = service; }

    @PostMapping(path = PATH, consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Object> introspect(HttpServletRequest request) {
        // ServletServerHttpRequest mặc định dựng lại POST form từ parameter map, trộn cả query.
        // Chỉ đọc body gốc để query/cookie không thể thay thế token hoặc che tham số lặp.
        var input = new ServletServerHttpRequest(request) {
            @Override public InputStream getBody() throws IOException { return request.getInputStream(); }
        };
        IntrospectionRequest form;
        try { form = IntrospectionRequest.fromForm(forms.read(null, input)); }
        catch (IOException | IllegalArgumentException exception) { throw new ApplicationException(ErrorCode.INVALID_REQUEST); }
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).header("Cache-Control", "no-store")
                .body(service.introspect(form));
    }
}
