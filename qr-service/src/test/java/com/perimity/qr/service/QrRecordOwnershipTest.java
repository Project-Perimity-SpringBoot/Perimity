package com.perimity.qr.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.perimity.qr.entity.QrRecord;
import com.perimity.qr.repository.QrRecordRepository;
import com.perimity.qr.security.PerimityPrincipal;
import com.perimity.qr.storage.StorageService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * PROPOSAL - the ownership rule on GET /api/qr/{passId} and the two download
 * endpoints.
 *
 * Plain JUnit with the SecurityContext set by hand rather than @WithMockUser:
 * the rule reads PerimityPrincipal.userId, and @WithMockUser installs a Spring
 * User, so a MockMvc test would be exercising a principal type this code never
 * sees in production.
 */
@ExtendWith(MockitoExtension.class)
class QrRecordOwnershipTest {

    private static final Long PASS = 41L;
    private static final Long OWNER = 7L;

    @Mock private QrRecordRepository qrRecordRepository;
    @Mock private QrTokenService qrTokenService;
    @Mock private QrImageService qrImageService;
    @Mock private PdfDocumentService pdfDocumentService;
    @Mock private StorageService storageService;

    private QrRecordService service() {
        return new QrRecordService(qrRecordRepository, qrTokenService,
                qrImageService, pdfDocumentService, storageService);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void signedInAs(Long userId, String role) {
        PerimityPrincipal principal = new PerimityPrincipal(
                userId, "u" + userId + "@example.com", "User " + userId, role, 2L);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    /**
     * download() takes its caller as a parameter rather than reading the
     * SecurityContext - the two read paths learn who is asking by different
     * routes. This hands it the same principal signedInAs installed, so a test
     * still expresses "the signed-in caller" once.
     */
    private PerimityPrincipal signedInPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? null : (PerimityPrincipal) auth.getPrincipal();
    }

    private void givenPassHeldBy(Long holderUserId) {
        given(qrRecordRepository.findByPassIdAndActiveTrue(PASS)).willReturn(
                Optional.of(QrRecord.builder()
                        .passId(PASS).campusId(2L)
                        .holderUserId(holderUserId)
                        .qrKey("2/qr/41/1.png").pdfKey("2/pdf/41/1.pdf")
                        .active(true)
                        .build()));
    }

    // ---------------------------------------------------------------
    // The rule
    // ---------------------------------------------------------------

    @Test
    void holderMayReadTheirOwnPass() {
        givenPassHeldBy(OWNER);
        signedInAs(OWNER, "STUDENT");

        assertThat(service().getActiveByPassId(PASS).getPassId()).isEqualTo(PASS);
    }

    /**
     * The defect this proposal exists to close. Before it, this returned the
     * other holder's QR keys, campus and validity window quite happily.
     */
    @Test
    void aDifferentHolderMayNotReadIt() {
        givenPassHeldBy(OWNER);
        signedInAs(99L, "STUDENT");

        assertThatThrownBy(() -> service().getActiveByPassId(PASS))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("belongs to another holder");
    }

    /**
     * The download endpoints matter more than the metadata one: this hands over
     * the QR image and the pass PDF themselves - the credential, not a
     * description of it.
     */
    @Test
    void aDifferentHolderMayNotDownloadThePdfOrTheImage() {
        givenPassHeldBy(OWNER);
        signedInAs(99L, "STUDENT");

        assertThatThrownBy(() -> service().download(PASS, true, signedInPrincipal()))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service().download(PASS, false, signedInPrincipal()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void visitorsAreScopedTheSameWayAsStudents() {
        givenPassHeldBy(OWNER);
        signedInAs(99L, "VISITOR");

        assertThatThrownBy(() -> service().getActiveByPassId(PASS))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ---------------------------------------------------------------
    // Staff pass through
    // ---------------------------------------------------------------

    /**
     * A guard on a manual lookup, an admin in the audit log and a faculty
     * member checking a batch are all legitimately looking at passes that are
     * not theirs. Scoping them out would break the product, not secure it.
     */
    @Test
    void staffMayReadAnyPass() {
        for (String role : List.of("FACULTY", "CAMPUS_ADMIN", "SUPER_ADMIN")) {
            givenPassHeldBy(OWNER);
            signedInAs(99L, role);

            assertThatCode(() -> service().getActiveByPassId(PASS))
                    .as("%s should be allowed", role)
                    .doesNotThrowAnyException();
        }
    }

    /**
     * The queue consumer and the internal API-key endpoints reach this service
     * with no SecurityContext at all. Neither is a person whose ownership could
     * be checked, and failing them closed would stop QR generation outright.
     */
    @Test
    void anUnauthenticatedCallerIsNotScoped() {
        givenPassHeldBy(OWNER);

        assertThatCode(() -> service().getActiveByPassId(PASS)).doesNotThrowAnyException();
    }

    // ---------------------------------------------------------------
    // The open question
    // ---------------------------------------------------------------

    /**
     * Documents the fail-open choice rather than endorsing it.
     *
     * Every row written before holder_user_id existed has no holder, so this is
     * the behaviour for every pass currently in the database: still readable by
     * any authenticated user. If the team decides to fail closed after
     * gatepass-service backfills the column, THIS TEST SHOULD FLIP to expect
     * AccessDeniedException - it is the marker for that decision, not a
     * statement that fail-open is correct.
     */
    @Test
    void aPassWithNoRecordedHolderIsReadableByAnyone_failOpen_pendingBackfill() {
        givenPassHeldBy(null);
        signedInAs(99L, "STUDENT");

        assertThatCode(() -> service().getActiveByPassId(PASS)).doesNotThrowAnyException();
    }
}
