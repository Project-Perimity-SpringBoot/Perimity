package com.perimity.gatepass.bulk;

import com.perimity.gatepass.client.InternalServiceClient;
import com.perimity.gatepass.dto.response.RowErrorResponse;
import com.perimity.gatepass.repository.GatePassRepository;
import com.perimity.gatepass.validation.ValidationPatterns;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Checks every row and separates the good from the bad.
 *
 * ==========================================================================
 *  NEVER BLOCK THE BATCH FOR A FEW BAD ROWS.
 * ==========================================================================
 *
 * This is the rule stated in the Event & Bulk design document and it is the
 * same rule the expiry sweep follows. 580 valid rows are processed; the 20 bad
 * ones come back in a downloadable report so the uploader fixes only those and
 * re-uploads them. An exception thrown on row 34 that abandons the other 599
 * is the failure mode this whole class exists to prevent.
 *
 * FOUR CHECKS, cheapest first:
 *
 *   1. shape        - is the name/email/phone the right SHAPE (regex, in-memory)
 *   2. duplicate    - is this email twice in THIS sheet (in-memory set)
 *   3. already has  - does this person already hold a pass for this event (1 query)
 *   4. blocklist    - is this email barred at this campus (1 call per row, cached)
 *
 * ON QUERY COUNT: check 3 is deliberately ONE query for the whole sheet, not
 * one per row. The obvious implementation calls
 * existsByHolderUserIdAndEventIdAndStatusNot inside the loop, which is 600
 * round trips for a 600-row sheet and turns a two-second validation into a
 * two-minute one. Instead every holder already passed for this event is
 * fetched once into a Set. This is the classic N+1 and it is very easy to
 * reintroduce by "tidying" this class.
 */
@Service
public class BulkValidationService {

    private static final Logger log = LoggerFactory.getLogger(BulkValidationService.class);

    /** Compiled once. Compiling a regex 600 times per upload is pure waste. */
    private static final Pattern EMAIL = Pattern.compile(ValidationPatterns.EMAIL);
    private static final Pattern PERSON_NAME = Pattern.compile(ValidationPatterns.PERSON_NAME);
    private static final Pattern PHONE = Pattern.compile(ValidationPatterns.PHONE);

    /**
     * How many errors travel back in the JSON summary. The rest are in the
     * downloadable CSV. A sheet where all 5000 rows are broken must not put a
     * 5000-element array through the browser.
     */
    public static final int MAX_ERRORS_INLINE = 50;

    private final GatePassRepository passRepository;
    private final InternalServiceClient internal;

    public BulkValidationService(GatePassRepository passRepository,
                                 InternalServiceClient internal) {
        this.passRepository = passRepository;
        this.internal = internal;
    }

    /**
     * The outcome of a validation pass.
     *
     * valid is a LinkedHashMap keyed by lowercased email, so the sheet's own
     * order is preserved (the uploader's row order is the order they will read
     * the progress in) while duplicate detection stays O(1).
     */
    public record Outcome(
            List<ParsedRow> valid,
            List<RowErrorResponse> errors,
            int totalRows
    ) {
        public int validCount() {
            return valid.size();
        }
        public int invalidCount() {
            return errors.size();
        }
        /** Only the first MAX_ERRORS_INLINE, for the JSON response. */
        public List<RowErrorResponse> inlineErrors() {
            return errors.size() <= MAX_ERRORS_INLINE
                    ? errors
                    : errors.subList(0, MAX_ERRORS_INLINE);
        }
    }

    /**
     * @param eventId null for a DAILY student batch - check 3 is skipped, since
     *                "already has a pass for this event" is meaningless without
     *                an event.
     */
    public Outcome validate(List<ParsedRow> rows, Long campusId, Long eventId) {

        Map<String, ParsedRow> valid = new LinkedHashMap<>();
        List<RowErrorResponse> errors = new ArrayList<>();
        Set<String> seenInSheet = new HashSet<>();

        // ONE query for the whole sheet. See the class comment.
        Set<Long> alreadyPassed = eventId == null
                ? Set.of()
                : new HashSet<>(passRepository.findHolderUserIdsWithLivePassForEvent(eventId));

        // The blocklist answer is memoised per email so a sheet listing the
        // same barred address twenty times makes one call, not twenty.
        Map<String, Boolean> blockedCache = new java.util.HashMap<>();

        for (ParsedRow row : rows) {

            String reason = shapeProblem(row);

            if (reason == null && !seenInSheet.add(row.emailKey())) {
                reason = "This email appears more than once in the sheet";
            }

            if (reason == null && isBlocked(blockedCache, campusId, row)) {
                reason = "This email is on this campus's blocklist";
            }

            if (reason == null && eventId != null) {
                // Only resolvable for someone who already has an identity; a
                // brand-new email cannot hold a pass yet, so no lookup needed.
                Long existingUserId = internal.findUserIdByEmail(row.emailKey()).orElse(null);
                if (existingUserId != null && alreadyPassed.contains(existingUserId)) {
                    reason = "This person already holds a pass for this event";
                }
            }

            if (reason == null) {
                valid.put(row.emailKey(), row);
            } else {
                errors.add(RowErrorResponse.of(row.rowNumber(), row.email(), reason));
            }
        }

        log.info("Validated {} row(s) for campus {}: {} valid, {} rejected",
                rows.size(), campusId, valid.size(), errors.size());

        return new Outcome(List.copyOf(valid.values()), errors, rows.size());
    }

    // ------------------------------------------------------------- checks

    /**
     * Shape only. Regex validates SHAPE, never MEANING - the project rule.
     *
     * "Does this address exist" and "is this person allowed in" are not
     * questions a pattern can answer, and pretending otherwise is how you end
     * up rejecting legitimate addresses.
     */
    private String shapeProblem(ParsedRow row) {

        if (row.name() == null) {
            return "Name is required";
        }
        if (row.name().length() > 120) {
            return "Name is longer than 120 characters";
        }
        if (!PERSON_NAME.matcher(row.name()).matches()) {
            return "Name contains characters that are not allowed";
        }

        if (row.email() == null) {
            return "Email is required";
        }
        if (row.email().length() > 180) {
            return "Email is longer than 180 characters";
        }
        if (!EMAIL.matcher(row.email()).matches()) {
            return "Not a valid email address";
        }

        // Phone and purpose are optional. Only checked when present - an empty
        // optional column must not fail a row.
        if (row.phone() != null && !PHONE.matcher(row.phone()).matches()) {
            return "Not a valid phone number";
        }
        if (row.purpose() != null && row.purpose().length() > 300) {
            return "Purpose is longer than 300 characters";
        }

        return null;
    }

    /**
     * Blocklist check, memoised.
     *
     * FAILS OPEN, and that is a decision worth defending rather than an
     * oversight: if auth-service is unreachable the row is allowed through.
     * The alternative is that one service restarting rejects all 600 rows of a
     * legitimate upload as "blocklisted", which is both wrong and alarming.
     * A barred visitor who slips into a batch is still stopped at the gate,
     * because the guard scan checks pass status live.
     *
     * The unavailability is logged loudly so it is not invisible.
     */
    private boolean isBlocked(Map<String, Boolean> cache, Long campusId, ParsedRow row) {
        return cache.computeIfAbsent(row.emailKey(), email ->
                internal.isBlocklisted(campusId, email, row.phone()).orElseGet(() -> {
                    log.warn("Blocklist check unavailable for {} - allowing the row through", email);
                    return false;
                }));
    }
}
