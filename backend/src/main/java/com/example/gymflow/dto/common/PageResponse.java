package com.example.gymflow.dto.common;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Page JSON contract of the API: {@code {items, page, size, totalItems, totalPages}}, {@code page} is 0-based.
 *
 * @param <T> the item type
 */
public record PageResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalItems,
        int totalPages
) {
    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
