package com.perimity.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.perimity.user.dto.request.DocumentVerificationDto;
import com.perimity.user.dto.response.DocumentResponse;
import com.perimity.user.entity.Document;
import com.perimity.user.entity.StudentProfile;
import com.perimity.user.entity.enums.DocumentType;
import com.perimity.user.exception.ForbiddenException;
import com.perimity.user.repository.DocumentRepository;
import com.perimity.user.repository.FacultyProfileRepository;
import com.perimity.user.repository.StudentProfileRepository;
import com.perimity.user.security.CurrentUser;
import com.perimity.user.storage.StorageService;
import com.perimity.user.storage.StoredObject;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

/**
 * The rules that keep the verification step from being decoration, plus the
 * Day 9 rule that a client can no longer choose where its file is written.
 *
 * UploadValidator is a REAL instance here, not a mock. Its whole job is to look
 * at bytes, and a mock that always says yes would let this test pass while the
 * actual check was broken.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DocumentServiceTest {

    private static final Long DOCUMENT_ID = 12L;
    private static final Long HOLDER_ACCOUNT = 108L;
    private static final Long ADMIN_ACCOUNT = 7L;
    private static final Long CAMPUS = 1L;

    private static final byte[] PDF = {'%', 'P', 'D', 'F', '-', '1', '.', '4'};

    @Mock private DocumentRepository documentRepository;
    @Mock private StudentProfileRepository studentRepository;
    @Mock private FacultyProfileRepository facultyRepository;
    @Mock private StorageService storage;
    @Mock private CurrentUser currentUser;

    private DocumentService service;
    private Document existing;

    @BeforeEach
    void setUp() {
        service = new DocumentService(documentRepository, studentRepository, facultyRepository,
                storage, new UploadValidator(), currentUser, 5L, 15);

        existing = Document.builder()
                .id(DOCUMENT_ID)
                .userId(HOLDER_ACCOUNT)
                .docType(DocumentType.ID_PROOF)
                .s3Key("profiles/campus-1/users/108/documents/abc-id-proof.pdf")
                .fileName("id-proof.pdf")
                .mimeType("application/pdf")
                .verified(false)
                .build();

        when(documentRepository.findById(DOCUMENT_ID)).thenReturn(Optional.of(existing));
        when(documentRepository.save(any(Document.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(studentRepository.findByUserId(HOLDER_ACCOUNT)).thenReturn(Optional.of(
                StudentProfile.builder().id(55L).userId(HOLDER_ACCOUNT).campusId(CAMPUS).build()));
        when(currentUser.canSeeCampus(CAMPUS)).thenReturn(true);
        when(currentUser.userId()).thenReturn(ADMIN_ACCOUNT);
        when(storage.put(anyString(), any(), anyLong(), anyString()))
                .thenAnswer(i -> new StoredObject(i.getArgument(0), i.getArgument(3), 8L));
        when(storage.exists(anyString())).thenReturn(true);
        when(storage.presignedReadUrl(anyString(), any())).thenReturn("https://example.invalid/signed");
    }

    // --------------------------------------------------------- Day 9

    @Test
    @DisplayName("the storage key is generated here, not taken from the caller")
    void generatesTheStorageKey() {
        service.upload(HOLDER_ACCOUNT, DocumentType.ID_PROOF, pdf("id-proof.pdf"));

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(storage).put(key.capture(), any(), anyLong(), eq("application/pdf"));

        // Built from the profile row that was just loaded, so it can only ever
        // land under this person's own prefix.
        assertThat(key.getValue()).startsWith("profiles/campus-1/users/108/documents/");
    }

    @Test
    @DisplayName("a hostile filename cannot redirect the upload into someone else's folder")
    void filenameCannotChooseTheDestination() {
        service.upload(HOLDER_ACCOUNT, DocumentType.ID_PROOF, pdf("../../200/documents/steal.pdf"));

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(storage).put(key.capture(), any(), anyLong(), anyString());

        assertThat(key.getValue()).startsWith("profiles/campus-1/users/108/documents/");
        assertThat(key.getValue()).doesNotContain("..");
    }

    @Test
    @DisplayName("a file that is not really a PDF is refused before anything is stored")
    void refusesAFileThatLiesAboutItsType() {
        MultipartFile fake = new MockMultipartFile(
                "file", "id-proof.pdf", "application/pdf", "<html>not a pdf".getBytes());

        assertThatThrownBy(() -> service.upload(HOLDER_ACCOUNT, DocumentType.ID_PROOF, fake))
                .isInstanceOf(IllegalArgumentException.class);

        // Nothing reached storage. Validation runs first for exactly this reason.
        verify(storage, org.mockito.Mockito.never()).put(anyString(), any(), anyLong(), anyString());
    }

    @Test
    @DisplayName("a newly uploaded document is always unverified")
    void newDocumentsStartUnverified() {
        DocumentResponse created = service.upload(HOLDER_ACCOUNT, DocumentType.ID_PROOF, pdf("id.pdf"));

        assertThat(created.verified()).isFalse();
        assertThat(created.verifiedBy()).isNull();
    }

    @Test
    @DisplayName("the download link is short lived and never stored")
    void mintsAShortLivedLink() {
        var url = service.downloadUrl(DOCUMENT_ID);

        assertThat(url.url()).isEqualTo("https://example.invalid/signed");
        assertThat(url.validForMinutes()).isEqualTo(15);
        assertThat(url.expiresAt()).isAfter(java.time.LocalDateTime.now());
    }

    @Test
    @DisplayName("a row whose file has vanished says so, rather than handing back a dead link")
    void reportsAMissingObject() {
        when(storage.exists(anyString())).thenReturn(false);

        assertThatThrownBy(() -> service.downloadUrl(DOCUMENT_ID))
                .isInstanceOf(com.perimity.user.exception.ResourceNotFoundException.class)
                .hasMessageContaining("missing from storage");
    }

    @Test
    @DisplayName("deleting an unverified document removes the file too")
    void deleteRemovesTheObject() {
        service.delete(DOCUMENT_ID);

        verify(documentRepository).delete(existing);
        verify(storage).delete("profiles/campus-1/users/108/documents/abc-id-proof.pdf");
    }

    // --------------------------------------------------------- Day 6

    @Test
    @DisplayName("verifiedBy is taken from the token and the value in the body is ignored")
    void ignoresTheVerifiedByInTheBody() {
        DocumentResponse decided = service.decide(DOCUMENT_ID, DocumentVerificationDto.builder()
                .verified(true)
                .verifiedBy(999L)   // a client claiming somebody else approved this
                .build());

        assertThat(decided.verifiedBy()).isEqualTo(ADMIN_ACCOUNT);
        assertThat(decided.verified()).isTrue();
        assertThat(decided.verifiedAt()).isNotNull();
    }

    @Test
    @DisplayName("nobody verifies their own document")
    void refusesSelfVerification() {
        // Faculty are administrators on some campuses. Without this check an
        // admin could approve their own ID proof and the step means nothing.
        when(currentUser.userId()).thenReturn(HOLDER_ACCOUNT);

        assertThatThrownBy(() -> service.decide(DOCUMENT_ID, DocumentVerificationDto.builder()
                .verified(true).verifiedBy(HOLDER_ACCOUNT).build()))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("your own document");
    }

    @Test
    @DisplayName("a rejection stores its remarks so the person knows what to fix")
    void storesRejectionRemarks() {
        DocumentResponse decided = service.decide(DOCUMENT_ID, DocumentVerificationDto.builder()
                .verified(false)
                .verifiedBy(ADMIN_ACCOUNT)
                .remarks("The scan is cut off on the right edge. Re-upload the full page.")
                .build());

        assertThat(decided.verified()).isFalse();
        assertThat(decided.verificationRemarks()).contains("cut off");
    }

    @Test
    @DisplayName("approving clears an earlier rejection note")
    void approvalClearsOldRemarks() {
        existing.setVerificationRemarks("The scan is cut off on the right edge.");

        DocumentResponse decided = service.decide(DOCUMENT_ID, DocumentVerificationDto.builder()
                .verified(true).verifiedBy(ADMIN_ACCOUNT).build());

        // Otherwise a verified document goes on displaying why it was once refused.
        assertThat(decided.verificationRemarks()).isNull();
    }

    @Test
    @DisplayName("a verified document cannot be deleted")
    void refusesToDeleteAVerifiedDocument() {
        existing.setVerified(true);

        assertThatThrownBy(() -> service.delete(DOCUMENT_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Upload a replacement");
    }

    @Test
    @DisplayName("an account with no profile in this service cannot have documents attached")
    void refusesAnAccountWithNoProfile() {
        when(studentRepository.findByUserId(HOLDER_ACCOUNT)).thenReturn(Optional.empty());
        when(facultyRepository.findByUserId(HOLDER_ACCOUNT)).thenReturn(Optional.empty());

        // Document has no campus_id of its own. The profile is the only thing
        // that says which campus a file belongs to - and since Day 9, the only
        // thing that says where in storage it may be written.
        assertThatThrownBy(() -> service.listForUser(HOLDER_ACCOUNT))
                .isInstanceOf(com.perimity.user.exception.ResourceNotFoundException.class)
                .hasMessageContaining("no profile");
    }

    private MultipartFile pdf(String name) {
        return new MockMultipartFile("file", name, "application/pdf", PDF);
    }
}
