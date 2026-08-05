package com.perimity.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.perimity.user.entity.FacultyProfile;
import com.perimity.user.entity.StudentProfile;
import com.perimity.user.entity.enums.ProfileVerificationStatus;
import com.perimity.user.repository.FacultyProfileRepository;
import com.perimity.user.repository.StudentProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Profile provisioning from a user.created event.
 *
 * ==========================================================================
 * IDEMPOTENCE IS THE WHOLE POINT
 * ==========================================================================
 * A broker redelivers whenever a consumer dies between doing the work and
 * acknowledging it. That is normal operation, not an error - so provisioning
 * the same account twice must be harmless.
 *
 * If it stopped being harmless, the symptom would not be an exception. It would
 * be duplicate profiles appearing under load, or a message dead-lettering
 * forever because it fails identically on every redelivery - and both only show
 * up when a broker hiccups in front of people.
 *
 * ==========================================================================
 * THE FAILURE THAT MUST STILL THROW
 * ==========================================================================
 * A STUDENT or FACULTY with no campusId cannot have a profile - campus_id is
 * NOT NULL on both tables. That has to throw so the message retries and then
 * lands in the DLQ where somebody can see it. Swallowing it would recreate the
 * silent orphan this whole mechanism exists to abolish.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProfileProvisioningServiceTest {

    private static final Long ACCOUNT = 24L;
    private static final Long CAMPUS = 2L;

    @Mock private StudentProfileRepository studentRepository;
    @Mock private FacultyProfileRepository facultyRepository;

    private ProfileProvisioningService service;

    @BeforeEach
    void setUp() {
        service = new ProfileProvisioningService(studentRepository, facultyRepository);

        when(studentRepository.existsByUserId(ACCOUNT)).thenReturn(false);
        when(facultyRepository.existsByUserId(ACCOUNT)).thenReturn(false);
        when(studentRepository.save(any(StudentProfile.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(facultyRepository.save(any(FacultyProfile.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    /* ---------------------------------------------------------- creating */

    @Test
    @DisplayName("a STUDENT gets an empty profile in DRAFT")
    void provisionsAStudent() {
        assertThat(service.provision(ACCOUNT, "STUDENT", CAMPUS)).isTrue();

        ArgumentCaptor<StudentProfile> saved = ArgumentCaptor.forClass(StudentProfile.class);
        verify(studentRepository).save(saved.capture());

        assertThat(saved.getValue().getUserId()).isEqualTo(ACCOUNT);
        assertThat(saved.getValue().getCampusId()).isEqualTo(CAMPUS);
        // DRAFT, because nobody has checked anything yet. Anything else would
        // be a verification claim nobody made.
        assertThat(saved.getValue().getVerificationStatus())
                .isEqualTo(ProfileVerificationStatus.DRAFT);
        // Staff decisions, not ours to invent.
        assertThat(saved.getValue().getRollNo()).isNull();
        assertThat(saved.getValue().getDepartmentId()).isNull();
    }

    @Test
    @DisplayName("a FACULTY gets an empty profile - this is what fills the host dropdown")
    void provisionsFaculty() {
        assertThat(service.provision(ACCOUNT, "FACULTY", CAMPUS)).isTrue();
        verify(facultyRepository).save(any(FacultyProfile.class));
    }

    /* ------------------------------------------------------- idempotence */

    @Test
    @DisplayName("a redelivered message does not create a second profile")
    void doesNothingWhenTheProfileAlreadyExists() {
        when(studentRepository.existsByUserId(ACCOUNT)).thenReturn(true);

        assertThat(service.provision(ACCOUNT, "STUDENT", CAMPUS)).isFalse();
        verify(studentRepository, never()).save(any(StudentProfile.class));
    }

    @Test
    @DisplayName("losing the race to a concurrent delivery counts as success")
    void treatsAUniqueViolationAsSuccess() {
        /*
         * Two deliveries processed at once: the existence check passes for
         * both, then the unique index on user_id rejects the second insert.
         *
         * That outcome is exactly what was wanted - a profile exists - so it
         * must NOT propagate. If it did, the message would retry, fail
         * identically, and dead-letter forever while the profile sat there
         * perfectly fine.
         */
        when(studentRepository.save(any(StudentProfile.class)))
                .thenThrow(new DataIntegrityViolationException("uk_student_user"));

        assertThat(service.provision(ACCOUNT, "STUDENT", CAMPUS)).isFalse();
    }

    /* ------------------------------------------------ roles without one */

    @Test
    @DisplayName("roles with no profile entity are ignored, not failed")
    void ignoresRolesThatHaveNoProfile() {
        /*
         * GUARD, CAMPUS_ADMIN, SUPER_ADMIN and VISITOR have no profile table.
         * auth-service filters these before publishing, so reaching here means
         * a role was added there that this service has not been taught about.
         *
         * A log line beats an exception: dead-lettering a message that was
         * never actionable just fills the DLQ with noise.
         */
        for (String role : new String[]{"GUARD", "CAMPUS_ADMIN", "SUPER_ADMIN", "VISITOR", "NEW"}) {
            assertThat(service.provision(ACCOUNT, role, CAMPUS)).isFalse();
        }
        verify(studentRepository, never()).save(any(StudentProfile.class));
        verify(facultyRepository, never()).save(any(FacultyProfile.class));
    }

    /* ------------------------------------------ the one that must throw */

    @Test
    @DisplayName("a student with no campus THROWS, so the message dead-letters")
    void throwsWhenCampusIsMissing() {
        /*
         * campus_id is NOT NULL on both tables. Only SUPER_ADMIN may have a
         * null campus, and SUPER_ADMIN has no profile - so this is an
         * auth-service bug.
         *
         * It must throw. Logging and returning would leave an account with no
         * profile and no record that anything went wrong, which is precisely
         * the silent orphan this mechanism exists to prevent.
         */
        assertThatThrownBy(() -> service.provision(ACCOUNT, "STUDENT", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("campusId");

        verify(studentRepository, never()).save(any(StudentProfile.class));
    }

    @Test
    @DisplayName("a malformed message is dropped rather than retried")
    void ignoresAMessageWithNoAccount() {
        // Retrying will not conjure a userId. Dead-lettering it would fill the
        // queue with something no operator can act on.
        assertThat(service.provision(null, "STUDENT", CAMPUS)).isFalse();
        assertThat(service.provision(ACCOUNT, null, CAMPUS)).isFalse();
    }
}
