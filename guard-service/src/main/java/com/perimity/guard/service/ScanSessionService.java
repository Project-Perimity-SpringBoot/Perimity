package com.perimity.guard.service;

import com.perimity.guard.document.ScanSession;
import com.perimity.guard.document.enums.ScanResult;
import com.perimity.guard.document.enums.SessionState;
import com.perimity.guard.dto.request.ScanSessionEndDto;
import com.perimity.guard.dto.request.ScanSessionStartDto;
import com.perimity.guard.dto.response.ScanSessionResponse;
import com.perimity.guard.exception.ResourceNotFoundException;
import com.perimity.guard.repository.ScanSessionRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * A guard's shift.
 *
 * This exists so a guard picks their gate once, not on every scan. Every scan
 * then reads gate and campus from the open session - which is why
 * ScanRequestDto has no gateId field, and why a guard cannot log entries at a
 * gate they were never posted to.
 */
@Service
public class ScanSessionService {

    private final ScanSessionRepository sessionRepository;

    public ScanSessionService(ScanSessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    /**
     * Open a shift.
     *
     * One open session per guard, enforced here rather than by a unique index:
     * the constraint is "at most one where state is OPEN", and a Mongo partial
     * index is more trouble than this rule is worth at this scale.
     */
    public ScanSessionResponse start(ScanSessionStartDto dto) {
        if (sessionRepository.existsByGuardUserIdAndState(dto.getGuardUserId(), SessionState.OPEN)) {
            throw new IllegalStateException(
                    "This guard already has an open shift. End it before starting another.");
        }

        ScanSession session = ScanSession.builder()
                .guardUserId(dto.getGuardUserId())
                .campusId(dto.getCampusId())
                .gateId(dto.getGateId())
                .gateName(dto.getGateName().trim())
                .state(SessionState.OPEN)
                .startedAt(LocalDateTime.now())
                .deviceInfo(dto.getDeviceInfo())
                .build();

        return ScanSessionResponse.from(sessionRepository.save(session));
    }

    /**
     * Close a shift.
     *
     * guardUserId is checked against the stored session so one guard cannot end
     * another's shift by guessing an id. endedAt is stamped here, never taken
     * from the client.
     */
    public ScanSessionResponse end(String sessionId, ScanSessionEndDto dto) {
        ScanSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> ResourceNotFoundException.of("Session", sessionId));

        if (!session.getGuardUserId().equals(dto.getGuardUserId())) {
            throw new IllegalArgumentException("This shift belongs to a different guard.");
        }
        if (session.getState() == SessionState.CLOSED) {
            throw new IllegalStateException("This shift is already closed.");
        }

        session.setState(SessionState.CLOSED);
        session.setEndedAt(LocalDateTime.now());

        return ScanSessionResponse.from(sessionRepository.save(session));
    }

    /**
     * The guard's open shift. ScanService calls this on every scan, so it is
     * backed by the idx_guard_state compound index.
     */
    public ScanSession requireOpenSession(Long guardUserId) {
        return sessionRepository.findByGuardUserIdAndState(guardUserId, SessionState.OPEN)
                .orElseThrow(() -> new IllegalStateException(
                        "No open shift for this guard. Start a shift before scanning."));
    }

    public ScanSessionResponse current(Long guardUserId) {
        return ScanSessionResponse.from(requireOpenSession(guardUserId));
    }

    /** Who is on duty right now - the Campus Admin's gate view. */
    public List<ScanSessionResponse> openAtCampus(Long campusId) {
        return sessionRepository.findByCampusIdAndState(campusId, SessionState.OPEN)
                .stream().map(ScanSessionResponse::from).toList();
    }

    public List<ScanSessionResponse> history(Long guardUserId) {
        return sessionRepository.findByGuardUserIdOrderByStartedAtDesc(guardUserId)
                .stream().map(ScanSessionResponse::from).toList();
    }

    /**
     * Keeps the running totals on the session up to date.
     *
     * Denormalised on purpose: the shift summary a guard sees at handover would
     * otherwise be three count queries over the largest collection in the
     * platform, every time the screen refreshes.
     */
    void recordScan(ScanSession session, ScanResult result) {
        session.setTotalScans(nz(session.getTotalScans()) + 1);
        if (result == ScanResult.ALLOWED) {
            session.setAllowedCount(nz(session.getAllowedCount()) + 1);
        } else {
            session.setDeniedCount(nz(session.getDeniedCount()) + 1);
        }
        sessionRepository.save(session);
    }

    private int nz(Integer value) {
        return value == null ? 0 : value;
    }
}
