package com.perimity.campus.dto.response;

import com.perimity.campus.entity.CampusConfig;
import com.perimity.campus.entity.enums.ConfigValueType;
import java.time.LocalDateTime;

/**
 * Read model for one per-campus setting.
 *
 * The two accessors at the bottom exist so that five other services do not each
 * write their own "read this text as a boolean" logic. One reading, one place.
 */
public record CampusConfigResponse(
        Long id,
        Long campusId,
        String configKey,
        String configValue,
        ConfigValueType valueType,
        String description,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static CampusConfigResponse from(CampusConfig e) {
        return new CampusConfigResponse(
                e.getId(),
                e.getCampusId(),
                e.getConfigKey(),
                e.getConfigValue(),
                e.getValueType(),
                e.getDescription(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }

    /** Reads the value as a boolean, falling back when the type is not BOOLEAN. */
    public boolean asBoolean(boolean fallback) {
        if (valueType != ConfigValueType.BOOLEAN || configValue == null) {
            return fallback;
        }
        return Boolean.parseBoolean(configValue.trim());
    }

    /** Reads the value as an int, falling back when the type is not INTEGER. */
    public int asInt(int fallback) {
        if (valueType != ConfigValueType.INTEGER || configValue == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(configValue.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}
