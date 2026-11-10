package com.library.userservice.common.validation;

import java.util.Locale;

public final class Normalization {
    private Normalization() {}
    public static String identifier(String value) {
        return value == null ? null : value.strip().toLowerCase(Locale.ROOT);
    }
    public static String fullName(String value) { return value == null ? null : value.strip(); }
}

