package com.perimity.auth.dto.response;

import com.perimity.auth.entity.BlocklistEntry;
import java.time.LocalDateTime;

/**
 * Read model for a blocklist entry.
 *
 * This is an ADMIN-ONLY shape. It must never be reachable by the person it
 * describes. Per FR-BLK-4 a blocked registration is refused with a deliberately
 * vague message - the refusal path returns nothing from this record.
 */
public record BlocklistEntryResponse(
        Long id,
        Long campusId,
        String email,
        String phone,
        String reason,
        Long createdBy,
        LocalDateTime createdAt
) {

    public static BlocklistEntryResponse from(BlocklistEntry e) {
        return new BlocklistEntryResponse(
                e.getId(),
                e.getCampusId(),
                e.getEmail(),
                e.getPhone(),
                e.getReason(),
                e.getCreatedBy(),
                e.getCreatedAt()
        );
    }
}
