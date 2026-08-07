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
import com.perimity.user.dto.request.StudentProfileUpdateDto;
import com.perimity.user.dto.response.StudentProfileResponse;
import com.perimity.user.entity.StudentProfile;
import com.perimity.user.repository.StudentProfileRepository;
import com.perimity.user.security.CurrentUser;
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

/**
 * The two rules on this class that are easy to break and impossible to see
 * broken:
 *
 *   1. THE PAUSE RULE. Changing a roll number, government id or photo must hold
 *      every active pass the person has. If this stops firing nothing errors -
 *      it just means an edited photo leaves a green pass in someone's pocket,
 *      which is the exact hole the rule exists to close.
 *
 *   2. NULL MEANS UNCHANGED. Every field on the update DTO is optional. If null
 *      were ever treated as "set to null", a form that posts only an address
 *      would wipe the roll number and photo - and pause the pass while doing
 *      it. Nothing would throw.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StudentProfileServiceTest {

    private static final Long PROFILE_ID = 55L;
    private static final Long HOLDER_ACCOUNT = 108L;
    private static final Long EDITOR_ACCOUNT = 7L;
    private static final Long CAMPUS = 1L;

    @Mock private StudentProfileRepository studentRepository;
    @Mock private ProfileGuard guard;
    @Mock private PassPauseClient passPauseClient;
    @Mock private StudentPassIssuer passIssuer;
    @Mock private CurrentUser currentUser;

    private StudentProfileService service;
    private StudentProfile existing;

    @BeforeEach
    void setUp() {
        service = new StudentProfileService(
                studentRepository, guard, passPauseClient, passIssuer, currentUser);

        existing = StudentProfile.builder()
                .id(PROFILE_ID)
                .userId(HOLDER_ACCOUNT)
                .campusId(CAMPUS)
                .departmentId(3L)
                .rollNo("2026/CS/0141")
                .govId("123456789012")
                .address("12 Old Street")
                .photoS3Key("profiles/campus-1/students/108/photo-abc.jpg")
                .build();

        when(studentRepository.findById(PROFILE_ID)).thenReturn(Optional.of(existing));
        when(studentRepository.save(any(StudentProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(currentUser.canSeeCampus(CAMPUS)).thenReturn(true);
        when(currentUser.userId()).thenReturn(EDITOR_ACCOUNT);
        when(passPauseClient.pauseAllForHolder(anyLong(), anyString(), anyLong())).thenReturn(true);
    }

    @Test
    @DisplayName("changing the roll number pauses every pass the holder has")
    void sensitiveChangePausesTheHolder() {
        service.update(PROFILE_ID, StudentProfileUpdateDto.builder()
                .rollNo("2026/CS/0999")
                .build());

        ArgumentCaptor<String> reason = ArgumentCaptor.forClass(String.class);
        verify(passPauseClient).pauseAllForHolder(eq(HOLDER_ACCOUNT), reason.capture(), eq(EDITOR_ACCOUNT));

        // The reason reaches a human who has to decide whether to re-approve,
        // so it has to name what actually changed.
        assertThat(reason.getValue()).contains("roll number");
    }

    @Test
    @DisplayName("several sensitive fields at once produce one pause naming all of them")
    void reportsEverySensitiveFieldThatChanged() {
        service.update(PROFILE_ID, StudentProfileUpdateDto.builder()
                .rollNo("2026/CS/0999")
                .govId("999999999999")
                .photoS3Key("profiles/campus-1/students/108/photo-new.jpg")
                .build());

        ArgumentCaptor<String> reason = ArgumentCaptor.forClass(String.class);
        verify(passPauseClient).pauseAllForHolder(eq(HOLDER_ACCOUNT), reason.capture(), eq(EDITOR_ACCOUNT));

        assertThat(reason.getValue())
                .contains("roll number")
                .contains("government ID")
                .contains("photo");
    }

    @Test
    @DisplayName("changing only the address does NOT pause the pass")
    void ordinaryChangeLeavesThePassAlone() {
        service.update(PROFILE_ID, StudentProfileUpdateDto.builder()
                .address("48 New Road")
                .build());

        verify(passPauseClient, never()).pauseAllForHolder(anyLong(), anyString(), anyLong());
    }

    @Test
    @DisplayName("resending the same values is not a change and does not pause")
    void unchangedValueIsNotAChange() {
        // A form that reloads the profile and posts it back sends every field,
        // including the ones nobody touched. Comparing values rather than
        // presence is what stops a plain save from suspending someone's access.
        service.update(PROFILE_ID, StudentProfileUpdateDto.builder()
                .photoS3Key("profiles/campus-1/students/108/photo-abc.jpg")
                .rollNo("2026/CS/0141")
                .build());

        verify(passPauseClient, never()).pauseAllForHolder(anyLong(), anyString(), anyLong());
    }

    @Test
    @DisplayName("a field left out of the request keeps its existing value")
    void omittedFieldsSurvive() {
        StudentProfileResponse updated = service.update(PROFILE_ID,
                StudentProfileUpdateDto.builder().address("48 New Road").build());

        assertThat(updated.rollNo()).isEqualTo("2026/CS/0141");
        assertThat(updated.photoS3Key()).isEqualTo("profiles/campus-1/students/108/photo-abc.jpg");
        assertThat(updated.govIdPresent()).isTrue();
        assertThat(updated.address()).isEqualTo("48 New Road");
    }

    @Test
    @DisplayName("an empty string is the explicit way to clear a value, and it pauses")
    void emptyStringClearsAndCountsAsAChange() {
        StudentProfileResponse updated = service.update(PROFILE_ID,
                StudentProfileUpdateDto.builder().govId("").build());

        assertThat(updated.govIdPresent()).isFalse();
        verify(passPauseClient).pauseAllForHolder(eq(HOLDER_ACCOUNT), anyString(), eq(EDITOR_ACCOUNT));
    }

    @Test
    @DisplayName("a roll number already used on this campus is refused")
    void refusesADuplicateRollNumber() {
        StudentProfile someoneElse = StudentProfile.builder()
                .id(77L).userId(200L).campusId(CAMPUS).rollNo("2026/CS/0999").build();

        when(studentRepository.findByCampusIdAndRollNoIgnoreCase(CAMPUS, "2026/CS/0999"))
                .thenReturn(Optional.of(someoneElse));

        assertThatThrownBy(() -> service.update(PROFILE_ID,
                StudentProfileUpdateDto.builder().rollNo("2026/CS/0999").build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already used on this campus");
    }

    @Test
    @DisplayName("a profile on another campus reads as not found, never as forbidden")
    void anotherCampusIsInvisible() {
        // 404 rather than 403 on purpose. A 403 confirms the row exists, which
        // is enough to count another campus's students by walking the ids.
        when(currentUser.canSeeCampus(CAMPUS)).thenReturn(false);

        assertThatThrownBy(() -> service.getOne(PROFILE_ID))
                .isInstanceOf(com.perimity.user.exception.ResourceNotFoundException.class);
    }
}
