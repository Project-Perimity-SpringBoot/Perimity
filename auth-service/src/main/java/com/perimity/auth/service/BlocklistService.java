package com.perimity.auth.service;

import com.perimity.auth.dto.request.BlocklistCreateDto;
import com.perimity.auth.dto.request.BulkScreenRequestDto;
import com.perimity.auth.dto.response.BlocklistEntryResponse;
import com.perimity.auth.dto.response.BulkScreenResponseDto;
import com.perimity.auth.dto.response.BulkScreenResponseDto.RowVerdict;
import com.perimity.auth.dto.response.PageResponse;
import com.perimity.auth.entity.BlocklistEntry;
import com.perimity.auth.entity.enums.AuditAction;
import com.perimity.auth.entity.enums.Role;
import com.perimity.auth.exception.ResourceNotFoundException;
import com.perimity.auth.repository.BlocklistEntryRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(BlocklistService.class);

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

    // ------------------------------------------------- Day 10, bulk screening

    /**
     * Screen a whole spreadsheet in one call (FR-BLK-3).
     *
     * Two queries total, whatever the sheet size: load this campus's blocked
     * emails and phones, then decide every row in memory. Calling isBlocked()
     * per row would be two queries PER ROW - 1,200 for a 600-row sheet, with
     * the faculty watching a spinner through all of them, against a fast-path
     * budget of about two seconds.
     *
     * Read-only and side-effect-free apart from one audit row, so the bulk
     * engine may call it during validation, again after the uploader fixes
     * rows, and again on a retry, with the same answer every time.
     */
    @Transactional(readOnly = true)
    public BulkScreenResponseDto screen(BulkScreenRequestDto request) {
        Set<String> blockedEmails = repository.findBlockedEmails(request.getCampusId());
        Set<String> blockedPhones = repository.findBlockedPhones(request.getCampusId());

        List<RowVerdict> verdicts = new ArrayList<>(request.getRows().size());

        for (BulkScreenRequestDto.Candidate row : request.getRows()) {
            String email = row.getEmail() == null ? null : row.getEmail().trim().toLowerCase();
            String phone = row.getPhone() == null ? null : row.getPhone().trim();

            boolean blocked = (email != null && blockedEmails.contains(email))
                    || (phone != null && blockedPhones.contains(phone));

            verdicts.add(blocked
                    ? RowVerdict.refused(row.getRowNumber(), row.getEmail())
                    : RowVerdict.ok(row.getRowNumber(), row.getEmail()));
        }

        BulkScreenResponseDto response = BulkScreenResponseDto.of(verdicts);
        recordScreening(request, response);
        return response;
    }

    /**
     * One audit row per batch (FR-BLK-5), not one per refused row.
     *
     * The requirement is that a blocked attempt is recorded. A 600-row sheet of
     * blocked addresses taken literally means 600 rows, each in its own
     * transaction because AuditService is REQUIRES_NEW, and a Campus Admin
     * looking for last Tuesday's failed logins has to page through all of them.
     *
     * So: one row naming the count and the row numbers. That is a complete
     * record of the attempt - who, when, from where, how many, which rows - and
     * the per-row detail is in the error report the uploader downloads, which is
     * where anyone investigating a specific row would actually look.
     *
     * Row NUMBERS, not addresses. The row number is enough to find the row in
     * the sheet, and keeps the entry short enough to stay under the 500-character
     * sanitise() cap on a realistic batch.
     */
    private void recordScreening(BulkScreenRequestDto request, BulkScreenResponseDto response) {
        if (response.refusedCount() == 0) {
            return;
        }

        String rows = response.verdicts().stream()
                .filter(v -> !v.allowed())
                .map(v -> v.rowNumber() == null ? "?" : String.valueOf(v.rowNumber()))
                .limit(50)
                .collect(Collectors.joining(","));

        String more = response.refusedCount() > 50
                ? " and " + (response.refusedCount() - 50) + " more"
                : "";

        audit.recordAnonymous(AuditAction.BULK_BLOCKLIST_SCREENED,
                "campus:" + request.getCampusId(),
                response.refusedCount() + " of " + response.totalRows()
                        + " rows refused, rows " + rows + more
                        + (request.getSource() == null ? "" : ", source " + request.getSource()));

        log.info("Bulk screen for campus {}: {} of {} refused (source {})",
                request.getCampusId(), response.refusedCount(), response.totalRows(),
                request.getSource());
    }
}
