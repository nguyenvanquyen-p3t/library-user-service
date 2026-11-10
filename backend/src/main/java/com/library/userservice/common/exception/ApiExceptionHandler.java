package com.library.userservice.common.exception;

import com.library.userservice.common.api.ApiResponse;
import com.library.userservice.common.api.ErrorCode;
import com.library.userservice.common.api.FieldViolation;
import com.library.userservice.common.api.ValidationErrorData;
import com.library.userservice.common.validation.ValidationCode;
import jakarta.validation.ConstraintViolationException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.servlet.support.RequestContextUtils;
import tools.jackson.databind.exc.InvalidFormatException;
import tools.jackson.databind.exc.IgnoredPropertyException;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.exc.UnrecognizedPropertyException;

/** Chỉ áp dụng controller của dự án; filter Security và springdoc giữ cơ chế riêng. */
@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleHttp(Exception exception, HttpServletRequest request) throws Exception {
        // 405/415 có thể xảy ra trước khi Spring chọn controller nên selector basePackages không đủ.
        if (!isProjectHandler(request)) throw exception;
        var response = handle(exception);
        if (response.getStatusCode().is5xxServerError())
            org.slf4j.LoggerFactory.getLogger(ApiExceptionHandler.class)
                    .error("Không xử lý được request, loại lỗi: {}", exception.getClass().getName());
        return response;
    }

    private boolean isProjectHandler(HttpServletRequest request) {
        Object handler = request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE);
        if (handler instanceof HandlerMethod method) return isProjectType(method.getBeanType());
        var context = RequestContextUtils.findWebApplicationContext(request);
        if (context == null) return false;
        for (var mapping : context.getBeansOfType(RequestMappingHandlerMapping.class).values()) {
            for (var entry : mapping.getHandlerMethods().entrySet()) {
                if (!isProjectType(entry.getValue().getBeanType())) continue;
                var patterns = entry.getKey().getPathPatternsCondition();
                if (patterns != null && patterns.getMatchingCondition(request) != null) return true;
                var legacy = entry.getKey().getPatternsCondition();
                if (legacy != null && legacy.getMatchingCondition(request) != null) return true;
            }
        }
        return false;
    }

    private boolean isProjectType(Class<?> type) {
        return type.getPackageName().startsWith("com.library.userservice.");
    }

    public ResponseEntity<Object> handle(Exception exception) {
        if (exception instanceof ApplicationException app) {
            var headers = new HttpHeaders();
            if (app.retryAfterSeconds() != null) headers.set(HttpHeaders.RETRY_AFTER, app.retryAfterSeconds().toString());
            return response(app.code(), null, headers);
        }
        if (exception instanceof BindException bind) {
            var errors = bind.getBindingResult().getAllErrors().stream().map(error -> new FieldViolation(
                    error instanceof org.springframework.validation.FieldError field ? field.getField() : "$",
                    error instanceof org.springframework.validation.FieldError field && field.isBindingFailure()
                            ? ValidationCode.TYPE_MISMATCH : validationCode(error.getDefaultMessage()))).toList();
            return validation(errors);
        }
        if (exception instanceof HandlerMethodValidationException method) {
            if (method.isForReturnValue()) return response(ErrorCode.INTERNAL_ERROR);
            var errors = new ArrayList<FieldViolation>();
            for (var result : method.getParameterValidationResults()) {
                String field = result.getMethodParameter().getParameterName();
                for (var error : result.getResolvableErrors()) errors.add(new FieldViolation(
                        field == null ? "$" : field, validationCode(error.getDefaultMessage())));
            }
            for (var error : method.getCrossParameterValidationResults())
                errors.add(new FieldViolation("$", validationCode(error.getDefaultMessage())));
            return validation(errors);
        }
        if (exception instanceof ConstraintViolationException constraints) {
            return validation(constraints.getConstraintViolations().stream().map(violation -> {
                String field = "$";
                for (var node : violation.getPropertyPath()) if (node.getName() != null) field = node.getName();
                return new FieldViolation(field, validationCode(violation.getMessageTemplate()));
            }).toList());
        }
        if (exception instanceof MissingServletRequestParameterException missing)
            return validation(List.of(new FieldViolation(missing.getParameterName(), ValidationCode.REQUIRED)));
        if (exception instanceof MethodArgumentTypeMismatchException mismatch) {
            var type = mismatch.getRequiredType();
            var code = type == UUID.class ? ValidationCode.INVALID_FORMAT
                    : type != null && type.isEnum() ? ValidationCode.INVALID_VALUE : ValidationCode.TYPE_MISMATCH;
            return validation(List.of(new FieldViolation(mismatch.getName(), code)));
        }
        if (exception instanceof HttpMessageNotReadableException unreadable) return unreadable(unreadable);
        if (exception instanceof HttpRequestMethodNotSupportedException method) {
            var headers = new HttpHeaders();
            if (method.getSupportedHttpMethods() != null) headers.setAllow(method.getSupportedHttpMethods());
            return response(ErrorCode.METHOD_NOT_ALLOWED, null, headers);
        }
        if (exception instanceof HttpMediaTypeNotSupportedException) return response(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
        if (exception instanceof DataIntegrityViolationException integrity) return response(constraintCode(integrity));
        if (exception instanceof TransientDataAccessException || exception instanceof DataAccessResourceFailureException)
            return response(ErrorCode.SERVICE_UNAVAILABLE);
        return response(ErrorCode.INTERNAL_ERROR);
    }

    private ResponseEntity<Object> unreadable(HttpMessageNotReadableException exception) {
        for (Throwable cause = exception.getCause(); cause != null; cause = cause.getCause()) {
            if (cause instanceof UnrecognizedPropertyException unknown)
                return validation(List.of(new FieldViolation(unknown.getPropertyName(), ValidationCode.UNKNOWN_FIELD)));
            if (cause instanceof IgnoredPropertyException ignored)
                return validation(List.of(new FieldViolation(ignored.getPropertyName(), ValidationCode.UNKNOWN_FIELD)));
            if (cause instanceof MismatchedInputException mismatch) {
                String field = mismatch.getPath().stream().map(ref -> ref.getPropertyName())
                        .filter(Objects::nonNull).reduce((left, right) -> left + "." + right).orElse("$");
                var code = ValidationCode.TYPE_MISMATCH;
                if (cause instanceof InvalidFormatException format && format.getValue() instanceof String) {
                    if (format.getTargetType().isEnum()) code = ValidationCode.INVALID_VALUE;
                    else if (format.getTargetType() == UUID.class) code = ValidationCode.INVALID_FORMAT;
                }
                return validation(List.of(new FieldViolation(field, code)));
            }
        }
        return response(ErrorCode.INVALID_REQUEST);
    }

    private ErrorCode constraintCode(DataIntegrityViolationException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException constraint
                    && "23505".equals(constraint.getSQLState()) && constraint.getConstraintName() != null) {
                return switch (constraint.getConstraintName()) {
                    case "UQ_Users_UserName" -> ErrorCode.USERNAME_ALREADY_EXISTS;
                    case "UQ_Users_Email" -> ErrorCode.EMAIL_ALREADY_EXISTS;
                    case "UQ_UserIdentities_Provider_ProviderUserId" -> ErrorCode.GOOGLE_IDENTITY_ALREADY_REGISTERED;
                    default -> ErrorCode.INTERNAL_ERROR;
                };
            }
        }
        return ErrorCode.INTERNAL_ERROR;
    }

    private ValidationCode validationCode(String message) {
        try { return ValidationCode.valueOf(message == null ? "" : message); }
        catch (IllegalArgumentException ignored) { return ValidationCode.INVALID_VALUE; }
    }

    private ResponseEntity<Object> validation(List<FieldViolation> errors) {
        if (errors.isEmpty()) return response(ErrorCode.INTERNAL_ERROR);
        return response(ErrorCode.VALIDATION_ERROR, new ValidationErrorData(errors), new HttpHeaders());
    }
    private ResponseEntity<Object> response(ErrorCode code) { return response(code, null, new HttpHeaders()); }
    private ResponseEntity<Object> response(ErrorCode code, Object data, HttpHeaders headers) {
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new ResponseEntity<>(ApiResponse.failure(code, data), headers, HttpStatusCode.valueOf(code.status()));
    }
}
