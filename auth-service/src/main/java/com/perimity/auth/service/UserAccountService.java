package com.perimity.auth.service;

import com.perimity.auth.messaging.UserEventPublisher;
import com.perimity.auth.dto.request.InternalIdentityBatchDto;
import com.perimity.auth.dto.request.InternalStudentBatchDto;
import com.perimity.auth.dto.request.InternalIdentityCreateDto;
import com.perimity.auth.dto.request.PasswordChangeDto;
import com.perimity.auth.dto.request.PasswordResetConfirmDto;
import com.perimity.auth.dto.request.PasswordResetRequestDto;
import com.perimity.auth.dto.request.UserCreateDto;
import com.perimity.auth.dto.request.UserStatusUpdateDto;
import com.perimity.auth.dto.request.UserUpdateDto;
import com.perimity.auth.dto.request.VisitorRegistrationDto;
import com.perimity.auth.dto.response.IdentityBatchResponseDto;
import com.perimity.auth.dto.response.IdentityBatchResponseDto.RowResult;
import com.perimity.auth.dto.response.PageResponse;
import com.perimity.auth.dto.response.UserResponse;
import com.perimity.auth.entity.PasswordReset;
import com.perimity.auth.entity.User;
import com.perimity.auth.entity.enums.AuditAction;
import com.perimity.auth.entity.enums.Role;
import com.perimity.auth.exception.AuthenticationFailedException;
import com.perimity.auth.exception.RateLimitedException;
import com.perimity.auth.exception.ResourceNotFoundException;
import com.perimity.auth.repository.BlocklistEntryRepository;
import com.perimity.auth.repository.PasswordResetRepository;
import com.perimity.auth.repository.UserRepository;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HexFormat;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Accounts and passwords.
 *
 * Nothing is ever hard-deleted. Accounts are deactivated, so the audit trail
 * and every pass they ever held stay meaningful.
 */
@Service
public class UserAccountService {

