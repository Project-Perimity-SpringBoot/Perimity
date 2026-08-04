package com.perimity.qr.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.perimity.qr.entity.QrRecord;
import com.perimity.qr.repository.QrRecordRepository;
import com.perimity.qr.security.PerimityPrincipal;
import com.perimity.qr.storage.StorageService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

/**
 * Who may download a pass PDF.
 *
 * These bytes are the entry credential - the PDF carries a QR that opens a
 * gate. Before this rule existed the endpoint took only a passId, and a
 * signed-in student was able to fetch another holder's pass and scan in as
 * them. Every case below is that hole stated as an assertion.
 */
class QrRecordDownloadAuthorizationTest {

    private static final byte[] PDF = "%PDF-1.4 pass".getBytes();

    private static final Long HOLDER = 8L;
    private static final Long PASS = 4L;

    private QrRecordRepository repository;
    private StorageService storage;
    private QrRecordService service;

    private static PerimityPrincipal user(Long id, String role) {
        return new PerimityPrincipal(id, id + "@example.com", "Test User", role, 1L);
    }

    private QrRecord record(Long holderUserId) {
        QrRecord record = new QrRecord();
        record.setPassId(PASS);
        record.setCampusId(1L);
        record.setHolderUserId(holderUserId);
        record.setPdfKey("1/pdf/4/2.pdf");
        record.setQrKey("1/qr/4/2.png");
        return record;
    }

    @BeforeEach
    void setUp() {
        repository = mock(QrRecordRepository.class);
        storage = mock(StorageService.class);
        service = new QrRecordService(repository,
                mock(QrTokenService.class),
                mock(QrImageService.class),
                mock(PdfDocumentService.class),
                storage);
    }

    private void withRecord(QrRecord record) {
        given(repository.findByPassIdAndActiveTrue(PASS)).willReturn(Optional.of(record));
        given(storage.get("1/pdf/4/2.pdf")).willReturn(PDF);
    }

    @Test
    void theHolderMayDownloadTheirOwnPass() {
        withRecord(record(HOLDER));

        assertThat(service.download(PASS, true, user(HOLDER, "VISITOR"))).isEqualTo(PDF);
    }

    /**
     * THE BUG THIS FILE EXISTS FOR. Student id 4 pulling pass 4, held by user 8,
     * is the exact request that returned 200 and a working gate pass.
     */
    @Test
    void anotherHolderMayNotDownloadIt() {
        withRecord(record(HOLDER));

        assertThatThrownBy(() -> service.download(PASS, true, user(4L, "STUDENT")))
                .isInstanceOf(AccessDeniedException.class);

        verify(storage, never()).get("1/pdf/4/2.pdf");
    }

    @Test
    void staffMayDownloadAnyPassOnTheirCampus() {
        withRecord(record(HOLDER));

        assertThat(service.download(PASS, true, user(2L, "CAMPUS_ADMIN"))).isEqualTo(PDF);
        assertThat(service.download(PASS, true, user(3L, "FACULTY"))).isEqualTo(PDF);
    }

    /**
     * A guard scans passes; it never needs to download one, and a guard account
     * is the likeliest to be shared or left signed in at a gate.
     */
    @Test
    void aGuardIsNotStaffForThisPurpose() {
        withRecord(record(HOLDER));

        assertThatThrownBy(() -> service.download(PASS, true, user(5L, "GUARD")))
                .isInstanceOf(AccessDeniedException.class);
    }

    /**
     * Rows written before holder_user_id existed. Unknown owner must not read as
     * "anyone may have it" - the whole point of failing closed.
     */
    @Test
    void aLegacyRecordWithNoHolderIsStaffOnly() {
        withRecord(record(null));

        assertThatThrownBy(() -> service.download(PASS, true, user(HOLDER, "STUDENT")))
                .isInstanceOf(AccessDeniedException.class);

        assertThat(service.download(PASS, true, user(2L, "CAMPUS_ADMIN"))).isEqualTo(PDF);
    }

    @Test
    void noPrincipalIsRefused() {
        withRecord(record(HOLDER));

        assertThatThrownBy(() -> service.download(PASS, true, null))
                .isInstanceOf(AccessDeniedException.class);
    }
}
