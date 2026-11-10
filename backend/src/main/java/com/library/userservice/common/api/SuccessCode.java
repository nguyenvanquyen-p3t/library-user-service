package com.library.userservice.common.api;

public enum SuccessCode {
    REGISTER_CODE_SENT(200, "Mã xác minh đã được gửi"),
    REGISTER_SUCCESS(201, "Đăng ký thành công"),
    LOGIN_SUCCESS(200, "Đăng nhập thành công"),
    TOKEN_REFRESH_SUCCESS(200, "Làm mới token thành công"),
    LOGOUT_SUCCESS(200, "Đã xử lý yêu cầu đăng xuất"),
    PASSWORD_RESET_CODE_SENT(200, "Nếu email tồn tại trong hệ thống, mã xác minh sẽ được gửi"),
    PASSWORD_RESET_SUCCESS(200, "Đặt lại mật khẩu thành công"),
    PROFILE_GET_SUCCESS(200, "Lấy thông tin cá nhân thành công"),
    PROFILE_UPDATE_SUCCESS(200, "Cập nhật thông tin cá nhân thành công"),
    PASSWORD_CHANGE_SUCCESS(200, "Đổi mật khẩu thành công. Vui lòng đăng nhập lại"),
    GOOGLE_LOGIN_SUCCESS(200, "Đăng nhập bằng Google thành công"),
    GOOGLE_ACCOUNT_LINKED(200, "Liên kết Google và đăng nhập thành công"),
    GOOGLE_REGISTRATION_REQUIRED(200, "Vui lòng hoàn tất thông tin tài khoản"),
    GOOGLE_REGISTER_SUCCESS(201, "Đăng ký bằng Google thành công"),
    SET_PASSWORD_CODE_SENT(200, "Mã xác minh đã được gửi"),
    PASSWORD_SET_SUCCESS(200, "Thiết lập mật khẩu thành công. Vui lòng đăng nhập lại"),
    USER_GET_SUCCESS(200, "Lấy thông tin người dùng thành công"),
    USER_STATUS_UPDATE_SUCCESS(200, "Cập nhật trạng thái thành công"),
    SESSION_LIST_SUCCESS(200, "Lấy danh sách phiên thành công"),
    SESSION_REVOKE_SUCCESS(200, "Đã xử lý yêu cầu đăng xuất phiên"),
    OTHER_SESSIONS_REVOKED(200, "Đã xử lý yêu cầu đăng xuất các phiên khác");

    private final int status;
    private final String message;
    SuccessCode(int status, String message) { this.status = status; this.message = message; }
    public int status() { return status; }
    public String message() { return message; }
}

