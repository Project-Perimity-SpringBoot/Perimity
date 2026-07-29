package com.perimity.guard.dto.response;

import com.perimity.guard.document.ScanSession;
import com.perimity.guard.document.enums.SessionState;
import java.time.LocalDateTime;

/** Read model for a shift. The scanner screen shows which gate it is pinned to. */
public record ScanSessionResponse(
        String id,
        Long guardUserId,
        Long campusId,
        Long gateId,
        String gateName,
        SessionState state,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        int totalScans,
        int allowedCount,
        int deniedCount
) {

    public static ScanSessionResponse from(ScanSession s) {
        return new ScanSessionResponse(
                s.getId(), s.getGuardUserId(), s.getCampusId(), s.getGateId(), s.getGateName(),
                s.getState(), s.getStartedAt(), s.getEndedAt(),
                s.getTotalScans() == null ? 0 : s.getTotalScans(),
                s.getAllowedCount() == null ? 0 : s.getAllowedCount(),
                s.getDeniedCount() == null ? 0 : s.getDeniedCount());
    }
}
