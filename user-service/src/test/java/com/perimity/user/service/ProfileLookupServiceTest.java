package com.perimity.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.perimity.user.dto.response.ProfileSummaryResponse;
import com.perimity.user.entity.FacultyProfile;
import com.perimity.user.entity.StudentProfile;
import com.perimity.user.entity.enums.ProfileType;
import com.perimity.user.exception.ResourceNotFoundException;
import com.perimity.user.repository.FacultyProfileRepository;
import com.perimity.user.repository.StudentProfileRepository;
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
 * The Day 8 contract gatepass-service already calls.
 *
 * Its InternalServiceClient reads three fields out of the envelope: userId,
 * identifierCode and photoS3Key. Renaming or dropping any of them makes that
 * call return nulls, and every printed pass quietly loses its photo with
 * nothing in a log to explain it. These tests pin the three names down.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProfileLookupServiceTest {

    @Mock private StudentProfileRepository studentRepository;
    @Mock private FacultyProfileRepository facultyRepository;

    private ProfileLookupService service;

    @BeforeEach
    void setUp() {
        service = new ProfileLookupService(studentRepository, facultyRepository);
    }

    @Test
    @DisplayName("a student's roll number is the identifierCode")
    void studentIdentifierIsTheRollNumber() {
        when(studentRepository.findByUserId(108L)).thenReturn(Optional.of(
                StudentProfile.builder().id(55L).userId(108L).campusId(1L)
                        .rollNo("2026/CS/0141")
                        .photoS3Key("profiles/campus-1/students/108/photo-abc.jpg")
                        .build()));

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
        when(studentRepository.findByUserId(108L)).thenReturn(Optional.of(
                StudentProfile.builder().id(55L).userId(108L).campusId(1L)
                        .rollNo("2026/CS/0141")
                        .govId("123456789012")
                        .address("12 Old Street")
                        .build()));

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
}
