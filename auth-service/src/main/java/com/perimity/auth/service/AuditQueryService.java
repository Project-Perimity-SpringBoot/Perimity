package com.perimity.auth.service;

import com.perimity.auth.dto.response.AuditLogResponse;
import com.perimity.auth.dto.response.PageResponse;
import com.perimity.auth.entity.enums.AuditAction;
import com.perimity.auth.repository.AuditLogRepository;
import java.time.LocalDateTime;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reading the audit trail.
 *
 * Read-only, deliberately. There is no update and no delete anywhere in this
 * service - an audit trail that can be edited is not evidence.
 */
@Service
public class AuditQueryService {

    private final AuditLogRepository repository;

    public AuditQueryService(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> byCampus(Long campusId, AuditAction action,
                                                   Pageable pageable) {
        return PageResponse.from(
                action == null
                        ? repository.findByCampusIdOrderByCreatedAtDesc(campusId, pageable)
                        : repository.findByCampusIdAndActionOrderByCreatedAtDesc(campusId, action, pageable),
                AuditLogResponse::from);
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> byCampusAndRange(Long campusId, LocalDateTime from,
                                                           LocalDateTime to, Pageable pageable) {
        return PageResponse.from(
                repository.findByCampusIdAndCreatedAtBetweenOrderByCreatedAtDesc(
                        campusId, from, to, pageable),
                AuditLogResponse::from);
    }

    /** Everything one person did. The first query asked after an incident. */
    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> byActor(Long actorUserId, Pageable pageable) {
        return PageResponse.from(
                repository.findByActorUserIdOrderByCreatedAtDesc(actorUserId, pageable),
                AuditLogResponse::from);
    }
}
