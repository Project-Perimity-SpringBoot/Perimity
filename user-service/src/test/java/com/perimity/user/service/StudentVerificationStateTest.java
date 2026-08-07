package com.perimity.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.perimity.user.client.PassPauseClient;
import com.perimity.user.dto.request.StudentSelfDetailsDto;
import com.perimity.user.dto.request.StudentVerificationDecisionDto;
import com.perimity.user.entity.StudentProfile;
import com.perimity.user.entity.enums.Gender;
import com.perimity.user.entity.enums.ProfileVerificationStatus;
import com.perimity.user.repository.StudentProfileRepository;
import com.perimity.user.security.CurrentUser;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
 * The verification state machine.
 *
 * ==========================================================================
 * WHY THESE TRANSITIONS NEED GUARDING
 * ==========================================================================
 * verificationStatus is not a label. VERIFIED is a claim that a named member of
 * staff looked at a student's details and said they were right, and verifiedBy
 * records who. Every rule below exists to stop that claim becoming false.
 *
 * None of them fail loudly when broken. If the VERIFIED-to-DRAFT reset stopped
 * firing, nothing would error - a student would edit their date of birth and
 * keep a Verified badge over details nobody has ever seen, with a faculty
 * member's id attached to it. That is worse than no verification at all,
 * because it is a lie with a name on it.
 *
 * The photo case is the sharpest. It is the one field a guard compares against
 * the face in front of them, so a verified profile whose photo was swapped
 * afterwards is a pass vouching for the wrong person.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StudentVerificationStateTest {

    private static final Long STUDENT_ACCOUNT = 108L;
    private static final Long FACULTY_ACCOUNT = 6L;
    private static final Long PROFILE_ID = 55L;
    private static final Long CAMPUS = 2L;

    @Mock private StudentProfileRepository studentRepository;
    @Mock private ProfileGuard guard;
    @Mock private PassPauseClient passPauseClient;
    @Mock private StudentPassIssuer passIssuer;
    @Mock private CurrentUser currentUser;

    private StudentProfileService service;
    private StudentProfile profile;

    @BeforeEach
    void setUp() {
        service = new StudentProfileService(
                studentRepository, guard, passPauseClient, passIssuer, currentUser);

        profile = StudentProfile.builder()
                .id(PROFILE_ID)
                .userId(STUDENT_ACCOUNT)
                .campusId(CAMPUS)
                .firstName("Anjali")
                .lastName("Rao")
                .dateOfBirth(LocalDate.of(2004, 8, 19))
                .gender(Gender.FEMALE)
                .address("12 Example Road")
                .phoneCountryCode("+91")
                .phoneNumber("9876543210")
                .photoS3Key("campus-2/students/108/photo-abc.jpg")
                .verificationStatus(ProfileVerificationStatus.DRAFT)
                .build();

        when(studentRepository.findByUserId(STUDENT_ACCOUNT)).thenReturn(Optional.of(profile));
        when(studentRepository.findById(PROFILE_ID)).thenReturn(Optional.of(profile));
        when(studentRepository.save(any(StudentProfile.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(currentUser.canSeeCampus(CAMPUS)).thenReturn(true);
        when(currentUser.campusId()).thenReturn(CAMPUS);
        when(currentUser.userId()).thenReturn(FACULTY_ACCOUNT);
    }

    private StudentSelfDetailsDto details() {
        return StudentSelfDetailsDto.builder()
                .firstName("Anjali")
                .lastName("Rao")
                .dateOfBirth(LocalDate.of(2004, 8, 19))
                .gender(Gender.FEMALE)
                .address("12 Example Road")
                .phoneCountryCode("+91")
                .phoneNumber("9876543210")
                .build();
    }

    /* --------------------------------------------------------- the lock */

    @Test
    @DisplayName("a student cannot edit while faculty are reviewing")
    void refusesAnEditWhileSubmitted() {
        /*
         * If this stops firing, a student can change their details while a
         * reviewer has them open - and the reviewer approves something they
         * never saw. The approval would be recorded against their name.
         */
        profile.setVerificationStatus(ProfileVerificationStatus.SUBMITTED);

        assertThatThrownBy(() -> service.updateOwnDetails(STUDENT_ACCOUNT, details()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("faculty");
    }

    @Test
    @DisplayName("submitting twice is refused")
    void refusesASecondSubmit() {
        // submittedAt orders the review queue. A second submit would move the
        // student to the back of it for clicking twice.
        profile.setVerificationStatus(ProfileVerificationStatus.SUBMITTED);

        assertThatThrownBy(() -> service.submitOwnDetails(STUDENT_ACCOUNT))
                .isInstanceOf(IllegalStateException.class);
    }

    /* ------------------------------------- the one that matters the most */

    @Test
    @DisplayName("editing a VERIFIED profile clears the verification entirely")
    void editingAVerifiedProfileResetsItToDraft() {
        /*
         * THE CENTRAL RULE OF THIS FEATURE.
         *
         * A verified record must never describe details nobody checked. If this
         * regressed, the row would go on claiming faculty 6 verified a date of
         * birth that has since changed - a false attestation with a real
         * person's id on it, and nothing anywhere would error.
         */
        profile.setVerificationStatus(ProfileVerificationStatus.VERIFIED);
        profile.setVerifiedBy(FACULTY_ACCOUNT);
        profile.setVerifiedAt(LocalDateTime.now());
        profile.setSubmittedAt(LocalDateTime.now());

        service.updateOwnDetails(STUDENT_ACCOUNT, details());

        assertThat(profile.getVerificationStatus()).isEqualTo(ProfileVerificationStatus.DRAFT);
        assertThat(profile.getVerifiedBy()).isNull();
        assertThat(profile.getVerifiedAt()).isNull();
        assertThat(profile.getSubmittedAt()).isNull();
    }

    @Test
    @DisplayName("editing a REJECTED profile keeps the reviewer's remarks")
    void editingARejectedProfileKeepsTheRemarks() {
        // The student is editing BECAUSE they were told what was wrong.
        // Clearing the reason the moment they start typing would take away the
        // instructions mid-task. These clear on the next submit instead.
        profile.setVerificationStatus(ProfileVerificationStatus.REJECTED);
        profile.setVerificationRemarks("Your date of birth does not match your ID proof.");

        service.updateOwnDetails(STUDENT_ACCOUNT, details());

        assertThat(profile.getVerificationRemarks())
                .isEqualTo("Your date of birth does not match your ID proof.");
    }

    /* --------------------------------------------------------- submitting */

    @Test
    @DisplayName("submitting requires a passport photo")
    void refusesToSubmitWithoutAPhoto() {
        // A verified profile with no photo says somebody checked a person they
        // had no way to recognise.
        profile.setPhotoS3Key(null);

        assertThatThrownBy(() -> service.submitOwnDetails(STUDENT_ACCOUNT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("photo");
    }

    @Test
    @DisplayName("submitting names every missing field at once")
    void namesEveryMissingFieldOnSubmit() {
        profile.setFirstName(null);
        profile.setAddress(null);

        assertThatThrownBy(() -> service.submitOwnDetails(STUDENT_ACCOUNT))
                .hasMessageContaining("first name")
                .hasMessageContaining("address");
    }

    @Test
    @DisplayName("a valid submit stamps submittedAt and clears old remarks")
    void submitStampsAndClears() {
        profile.setVerificationStatus(ProfileVerificationStatus.REJECTED);
        profile.setVerificationRemarks("Fix your phone number.");

        service.submitOwnDetails(STUDENT_ACCOUNT);

        assertThat(profile.getVerificationStatus()).isEqualTo(ProfileVerificationStatus.SUBMITTED);
        assertThat(profile.getSubmittedAt()).isNotNull();
        // Stale remarks next to corrected details would just confuse.
        assertThat(profile.getVerificationRemarks()).isNull();
    }

    /* ---------------------------------------------------------- deciding */

    @Test
    @DisplayName("approving records the reviewer from the token, not the body")
    void approvalRecordsTheTokenHolder() {
        /*
         * StudentVerificationDecisionDto has no verifiedBy field precisely so
         * this cannot be forged. If the identity ever came from the request,
         * one member of staff could record an approval under another's name.
         */
        profile.setVerificationStatus(ProfileVerificationStatus.SUBMITTED);

        service.decideVerification(PROFILE_ID,
                StudentVerificationDecisionDto.builder().approved(true).build());

        assertThat(profile.getVerificationStatus()).isEqualTo(ProfileVerificationStatus.VERIFIED);
        assertThat(profile.getVerifiedBy()).isEqualTo(FACULTY_ACCOUNT);
        assertThat(profile.getVerifiedAt()).isNotNull();
    }

    @Test
    @DisplayName("approving resumes the holder's paused passes")
    void approvalResumesTheHoldersPasses() {
        /*
         * THE OTHER END OF THE PAUSE RULE, and it did not exist.
         *
         * A sensitive edit paused every pass the student held and nothing in
         * the product ever moved one back - not verification, not any screen.
         * The student's own pass page meanwhile promised "staff re-verify and
         * it resumes". Students ended up with permanently dead passes.
         *
         * If this stops firing, nothing errors. The approval is recorded, the
         * badge turns green, and the pass stays PAUSED with the student told to
         * wait for staff who have already done their part - which is the exact
         * failure this test exists to catch.
         */
        profile.setVerificationStatus(ProfileVerificationStatus.SUBMITTED);

        service.decideVerification(PROFILE_ID,
                StudentVerificationDecisionDto.builder().approved(true).build());

        verify(passPauseClient).resumeAllForHolder(
                eq(STUDENT_ACCOUNT), anyString(), eq(FACULTY_ACCOUNT));
    }

    @Test
    @DisplayName("rejecting does NOT resume the passes")
    void rejectionLeavesThePassesHeld() {
        // A rejection means the details are still wrong. Resuming here would put
        // a working pass in the pocket of somebody a reviewer just refused.
        profile.setVerificationStatus(ProfileVerificationStatus.SUBMITTED);

        service.decideVerification(PROFILE_ID, StudentVerificationDecisionDto.builder()
                .approved(false).remarks("Photo is unusable.").build());

        verify(passPauseClient, never()).resumeAllForHolder(anyLong(), anyString(), anyLong());
    }

    @Test
    @DisplayName("editing an unverified profile does not pause the pass")
    void editingADraftDoesNotPause() {
        /*
         * updateOwnDetails used to call pauseHolder UNCONDITIONALLY, so fixing a
         * typo in an address held the pass - and, before resume existed, held it
         * forever. Nothing on this form reaches the gate: the pass carries
         * auth-service's account name, and the photo and roll number are edited
         * elsewhere.
         *
         * A DRAFT profile has no verification to invalidate, so there is nothing
         * to hold the pass for.
         */
        profile.setVerificationStatus(ProfileVerificationStatus.DRAFT);

        service.updateOwnDetails(STUDENT_ACCOUNT, details());

        verify(passPauseClient, never()).pauseAllForHolder(anyLong(), anyString(), anyLong());
    }

    @Test
    @DisplayName("editing a VERIFIED profile still pauses the pass")
    void editingAVerifiedProfileStillPauses() {
        // The other half of the rule above. A checked record has just been
        // changed, so the sign-off the pass rests on is void until somebody
        // looks again.
        profile.setVerificationStatus(ProfileVerificationStatus.VERIFIED);

        service.updateOwnDetails(STUDENT_ACCOUNT, details());

        verify(passPauseClient).pauseAllForHolder(eq(STUDENT_ACCOUNT), anyString(), anyLong());
    }

    @Test
    @DisplayName("deciding twice is refused, so two reviewers cannot race")
    void refusesASecondDecision() {
        // Without this the second reviewer silently overwrites the first, and
        // the record names whoever happened to click last.
        profile.setVerificationStatus(ProfileVerificationStatus.VERIFIED);

        assertThatThrownBy(() -> service.decideVerification(PROFILE_ID,
                StudentVerificationDecisionDto.builder().approved(true).build()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("a DRAFT profile cannot be approved")
    void refusesToDecideOnADraft() {
        // Stops a reviewer approving details a student is still editing.
        profile.setVerificationStatus(ProfileVerificationStatus.DRAFT);

        assertThatThrownBy(() -> service.decideVerification(PROFILE_ID,
                StudentVerificationDecisionDto.builder().approved(true).build()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("rejecting stores the remarks the student reads")
    void rejectionStoresRemarks() {
        profile.setVerificationStatus(ProfileVerificationStatus.SUBMITTED);

        service.decideVerification(PROFILE_ID, StudentVerificationDecisionDto.builder()
                .approved(false).remarks("Your roll number is wrong.").build());

        assertThat(profile.getVerificationStatus()).isEqualTo(ProfileVerificationStatus.REJECTED);
        assertThat(profile.getVerificationRemarks()).isEqualTo("Your roll number is wrong.");
        assertThat(profile.getVerifiedBy()).isEqualTo(FACULTY_ACCOUNT);
    }

    @Test
    @DisplayName("only staff may decide")
    void refusesADecisionFromANonReviewer() {
        profile.setVerificationStatus(ProfileVerificationStatus.SUBMITTED);
        org.mockito.Mockito.doThrow(new RuntimeException("not a reviewer"))
                .when(currentUser).requireProfileReviewer();

        assertThatThrownBy(() -> service.decideVerification(PROFILE_ID,
                StudentVerificationDecisionDto.builder().approved(true).build()))
                .hasMessageContaining("not a reviewer");

        verify(studentRepository, never()).save(any(StudentProfile.class));
    }

    /* -------------------------------------------- the self-healing path */

    @Test
    @DisplayName("a student with no profile row gets one created on first save")
    void createsTheProfileOnFirstSaveWhenMissing() {
        /*
         * An account can exist without a profile - every student created before
         * user.created provisioning is in that position. Without this they
         * could sign in and find every profile screen telling them they do not
         * exist, with nothing in the product able to fix it.
         *
         * PUT is create-or-replace, so creating here is what the verb means.
         */
        when(studentRepository.findByUserId(STUDENT_ACCOUNT)).thenReturn(Optional.empty());

        service.updateOwnDetails(STUDENT_ACCOUNT, details());

        verify(studentRepository, org.mockito.Mockito.atLeastOnce())
                .save(any(StudentProfile.class));
    }
}
