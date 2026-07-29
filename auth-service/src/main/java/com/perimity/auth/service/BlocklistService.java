package com.perimity.auth.service;

import com.perimity.auth.dto.request.BlocklistCreateDto;
import com.perimity.auth.dto.response.BlocklistEntryResponse;
import com.perimity.auth.dto.response.PageResponse;
import com.perimity.auth.entity.BlocklistEntry;
import com.perimity.auth.entity.enums.AuditAction;
import com.perimity.auth.entity.enums.Role;
import com.perimity.auth.exception.ResourceNotFoundException;
import com.perimity.auth.repository.BlocklistEntryRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The per-campus blocklist (FR-BLK-1 to FR-BLK-6).
 *
 * Scoped per campus by design: being barred from one institution does not bar
 * you from another. There is no platform-wide block, and adding one would be a
 * product decision, not a technical one.
 *
 * Every entry needs a reason. An entry with no reason cannot be defended six
 * months later when somebody asks why a person was refused at the gate.
 */
@Service
public class BlocklistService {

    private final BlocklistEntryRepository repository;
    private final AuditService audit;

    public BlocklistService(BlocklistEntryRepository repository, AuditService audit) {
        this.repository = repository;
        this.audit = audit;
    }

    @Transactional
    public BlocklistEntryResponse add(BlocklistCreateDto dto, Long actorUserId, Role actorRole) {
        if (dto.getEmail() != null
                && repository.existsByCampusIdAndEmailIgnoreCase(dto.getCampusId(), dto.getEmail())) {
            throw new IllegalArgumentException("That email is already blocked at this campus.");
        }
        if (dto.getPhone() != null
                && repository.existsByCampusIdAndPhone(dto.getCampusId(), dto.getPhone())) {
            throw new IllegalArgumentException("That phone number is already blocked at this campus.");
        }

        BlocklistEntry entry = repository.save(BlocklistEntry.builder()
                .campusId(dto.getCampusId())
                .email(dto.getEmail() == null ? null : dto.getEmail().toLowerCase())
                .phone(dto.getPhone())
                .reason(dto.getReason())
                .createdBy(actorUserId)
                .build());

        audit.record(AuditAction.BLOCKLIST_ADDED, actorUserId, actorRole,
                dto.getCampusId(), "blocklist:" + entry.getId(), dto.getReason());

        return BlocklistEntryResponse.from(entry);
    }

    /**
     * Remove an entry. This one IS a hard delete, unlike everything else.
     *
     * A blocklist is a live control, not a history. Keeping a soft-deleted row
     * would mean every check has to remember to filter it out, and one forgotten
     * filter silently bars someone who was cleared. The audit row is the record.
     */
    @Transactional
    public void remove(Long campusId, Long id, Long actorUserId, Role actorRole) {
        BlocklistEntry entry = repository.findByIdAndCampusId(id, campusId)
                .orElseThrow(() -> ResourceNotFoundException.of("Blocklist entry", id));

        repository.delete(entry);

        audit.record(AuditAction.BLOCKLIST_REMOVED, actorUserId, actorRole, campusId,
                "blocklist:" + id,
                "Removed entry for " + (entry.getEmail() != null ? entry.getEmail() : entry.getPhone()));
    }

    /** Admin-only listing. Never reachable by the person it describes. */
    @Transactional(readOnly = true)
    public PageResponse<BlocklistEntryResponse> list(Long campusId, String emailFilter, Pageable pageable) {
        return PageResponse.from(
                emailFilter == null || emailFilter.isBlank()
                        ? repository.findByCampusIdOrderByCreatedAtDesc(campusId, pageable)
                        : repository.findByCampusIdAndEmailContainingIgnoreCase(campusId, emailFilter, pageable),
                BlocklistEntryResponse::from);
    }

    /**
     * The check other services call before creating anything for a person.
     *
     * Returns a plain boolean and nothing else - a caller must not be able to
     * learn WHY somebody is blocked, only that they are.
     */
    @Transactional(readOnly = true)
    public boolean isBlocked(Long campusId, String email, String phone) {
        return (email != null && repository.existsByCampusIdAndEmailIgnoreCase(campusId, email))
                || (phone != null && repository.existsByCampusIdAndPhone(campusId, phone));
    }

    @Transactional(readOnly = true)
    public long count(Long campusId) {
        return repository.countByCampusId(campusId);
    }
}
