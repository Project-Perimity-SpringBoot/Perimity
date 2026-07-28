package com.perimity.auth.dto.response;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/**
 * A flat, serialisation-safe wrapper for a paged result.
 *
 * Spring's Page serialises to a large unstable JSON shape and logs a warning
 * about relying on it. Nine of your repository methods return Page, so every
 * list endpoint should hand back this instead.
 *
 * Usage:
 *   PageResponse.from(userRepo.findByCampusIdOrderByNameAsc(campusId, pageable),
 *                     UserResponse::from)
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {

    public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast()
        );
    }
}
