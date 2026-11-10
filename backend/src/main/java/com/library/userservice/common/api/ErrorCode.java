package com.library.userservice.common.api;

public enum ErrorCode {
    INVALID_REQUEST(400, "Request không đọc được hoặc thiếu body"),
    VALIDATION_ERROR(400, "Dữ liệu không hợp lệ"),
    AUTHENTICATION_REQUIRED(401, "Cần cung cấp thông tin xác thực"),
    SERVICE_TOKEN_INVALID(401, "Service token không hợp lệ"),
    ADMIN_ACTOR_INVALID(401, "Danh tính quản trị viên không hợp lệ"),
    INVALID_CREDENTIALS(401, "Thông tin đăng nhập không đúng"),
    ACCESS_TOKEN_INVALID(401, "Access token không hợp lệ"),
    SESSION_INACTIVE(401, "Phiên không còn hiệu lực"),
    REFRESH_TOKEN_INVALID(401, "Refresh token không hợp lệ"),
    GOOGLE_TOKEN_INVALID(401, "Google token không hợp lệ"),
    REGISTRATION_TOKEN_INVALID(401, "Token đăng ký không hợp lệ"),
    ACCOUNT_BANNED(403, "Tài khoản bị khóa"),
    ACCESS_DENIED(403, "Không có quyền thực hiện"),
    USERNAME_ALREADY_EXISTS(409, "Tên đăng nhập đã được sử dụng"),
    EMAIL_ALREADY_EXISTS(409, "Email đã được sử dụng"),
    PASSWORD_NOT_CONFIGURED(409, "Tài khoản chưa thiết lập mật khẩu"),
    PASSWORD_ALREADY_CONFIGURED(409, "Tài khoản đã có mật khẩu"),
    GOOGLE_IDENTITY_ALREADY_REGISTERED(409, "Danh tính Google đã được đăng ký"),
    CURRENT_PASSWORD_INCORRECT(400, "Mật khẩu hiện tại không đúng"),
    OTP_INVALID(400, "Mã xác minh không hợp lệ"),
    OTP_ATTEMPTS_EXCEEDED(400, "Mã đã đạt giới hạn nhập sai"),
    OTP_RATE_LIMITED(429, "Vượt giới hạn gửi mã"),
    USER_NOT_FOUND(404, "Không tìm thấy người dùng"),
    SESSION_NOT_FOUND(404, "Không tìm thấy phiên"),
    CURRENT_SESSION_REVOKE_NOT_ALLOWED(400, "Không được chọn phiên hiện tại"),
    METHOD_NOT_ALLOWED(405, "Phương thức HTTP không được hỗ trợ"),
    UNSUPPORTED_MEDIA_TYPE(415, "Kiểu nội dung không được hỗ trợ"),
    SERVICE_UNAVAILABLE(503, "Dịch vụ tạm thời không khả dụng"),
    INTERNAL_ERROR(500, "Lỗi hệ thống");

    private final int status;
    private final String message;
    ErrorCode(int status, String message) { this.status = status; this.message = message; }
    public int status() { return status; }
    public String message() { return message; }
}

