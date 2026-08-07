package com.perimity.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.perimity.user.dto.response.ProfileSummaryResponse;
import com.perimity.user.entity.FacultyProfile;
import com.perimity.user.entity.StudentProfile;
import com.perimity.user.entity.enums.ProfileType;
import com.perimity.user.exception.ResourceNotFoundException;
import com.perimity.user.repository.FacultyProfileRepository;
import com.perimity.user.repository.StudentProfileRepository;
import com.perimity.user.storage.StorageException;
import com.perimity.user.storage.StorageService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * The cross-service contract, and the two things Days 10 and 11 added to it.
 *
 * gatepass-service's InternalServiceClient reads three field names out of the
 * envelope: userId, identifierCode and photoS3Key. Renaming or dropping any of
 * them makes that call return nulls, and every printed pass quietly loses its
 * photo with nothing in a log. These tests pin the three names down.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProfileLookupServiceTest {

    private static final String SIGNED = "https://example.invalid/signed";

    @Mock private StudentProfileRepository studentRepository;
    @Mock private FacultyProfileRepository facultyRepository;
    @Mock private StorageService storage;
    @Mock private ProfileGuard guard;

    private ProfileLookupService service;

    @BeforeEach
    void setUp() {
        service = new ProfileLookupService(studentRepository, facultyRepository, storage, guard, 15);
        when(storage.presignedReadUrl(anyString(), any())).thenReturn(SIGNED);
    }

    // ------------------------------------------------------- the contract

    @Test
    @DisplayName("a student's roll number is the identifierCode")
    void studentIdentifierIsTheRollNumber() {
        when(studentRepository.findByUserId(108L)).thenReturn(Optional.of(student(108L, "2026/CS/0141")));

        ProfileSummaryResponse summary = service.summaryOf(108L);

        assertThat(summary.userId()).isEqualTo(108L);
        assertThat(summary.identifierCode()).isEqualTo("2026/CS/0141");
        assertThat(summary.photoS3Key()).isEqualTo("profiles/campus-1/students/108/photo-abc.jpg");
        assertThat(summary.profileType()).isEqualTo(ProfileType.STUDENT);
    }

    @Test
    @DisplayName("a faculty member's employee id is the identifierCode - one field, either way")
    void facultyIdentifierIsTheEmployeeId() {
        // One field for both kinds, so a caller that does not care which sort of
        // person it is does not need two code paths.
        when(studentRepository.findByUserId(42L)).thenReturn(Optional.empty());
        when(facultyRepository.findByUserId(42L)).thenReturn(Optional.of(
                FacultyProfile.builder().id(9L).userId(42L).campusId(1L)
                        .employeeId("EMP-2041").build()));

        ProfileSummaryResponse summary = service.summaryOf(42L);

        assertThat(summary.identifierCode()).isEqualTo("EMP-2041");
        assertThat(summary.profileType()).isEqualTo(ProfileType.FACULTY);
    }

    @Test
    @DisplayName("the summary carries nothing sensitive")
    void carriesNothingSensitive() {
        StudentProfile p = student(108L, "2026/CS/0141");
        p.setGovId("123456789012");
        p.setAddress("12 Old Street");
        when(studentRepository.findByUserId(108L)).thenReturn(Optional.of(p));

        String rendered = service.summaryOf(108L).toString();

        // This crosses a service boundary on a shared key. The narrower it
        // stays, the less there is to leak and the less there is to break.
        assertThat(rendered).doesNotContain("123456789012");
        assertThat(rendered).doesNotContain("Old Street");
    }

    @Test
    @DisplayName("an account with no profile is a 404, which the caller degrades gracefully")
    void noProfileIsNotFound() {
        when(studentRepository.findByUserId(999L)).thenReturn(Optional.empty());
        when(facultyRepository.findByUserId(999L)).thenReturn(Optional.empty());

        // gatepass-service treats any failure here as "carry on without the
        // photo", so this degrades to a pass without a picture rather than a
        // pass that cannot be issued at all.
        assertThatThrownBy(() -> service.summaryOf(999L))
                .isInstanceOf(ResourceNotFoundException.class);

        assertThat(service.findSummary(999L)).isEmpty();
    }

    // ------------------------------------------------- Day 11: the photo

    @Test
    @DisplayName("a single summary carries a signed photo link, not just the key")
    void singleSummaryIsDisplayable() {
        when(studentRepository.findByUserId(108L)).thenReturn(Optional.of(student(108L, "R1")));

        // A key is not displayable. Without this the scanner has no path from a
        // scan to a face at all, because guard-service holds an API key and
        // cannot reach the JWT-guarded photo-url endpoint.
        assertThat(service.summaryOf(108L).photoUrl()).isEqualTo(SIGNED);
    }

    @Test
    @DisplayName("no photo means a null link, not a broken one")
    void noPhotoMeansNoLink() {
        StudentProfile p = student(108L, "R1");
        p.setPhotoS3Key(null);
        when(studentRepository.findByUserId(108L)).thenReturn(Optional.of(p));

        assertThat(service.summaryOf(108L).photoUrl()).isNull();
        verify(storage, never()).presignedReadUrl(anyString(), any());
    }

    @Test
    @DisplayName("storage being unreachable degrades the face, it does not fail the scan")
    void storageFailureDoesNotBreakTheScan() {
        when(storage.presignedReadUrl(anyString(), any()))
                .thenThrow(new StorageException("S3 unreachable", null));
        when(studentRepository.findByUserId(108L)).thenReturn(Optional.of(student(108L, "R1")));

        ProfileSummaryResponse summary = service.summaryOf(108L);

        // A scanner showing a name and no face is degraded. A scanner returning
        // 500 because S3 was slow is broken - at a gate, with a queue behind it.
        assertThat(summary.photoUrl()).isNull();
        assertThat(summary.identifierCode()).isEqualTo("R1");
    }

    private StudentProfile student(Long userId, String rollNo) {
        return StudentProfile.builder()
                .id(55L).userId(userId).campusId(1L).rollNo(rollNo)
                .photoS3Key("profiles/campus-1/students/108/photo-abc.jpg")
                .build();
    }
}
