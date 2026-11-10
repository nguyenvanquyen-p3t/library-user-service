package com.library.userservice.common.validation;

import org.hibernate.validator.internal.constraintvalidators.bv.EmailValidator;

/** Kiểm tra dữ liệu đã chuẩn hóa; không kiểm tra hiệu lực token/OTP hoặc database. */
public final class InputRules {
    private InputRules() {}

    public static ValidationCode check(String value, Input.Kind kind, boolean required) {
        if (value == null) return required ? ValidationCode.REQUIRED : null;
        return switch (kind) {
            case NEW_PASSWORD -> length(value, 6, 128);
            case CURRENT_PASSWORD -> null; // Chỉ bắt buộc không null, không áp chính sách mật khẩu mới.
            case TOKEN -> value.isEmpty() ? ValidationCode.INVALID_LENGTH : null;
            case OTP -> value.matches("[0-9]{6}") ? null : ValidationCode.INVALID_FORMAT;
            case PHONE -> value.matches("\\+?[0-9]{8,15}") ? null : ValidationCode.INVALID_FORMAT;
            case USERNAME -> identifier(value, false);
            case EMAIL -> identifier(value, true);
            case IDENTIFIER -> identifier(value, value.contains("@"));
            case FULL_NAME -> value.isBlank() ? ValidationCode.NOT_BLANK
                    : length(value, 1, 150) != null ? ValidationCode.INVALID_LENGTH
                    : value.codePoints().anyMatch(Character::isISOControl) ? ValidationCode.INVALID_FORMAT : null;
        };
    }

    private static ValidationCode identifier(String value, boolean email) {
        if (value.isBlank()) return ValidationCode.NOT_BLANK;
        if (length(value, 1, email ? 255 : 50) != null) return ValidationCode.INVALID_LENGTH;
        if (value.codePoints().anyMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c) || Character.isISOControl(c)))
            return ValidationCode.INVALID_FORMAT;
        if (!email) return value.contains("@") ? ValidationCode.INVALID_FORMAT : null;
        return new EmailValidator().isValid(value, null) ? null : ValidationCode.INVALID_FORMAT;
    }

    private static ValidationCode length(String value, int min, int max) {
        return value.length() < min || value.length() > max ? ValidationCode.INVALID_LENGTH : null;
    }
}

