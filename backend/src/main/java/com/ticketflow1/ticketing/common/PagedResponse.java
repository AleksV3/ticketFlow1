package com.ticketflow1.ticketing.common;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/** Standard list-endpoint envelope (contracts/README.md Pagination). */
public record PagedResponse<T>(
        List<T> items,
        int page,
        int pageSize,
        long totalItems,
        int totalPages) {

    public static <E, T> PagedResponse<T> from(Page<E> page, Function<E, T> mapper) {
        return new PagedResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
