package com.perimity.user.client;

import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Declarative call into auth-service, for bulk student onboarding.
 *
 * ==========================================================================
 * WHY user-service CREATES ACCOUNTS AT ALL
 * ==========================================================================
 * It does not. It asks auth-service to, which is the only service allowed to
 * write a users row. The import runs here because it owns student profiles and
 * the verification state machine, but the account half of each row has to go
 * through the service that owns accounts.
 *
 * ==========================================================================
 * WHY THIS IS NOT THE FACULTY'S JWT
 * ==========================================================================
 * The tempting alternative was to forward the uploading faculty's token, so
 * accounts would be created under their own authority and auth-service's
 * existing role rules would apply unchanged. It reads better and it does not
 * work: the import is a background job over hundreds of rows with Drive
 * fetches, passes and emails in it, and a JWT expires long before that
 * finishes. Rows would start failing partway through a batch, apparently at
 * random.
 *
 * So the internal key, and a dedicated endpoint that can only ever produce a
 * STUDENT. uploadedBy carries the faculty member's id so the audit trail still
 * names a person rather than a service.
 *
 * The contextId matters: two @FeignClient interfaces without distinct ones
 * collide at startup with a bean name clash that reads as a mystery.
 */
@FeignClient(
        name = "auth-service",
        contextId = "userStudentImport",
        url = "${perimity.services.auth-url:}",
        configuration = FeignSupportConfig.class)
public interface AuthFeignClient {

    @PostMapping("/api/internal/auth/users/students")
    StudentBatchEnvelope createStudents(@RequestBody StudentBatchRequest request);

    /**
     * Does an account already exist for this email?
     *
     * ======================================================================
     * AN ENDPOINT THAT ALREADY EXISTED, CALLED ONCE PER ROW
     * ======================================================================
     * Validation needs this to tell a real roll-number collision from a
     * student resubmitting their own form: this service knows profiles by
     * account id, a spreadsheet only carries emails, and without the mapping
     * every returning student is rejected.
     *
     * A batch endpoint taking the whole sheet would be one round trip instead
     * of one per row, and that was the first implementation. It was removed
     * on purpose - it meant a new DTO and a new method on auth-service's
     * service layer, and this project's rule is that those stay as they are.
     * GET /by-email has been there since Day 8 and answers the same question.
     *
     * The cost is real and worth stating: a 200-row intake makes 200 calls
     * during validation rather than one. They are small, local and only
     * happen when a person uploads a sheet, so seconds rather than
     * milliseconds on the preview screen. If that ever becomes the thing
     * people complain about, the batch endpoint is the fix.
     *
     * 404 IS A NORMAL ANSWER here, not an error - it is how the endpoint says
     * "new student". Feign turns it into FeignException.NotFound, so the
     * caller must catch it rather than let it fail the upload.
     */
    @GetMapping("/api/internal/auth/users/by-email")
    UserEnvelope findByEmail(@RequestParam("email") String email);

    /**
     * The account behind an id, for the name a pass has to carry.
     *
     * A student's pass shows auth-service's User.name - the authoritative name,
     * the one an entry log records. StudentProfile's first/last name are
     * self-declared detail that a freshly created profile does not have yet, so
     * building the holder name from them would put an empty string on the pass
     * of every student added through the Add Student screen.
     *
     * The endpoint is named /email because gatepass-service added it wanting an
     * address. It answers with the whole UserResponse, so it serves here too
     * rather than justifying a second endpoint that returns the same row.
     */
    @GetMapping("/api/internal/auth/users/{userId}/email")
    UserEnvelope findById(@PathVariable("userId") Long userId);

    /**
     * Only the fields this service actually uses.
     *
     * auth-service's UserResponse carries more - name, phone, campus, lock
     * state. They are deliberately absent here: Jackson ignores what a record
     * does not declare, and a contract copy that lists fields nobody reads is
     * one that has to be maintained for no reason.
     */
    record UserEnvelope(boolean success, String message, UserView data) { }

    record UserView(Long id, String email, String name, boolean mustChangePassword) { }

    /**
     * Mirrors InternalStudentBatchDto in auth-service. A copy, like every other
     * cross-service contract here - Jackson matches by field NAME, so keep the
     * names identical and change both in one commit.
     *
     * There is no role field. The endpoint decides, and a field that is ignored
     * is a field somebody eventually trusts.
     */
    record StudentBatchRequest(
            Long campusId,
            Long uploadedBy,
            String source,
            List<Row> rows) {

        public record Row(
                Integer rowNumber,
                String email,
                String name,
                String phone,
                /**
                 * One per row, generated by the caller. A batch sharing a
                 * password would let any student in it sign in as any other
                 * until the first one changed theirs.
                 */
                String temporaryPassword) { }
    }

    record StudentBatchEnvelope(boolean success, String message, StudentBatchResult data) { }

    record StudentBatchResult(
            int totalRows,
            int reusedCount,
            int createdCount,
            int refusedCount,
            int duplicateCount,
            List<RowResult> results) { }

    /**
     * outcome is REUSED, CREATED, REFUSED or DUPLICATE.
     *
     * A String rather than an enum on purpose: auth-service adding an outcome
     * this service has never heard of should not make the whole batch
     * unparseable. An unknown value can be reported against the row; a
     * deserialisation failure loses every row in the response.
     */
    record RowResult(Integer rowNumber, String email, String outcome, Long userId) { }
}
