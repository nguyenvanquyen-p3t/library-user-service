package com.library.userservice.session.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record SessionPage(List<SessionItem> items, int page, int size, long totalElements) {
    public SessionPage {
        items = List.copyOf(items);
        if (page < 0 || size < 1 || size > 100 || totalElements < 0) throw new IllegalArgumentException("Phân trang không hợp lệ");
    }
}

