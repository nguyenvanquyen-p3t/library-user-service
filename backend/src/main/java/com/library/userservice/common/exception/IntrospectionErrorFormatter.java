package com.library.userservice.common.exception;

import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** Formatter dùng sau này bởi controller/security adapter; chưa cài xác thực JWT. */
public final class IntrospectionErrorFormatter {
    private IntrospectionErrorFormatter() {}

    public enum Reason {
        MISSING_AUTHENTICATION(401, null), INVALID_TOKEN(401, "invalid_token"),
        INSUFFICIENT_SCOPE(401, "insufficient_scope"), INVALID_REQUEST(400, "invalid_request"),
        METHOD_NOT_ALLOWED(405, "invalid_request"), UNSUPPORTED_MEDIA_TYPE(415, "invalid_request"),
        UNAVAILABLE(503, "temporarily_unavailable"), INTERNAL_ERROR(500, "server_error");

        private final int status;
        private final String error;
        Reason(int status, String error) { this.status = status; this.error = error; }
    }

    public static ResponseEntity<Object> format(Reason reason) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setCacheControl("no-store");
        if (reason.status == 401) headers.set(HttpHeaders.WWW_AUTHENTICATE,
                reason.error == null ? "Bearer" : "Bearer error=\"" + reason.error + "\"");
        if (reason.status == 405) headers.set(HttpHeaders.ALLOW, "POST");
        return new ResponseEntity<>(reason.error == null ? Map.of() : Map.of("error", reason.error),
                headers, HttpStatusCode.valueOf(reason.status));
    }
}
