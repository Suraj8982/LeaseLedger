package com.leaseledger.common;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * The JSON shape of every paginated list in this API. We return this instead of
 * Spring's own Page object, whose JSON structure is an internal detail that has
 * changed between Spring versions.
 */
public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