    private static final Logger log = LoggerFactory.getLogger(UserAccountService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordResetRepository resetRepository;
    private final BlocklistEntryRepository blocklistRepository;
    private final PasswordEncoder passwordEncoder;
    private final RateLimiter rateLimiter;
    private final AuditService audit;
    private final EmailService emailService;
    private final UserEventPublisher userEvents;
    private final int resetExpiryMinutes;
    private final int resetPerEmailPerDay;
    private final String resetUrlBase;
    /** Where an imported student is told to sign in. */
    private final String studentLoginUrl;

    public UserAccountService(UserRepository userRepository,
                              PasswordResetRepository resetRepository,
                              BlocklistEntryRepository blocklistRepository,
                              PasswordEncoder passwordEncoder,
                              RateLimiter rateLimiter,
                              AuditService audit,
                              EmailService emailService,
                              UserEventPublisher userEvents,
                              @Value("${perimity.password.reset-link-expiry-minutes}") int resetExpiryMinutes,
                              @Value("${perimity.ratelimit.reset.per-email-per-day}") int resetPerEmailPerDay,
                              @Value("${perimity.frontend.reset-password-url}") String resetUrlBase,
                              @Value("${perimity.frontend.login-url:http://localhost:5173/login}")
                              String studentLoginUrl) {
        this.userRepository = userRepository;
        this.resetRepository = resetRepository;
        this.blocklistRepository = blocklistRepository;
        this.passwordEncoder = passwordEncoder;
        this.rateLimiter = rateLimiter;
        this.audit = audit;
        this.emailService = emailService;
        this.userEvents = userEvents;
        this.resetExpiryMinutes = resetExpiryMinutes;
        this.resetPerEmailPerDay = resetPerEmailPerDay;
        this.resetUrlBase = resetUrlBase;
        this.studentLoginUrl = studentLoginUrl;
    }

    // --------------------------------------------------------- accounts

    /** An admin creates a Faculty, Guard, Student or Campus Admin account. */
    @Transactional
    public UserResponse create(UserCreateDto dto, Long actorUserId, Role actorRole) {
        if (userRepository.existsByEmailIgnoreCase(dto.getEmail())) {
            throw new IllegalArgumentException("An account already exists for that email.");
        }

        if (dto.getRole() == Role.CAMPUS_ADMIN && dto.getCampusId() != null) {
            long activeCount = userRepository.countByCampusIdAndRoleAndActiveTrue(dto.getCampusId(), Role.CAMPUS_ADMIN);
            if (activeCount > 0) {
                throw new IllegalArgumentException(
                        "This campus already has an active Campus Admin. Please suspend or deactivate the existing admin account before creating a new one.");
            }
        }

        String hash = dto.getRole().canLoginWithPassword()
                ? passwordEncoder.encode(dto.getTemporaryPassword())
                : null;

        User user = userRepository.save(User.builder()
                .email(dto.getEmail().toLowerCase())
                .name(dto.getName().trim())
                .phone(dto.getPhone())
                .role(dto.getRole())
                .campusId(dto.getCampusId())
                .passwordHash(hash)
                // An admin-set password is temporary by definition. The holder
                // must replace it before doing anything else.
                .mustChangePassword(hash != null)
                .active(true)
                .build());

        audit.record(AuditAction.ACCOUNT_CREATED, actorUserId, actorRole,
                user.getCampusId(), "user:" + user.getId(),
                "Created " + user.getRole() + " account");

        /*
         * A STUDENT or FACULTY account needs a matching profile in user-service.
         * This announces the account; user-service provisions the profile.
         *
         * It used to be the CALLER's job - the React Add Student screen made a
         * second API call after this one returned. Any account created any other
         * way, or whose second call failed, ended up able to sign in with no
         * profile and no way for anyone to create one. Several accounts on this
         * system are still in that state.
         *
         * Published after commit, and a broker failure does not fail account
         * creation. See UserEventPublisher.
         */
        userEvents.publishCreatedAfterCommit(user);

        return UserResponse.from(user);
    }

    /**
     * Visitor self-service (SRS v1.1).
     *
     * Blocklist checked here. Per FR-BLK-4 the refusal is deliberately vague -
     * telling someone they are blocklisted tells them which identity to avoid
     * using next.
     */
    @Transactional
    public UserResponse registerVisitor(VisitorRegistrationDto dto) {
        boolean blocked = blocklistRepository
                        .existsByCampusIdAndEmailIgnoreCase(dto.getCampusId(), dto.getEmail())
                || (dto.getPhone() != null
                        && blocklistRepository.existsByCampusIdAndPhone(dto.getCampusId(), dto.getPhone()));

        if (blocked) {
            audit.recordAnonymous(AuditAction.BLOCKED_REGISTRATION_ATTEMPT,
                    "email:" + dto.getEmail(), "Campus " + dto.getCampusId());
            throw new IllegalStateException(
                    "We could not complete your registration. Please contact the campus office.");
        }

        Optional<User> existing = userRepository.findByEmailIgnoreCase(dto.getEmail());
        if (existing.isPresent()) {
            // A pass is not a person. Somebody who visited last year already has
            // an identity, and reusing it is the whole point of keying on email.
            return UserResponse.from(existing.get());
        }

        User user = userRepository.save(User.builder()
                .email(dto.getEmail().toLowerCase())
                .name(dto.getName().trim())
                .phone(dto.getPhone())
                .role(Role.VISITOR)
                .campusId(dto.getCampusId())
                .passwordHash(null)
                .mustChangePassword(false)
                .active(true)
                .build());

        audit.recordAnonymous(AuditAction.ACCOUNT_CREATED,
                "user:" + user.getId(), "Visitor self-registration");

        return UserResponse.from(user);
    }

    // ------------------------------------------------- internal (Day 8)

    /**
     * The internal lookup other services need.
     *
     * GET /api/internal/auth/users/by-email is a read-only check: "does an
     * identity already exist for this email, and if so what is it." This is
     * what the bulk engine calls per row before deciding whether to reuse an
     * identity or create a new lightweight visitor (Event_Bulk_Design.md,
     * "The Mixed-Attendee Problem"). No audit row here - a lookup that finds
     * nothing has changed nothing.
     */
    @Transactional(readOnly = true)
    public Optional<UserResponse> findByEmailForInternal(String email) {
        return userRepository.findByEmailIgnoreCase(email).map(UserResponse::from);
    }

    /**
     * POST /api/internal/auth/users.
     *
     * Same resolve-or-create shape as registerVisitor, called by a service
     * instead of by a visitor. Idempotent by design: the bulk engine may call
     * this twice for the same row on a retry, and a second call must return
     * the same identity, not a duplicate account or an error.
     */
    @Transactional
    public IdentityResolution resolveOrCreateInternalIdentity(InternalIdentityCreateDto dto) {
        boolean blocked = blocklistRepository
                        .existsByCampusIdAndEmailIgnoreCase(dto.getCampusId(), dto.getEmail())
                || (dto.getPhone() != null
                        && blocklistRepository.existsByCampusIdAndPhone(dto.getCampusId(), dto.getPhone()));

        if (blocked) {
            audit.recordAnonymous(AuditAction.BLOCKED_REGISTRATION_ATTEMPT,
                    "email:" + dto.getEmail(),
                    "Blocked during internal identity resolution, campus " + dto.getCampusId()
                            + (dto.getSource() == null ? "" : ", source " + dto.getSource()));
            throw new IllegalStateException("This person cannot be registered for that campus.");
        }

        Optional<User> existing = userRepository.findByEmailIgnoreCase(dto.getEmail());
        if (existing.isPresent()) {
            return new IdentityResolution(UserResponse.from(existing.get()), false);
        }

        User user = userRepository.save(User.builder()
                .email(dto.getEmail().toLowerCase())
                .name(dto.getName().trim())
                .phone(dto.getPhone())
                .role(Role.VISITOR)
                .campusId(dto.getCampusId())
                .passwordHash(null)
                .mustChangePassword(false)
                .active(true)
                .build());

        audit.recordAnonymous(AuditAction.ACCOUNT_CREATED, "user:" + user.getId(),
                "Created via internal service call"
                        + (dto.getSource() == null ? "" : ", source " + dto.getSource()));

        return new IdentityResolution(UserResponse.from(user), true);
    }

    /** created tells the controller whether to answer 200 (reused) or 201 (new). */
    public record IdentityResolution(UserResponse user, boolean created) { }


    // ------------------------------------------------- Day 10, bulk resolve

    /**
     * Resolve or create a whole spreadsheet's identities in one call.
     *
     * This is the mixed-attendee problem from Event_Bulk_Design.md: 600 rows,
     * roughly 100 of whom are already members, and the faculty does not know
     * which. Per row, matched by email - existing identity reused, brand-new
     * email gets a lightweight VISITOR identity, blocklisted row skipped.
     *
     * FOUR QUERIES FOR THE WHOLE SHEET, whatever its size:
     *   1. this campus's blocked emails
     *   2. this campus's blocked phones
     *   3. every existing identity among the sheet's emails
     *   4. one batch insert for the genuinely new people
     *
     * Calling the single-row resolveOrCreateInternalIdentity 600 times would be
     * roughly 1,800 queries and 600 separate insert statements. It would also
     * throw on the first blocklisted row and abandon the rest of the sheet.
     *
     * NOTHING HERE THROWS FOR A BAD ROW. Every row gets an outcome and the
     * batch always completes - "never block the batch for a few bad ones".
     */
    /**
     * Create or resolve STUDENT accounts for a bulk import.
     *
     * ==================================================================
     *  HOW THIS DIFFERS FROM resolveOrCreateBatch BELOW
     * ==================================================================
     * That one makes lightweight VISITOR identities: no password, OTP only.
     * This one makes accounts that sign in with a password and hold a campus
     * pass, so three things change.
     *
     *   1. Each row carries its OWN temporary password, hashed here. A shared
     *      password across a batch would let any student in it sign in as any
     *      other until the first one changed theirs.
     *   2. mustChangePassword is set, so the generated value is good for
     *      exactly one sign-in.
     *   3. The blocklist is NOT consulted. It exists to keep named individuals
     *      out as VISITORS; a student on the roll is admitted by the
     *      institution, and refusing them here would silently drop somebody
     *      from an intake with no reason given - FR-BLK-4 forbids saying why,
     *      which is tolerable for a visitor and not for a student who is
     *      supposed to be on the course.
     *
     * The role is STUDENT because of which method this is. There is no role
     * parameter and there must not be one - see InternalStudentBatchDto.
     *
     * REUSED rather than "already exists, fail": students resubmit forms and
     * faculty re-upload sheets. A second account on the same address would
     * split one person's history and could issue them a second pass.
     */
    @Transactional
    public IdentityBatchResponseDto resolveOrCreateStudents(InternalStudentBatchDto request) {
        Long campusId = request.getCampusId();
        List<InternalStudentBatchDto.Row> rows = request.getRows();

        Set<String> sheetEmails = new LinkedHashSet<>();
        for (InternalStudentBatchDto.Row row : rows) {
            sheetEmails.add(row.getEmail().trim().toLowerCase());
        }

        Map<String, User> known = new HashMap<>();
        for (User existing : userRepository.findByEmailIn(sheetEmails)) {
            known.put(existing.getEmail(), existing);
        }

        Set<String> seenInThisBatch = new LinkedHashSet<>();
        List<User> toCreate = new ArrayList<>();
        List<IdentityBatchResponseDto.RowResult> results = new ArrayList<>(rows.size());

        // Email to row number, so the second pass can report against the sheet.
        Map<String, InternalStudentBatchDto.Row> newRowsByEmail = new LinkedHashMap<>();

        for (InternalStudentBatchDto.Row row : rows) {
            String email = row.getEmail().trim().toLowerCase();

            User existing = known.get(email);
            if (existing != null) {
                /*
                 * ==============================================================
                 *  A REUSED ROW IS STILL SOMEBODY WHO NEEDS TELLING
                 * ==============================================================
                 * This branch used to record REUSED and move on, sending
                 * nothing. It looked harmless because the account already
                 * existed - but re-importing is the normal case, not the odd
                 * one: students resubmit the form, faculty fix a column and
                 * upload again. Those students ended up with a verified
                 * profile, a gate pass, and no idea any of it had happened.
                 * The batch reported success and the person it was for heard
                 * nothing.
                 *
                 * So they get the same email as a new student. What is IN it
                 * depends on one flag, and that distinction is the whole point
                 * of this block.
                 */
                if (existing.isMustChangePassword()) {
                    /*
                     * They have never completed a first sign-in, so the only
                     * password on this account is one an import generated and
                     * emailed - possibly weeks ago, in a message they deleted.
                     * Replacing it costs them nothing and is the only way to
                     * get them a credential they actually hold.
                     */
                    existing.setPasswordHash(passwordEncoder.encode(row.getTemporaryPassword()));
                    userRepository.save(existing);

                    audit.record(AuditAction.PASSWORD_CHANGED, request.getUploadedBy(),
                            Role.FACULTY, campusId, "user:" + existing.getId(),
                            "Temporary password reissued by "
                                    + (request.getSource() == null
                                            ? "a bulk import" : request.getSource()));

                    emailService.sendStudentWelcome(existing.getEmail(), existing.getName(),
                            row.getTemporaryPassword(), studentLoginUrl, false);
                } else {
                    /*
                     * They have set their own password, and it is a bcrypt hash
                     * - unrecoverable by design, so there is nothing to send
                     * them and nothing worth resetting.
                     *
                     * Overwriting it was considered and rejected. It would make
                     * every re-import a silent mass password reset, locking out
                     * every student already using the system on the day faculty
                     * fixed a typo in one column. A person who has a working
                     * password does not need a new one; they need to be told
                     * their details were updated.
                     */
                    emailService.sendStudentWelcome(existing.getEmail(), existing.getName(),
                            null, studentLoginUrl, true);
                }

                results.add(new IdentityBatchResponseDto.RowResult(
                        row.getRowNumber(), email, "REUSED", existing.getId()));
                continue;
            }

            /*
             * users.email is UNIQUE, so two rows with the same new address
             * would make saveAll throw and roll back every account in the
             * batch. One repeated address must not cost two hundred students
             * their accounts, so the duplicate is recorded and skipped here.
             */
            if (!seenInThisBatch.add(email)) {
                results.add(new IdentityBatchResponseDto.RowResult(
                        row.getRowNumber(), email, "DUPLICATE", null));
                continue;
            }

            toCreate.add(User.builder()
                    .email(email)
                    .name(row.getName().trim())
                    .phone(row.getPhone() == null || row.getPhone().isBlank()
                            ? null : row.getPhone().trim())
                    .role(Role.STUDENT)
                    .campusId(campusId)
                    .passwordHash(passwordEncoder.encode(row.getTemporaryPassword()))
                    // Generated by an import and emailed in plain text. It must
                    // survive exactly one sign-in.
                    .mustChangePassword(true)
                    .active(true)
                    .build());
            newRowsByEmail.put(email, row);
        }

        for (User created : userRepository.saveAll(toCreate)) {
            InternalStudentBatchDto.Row row = newRowsByEmail.get(created.getEmail());
            results.add(new IdentityBatchResponseDto.RowResult(
                    row == null ? null : row.getRowNumber(),
                    created.getEmail(), "CREATED", created.getId()));

            audit.record(AuditAction.ACCOUNT_CREATED, request.getUploadedBy(), Role.FACULTY,
                    campusId, "user:" + created.getId(),
                    "Created STUDENT account from "
                            + (request.getSource() == null ? "a bulk import" : request.getSource()));

            /*
             * Same event the single-account path publishes, so an imported
             * student gets a profile the same way anyone else does. Without
             * this, a bulk import would recreate the orphaned-account problem
             * two hundred rows at a time.
             */
            userEvents.publishCreatedAfterCommit(created);

            /*
             * Their sign-in details, or the account is unreachable.
             *
             * Sent from HERE rather than from user-service because this is the
             * only place the plain password exists. Returning it across a
             * service boundary so the caller could send the email would mean
             * putting live credentials in an HTTP response body and, briefly,
             * in whatever logs that response - for no gain.
             *
             * The email is fired per row rather than batched because
             * EmailService swallows its own failures: one bad mailbox must not
             * cost the other 199 students their notification, and a batched
             * send has no obvious way to be partially successful.
             */
            if (row != null) {
                emailService.sendStudentWelcome(
                        created.getEmail(),
                        created.getName(),
                        row.getTemporaryPassword(),
                        studentLoginUrl,
                        false);
            }
        }

        // Back into sheet order. Rows were split across two passes above, and a
        // report that jumps around is one nobody can check against the file.
        results.sort(java.util.Comparator.comparing(
                r -> r.rowNumber() == null ? Integer.MAX_VALUE : r.rowNumber()));

        return IdentityBatchResponseDto.of(results);
    }

    @Transactional
    public IdentityBatchResponseDto resolveOrCreateBatch(InternalIdentityBatchDto request) {
        Long campusId = request.getCampusId();
        List<InternalIdentityBatchDto.Row> rows = request.getRows();

        Set<String> blockedEmails = blocklistRepository.findBlockedEmails(campusId);
        Set<String> blockedPhones = blocklistRepository.findBlockedPhones(campusId);

        // Every distinct email in the sheet, lowercased once here so nothing
        // downstream has to think about case again.
        Set<String> sheetEmails = new LinkedHashSet<>();
        for (InternalIdentityBatchDto.Row row : rows) {
            sheetEmails.add(row.getEmail().trim().toLowerCase());
        }

        Map<String, Long> known = new HashMap<>();
        for (User existing : userRepository.findByEmailIn(sheetEmails)) {
            known.put(existing.getEmail(), existing.getId());
        }

        /*
         * Intra-batch duplicates are handled here rather than left to the
         * caller, and that is not defensive politeness - it is required.
         *
         * users.email is UNIQUE. Two rows with the same new email, both queued
         * for insert, means saveAll throws a constraint violation and the whole
         * transaction rolls back. One repeated address in a 600-row sheet would
         * cost all 600 identities. Tushar validates duplicates in the fast
         * phase too, but this service performs the insert, so this service has
         * to be the one that cannot be made to violate its own constraint.
         */
        Set<String> seenInThisBatch = new LinkedHashSet<>();
        List<User> toCreate = new ArrayList<>();
        List<PendingRow> pending = new ArrayList<>(rows.size());

        for (InternalIdentityBatchDto.Row row : rows) {
            String email = row.getEmail().trim().toLowerCase();
            String phone = row.getPhone() == null ? null : row.getPhone().trim();

            boolean blocked = blockedEmails.contains(email)
                    || (phone != null && blockedPhones.contains(phone));
            if (blocked) {
                pending.add(new PendingRow(row, email, Outcome.REFUSED));
                continue;
            }

            /*
             * Duplicate check BEFORE the existing-identity check, deliberately.
             * The other order looks equivalent and is not: a sheet listing the
             * same already-registered member twice would return two REUSED rows
             * with the same id, and the caller would try to issue that person
             * two event passes. "Already seen in this batch" is the honest
             * outcome whether the email was new or known.
             */
            if (!seenInThisBatch.add(email)) {
                pending.add(new PendingRow(row, email, Outcome.DUPLICATE));
                continue;
            }

            if (known.containsKey(email)) {
                pending.add(new PendingRow(row, email, Outcome.REUSED));
                continue;
            }

            toCreate.add(User.builder()
                    .email(email)
                    .name(row.getName().trim())
                    .phone(phone)
                    .role(Role.VISITOR)
                    .campusId(campusId)
                    .passwordHash(null)
                    .mustChangePassword(false)
                    .active(true)
                    .build());
            pending.add(new PendingRow(row, email, Outcome.CREATED));
        }

        // One insert for everyone genuinely new.
        for (User created : userRepository.saveAll(toCreate)) {
            known.put(created.getEmail(), created.getId());
        }

        List<RowResult> results = new ArrayList<>(pending.size());
        for (PendingRow p : pending) {
            Integer n = p.row().getRowNumber();
            String shown = p.row().getEmail();
            switch (p.outcome()) {
                // No reason, ever. FR-BLK-4.
                case REFUSED -> results.add(RowResult.refused(n, shown));
                case REUSED -> results.add(RowResult.reused(n, shown, known.get(p.email())));
                case CREATED -> results.add(RowResult.created(n, shown, known.get(p.email())));
                case DUPLICATE -> results.add(RowResult.duplicate(n, shown, known.get(p.email())));
            }
        }

        IdentityBatchResponseDto response = IdentityBatchResponseDto.of(results);

        // One row for the batch, for the same reason as bulk screening: 600
        // ACCOUNT_CREATED rows would bury every other entry from that day.
        audit.recordAnonymous(AuditAction.BULK_IDENTITY_RESOLVED,
                "campus:" + campusId,
                response.createdCount() + " created, " + response.reusedCount() + " reused, "
                        + response.refusedCount() + " refused, "
                        + response.duplicateCount() + " duplicate, of "
                        + response.totalRows() + " rows"
                        + (request.getSource() == null ? "" : ", source " + request.getSource()));

        log.info("Bulk identity resolve for campus {}: {} created, {} reused, {} refused, "
                        + "{} duplicate, of {} rows",
                campusId, response.createdCount(), response.reusedCount(),
                response.refusedCount(), response.duplicateCount(), response.totalRows());

        return response;
    }

    private enum Outcome { REUSED, CREATED, REFUSED, DUPLICATE }

    /** One row's decision, held until the inserts have run and ids exist. */
    private record PendingRow(InternalIdentityBatchDto.Row row, String email, Outcome outcome) { }

    // ------------------------------------------------------------ reads

    @Transactional
    public UserResponse update(Long id, UserUpdateDto dto) {
        User user = require(id);
        user.setName(dto.getName().trim());
        user.setPhone(dto.getPhone());
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public UserResponse changeStatus(Long id, UserStatusUpdateDto dto, Long actorUserId, Role actorRole) {
        User user = require(id);

        if (user.isActive() == dto.getActive()) {
            throw new IllegalStateException("This account is already "
                    + (user.isActive() ? "active" : "inactive") + ".");
        }

        if (dto.getActive() && user.getRole() == Role.CAMPUS_ADMIN && user.getCampusId() != null) {
            long activeCount = userRepository.countByCampusIdAndRoleAndActiveTrue(user.getCampusId(), Role.CAMPUS_ADMIN);
            if (activeCount > 0) {
                throw new IllegalArgumentException(
                        "This campus already has an active Campus Admin. Please suspend the other admin before activating this one.");
            }
        }

        user.setActive(dto.getActive());
        if (!dto.getActive()) {
            // Deactivating clears the lock so reactivation is not blocked by a
            // stale lockout from months ago.
            user.setLockedUntil(null);
            user.setFailedLoginCount(0);
        }
        userRepository.save(user);

        audit.record(dto.getActive() ? AuditAction.ACCOUNT_CREATED : AuditAction.ACCOUNT_DEACTIVATED,
                actorUserId, actorRole, user.getCampusId(), "user:" + user.getId(),
                dto.getReason());

        return UserResponse.from(user);
    }

    @Transactional(readOnly = true)
    public UserResponse getOne(Long id) {
        return UserResponse.from(require(id));
    }

    @Transactional(readOnly = true)
    /**
     * The same listing, limited to the roles the caller may see.
     *
     * A single role, when given, has already been checked against this set by
     * the controller - so it narrows the set rather than escaping it.
     */
    public PageResponse<UserResponse> byCampus(Long campusId, Role role,
                                               java.util.Set<Role> visible, Pageable pageable) {
        if (role != null) {
            return byCampus(campusId, role, pageable);
        }
        return PageResponse.from(
                userRepository.findByCampusIdAndRoleInOrderByNameAsc(campusId, visible, pageable),
                UserResponse::from);
    }

    public PageResponse<UserResponse> byCampus(Long campusId, Role role, Pageable pageable) {
        if (campusId == null) {
            return PageResponse.from(
                    role == null
                            ? userRepository.findAll(pageable)
                            : userRepository.findByRoleOrderByNameAsc(role, pageable),
                    UserResponse::from);
        }
        return PageResponse.from(
                role == null
                        ? userRepository.findByCampusIdOrderByNameAsc(campusId, pageable)
                        : userRepository.findByCampusIdAndRoleOrderByNameAsc(campusId, role, pageable),
                UserResponse::from);
    }

    // -------------------------------------------------------- passwords

    /** A signed-in user changes their own. The account comes from the token. */
    @Transactional
    public void changePassword(Long userId, PasswordChangeDto dto) {
        User user = require(userId);

        if (user.getPasswordHash() == null
                || !passwordEncoder.matches(dto.getCurrentPassword(), user.getPasswordHash())) {
            throw new AuthenticationFailedException("Your current password is not correct.");
        }

        user.setPasswordHash(passwordEncoder.encode(dto.getNewPassword()));
        user.setMustChangePassword(false);
        userRepository.save(user);

        // Any outstanding reset link is now void. Otherwise an old emailed link
        // could undo a password the owner just set.
        resetRepository.findByUserIdAndUsedFalse(userId).forEach(r -> {
            r.setUsed(true);
            r.setUsedAt(LocalDateTime.now());
            resetRepository.save(r);
        });

        audit.record(AuditAction.PASSWORD_CHANGED, userId, user.getRole(),
                user.getCampusId(), "user:" + userId, null);
    }

    /**
     * Request a reset link.
     *
     * ALWAYS returns quietly, whether or not the address exists. Anything else
     * makes this an account-enumeration tool.
     *
     * Day 9: the link is now emailed instead of logged. The Optional<String>
     * return is kept as-is - AuthController already ignores it - but nothing
     * outside EmailService sees the plain token any more.
     */
    @Transactional
    public Optional<String> requestPasswordReset(PasswordResetRequestDto dto) {
        if (!rateLimiter.allow("reset-email", dto.getEmail(), resetPerEmailPerDay, Duration.ofDays(1))) {
            throw new RateLimitedException("Too many reset requests for this address.",
                    rateLimiter.secondsUntilReset("reset-email", dto.getEmail()));
        }

        Optional<User> maybeUser = userRepository.findByEmailIgnoreCaseAndActiveTrue(dto.getEmail());
        if (maybeUser.isEmpty() || !maybeUser.get().getRole().canLoginWithPassword()) {
            audit.recordAnonymous(AuditAction.PASSWORD_RESET_REQUESTED,
                    "email:" + dto.getEmail(), "No eligible account - no link sent");
            return Optional.empty();
        }

        User user = maybeUser.get();

        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String plainToken = AuthService.sha256(HexFormat.of().formatHex(raw));

        resetRepository.save(PasswordReset.builder()
                .userId(user.getId())
                .tokenHash(AuthService.sha256(plainToken))
                .expiresAt(LocalDateTime.now().plusMinutes(resetExpiryMinutes))
                .build());

        audit.record(AuditAction.PASSWORD_RESET_REQUESTED, user.getId(), user.getRole(),
                user.getCampusId(), "user:" + user.getId(), null);

        String resetLink = resetUrlBase + "?token=" + plainToken;
        emailService.sendPasswordResetLink(dto.getEmail(), resetLink, resetExpiryMinutes);

        return Optional.of(plainToken);
    }

    /** Complete a reset. The token is looked up by its hash - only the hash is stored. */
    @Transactional
    public void confirmPasswordReset(PasswordResetConfirmDto dto) {
        PasswordReset reset = resetRepository
                .findByTokenHashAndUsedFalse(AuthService.sha256(dto.getToken()))
                .orElseThrow(() -> new AuthenticationFailedException(
                        "That reset link is not valid. Please request a new one."));

        if (!reset.isUsableAt(LocalDateTime.now())) {
            throw new AuthenticationFailedException(
                    "That reset link has expired. Please request a new one.");
        }

        User user = require(reset.getUserId());
        user.setPasswordHash(passwordEncoder.encode(dto.getNewPassword()));
        user.setMustChangePassword(false);
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        userRepository.save(user);

        reset.setUsed(true);
        reset.setUsedAt(LocalDateTime.now());
        resetRepository.save(reset);

        audit.record(AuditAction.PASSWORD_CHANGED, user.getId(), user.getRole(),
                user.getCampusId(), "user:" + user.getId(), "Completed via reset link");
    }

    private User require(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Account", id));
    }
}
