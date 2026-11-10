package com.library.userservice.common.validation;

public enum ValidationCode {
    REQUIRED("Trường bắt buộc không được thiếu hoặc null"),
    TYPE_MISMATCH("Sai kiểu dữ liệu"),
    NOT_BLANK("Không được để trống"),
    INVALID_LENGTH("Độ dài không hợp lệ"),
    INVALID_FORMAT("Định dạng không hợp lệ"),
    INVALID_VALUE("Giá trị không được phép"),
    OUT_OF_RANGE("Giá trị nằm ngoài giới hạn"),
    UNKNOWN_FIELD("Trường không được phép"),
    EMPTY_PATCH("Phải cung cấp ít nhất một trường cập nhật"),
    PASSWORD_MISMATCH("Mật khẩu xác nhận không khớp");

    private final String message;
    ValidationCode(String message) { this.message = message; }
    public String message() { return message; }
}

