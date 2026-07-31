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
import com.perimity.user.entity.StudentProfile;
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
 * Photo upload, and the ordering that matters.
 *
 * The interesting property is not that the file gets stored - it is what
 * happens around that:
 *
 *   - the OLD object is deleted only AFTER the new one is saved, so a failed
 *     upload never leaves someone with no photo at all
 *   - the pass is paused, because the photo is what a guard checks a face
 *     against and the QR in the holder's pocket now vouches for a different one
 *   - a rejected file never reaches storage
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProfileAssetServiceTest {

    private static final Long PROFILE_ID = 55L;
    private static final Long HOLDER_ACCOUNT = 108L;
    private static final Long EDITOR_ACCOUNT = 7L;
    private static final Long CAMPUS = 1L;
    private static final String OLD_KEY = "profiles/campus-1/students/108/photo-old.jpg";

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    @Mock private StudentProfileRepository studentRepository;
    @Mock private FacultyProfileRepository facultyRepository;
    @Mock private StorageService storage;
    @Mock private ProfileGuard guard;
    @Mock private PassPauseClient passPauseClient;
    @Mock private CurrentUser currentUser;

    private ProfileAssetService service;
    private StudentProfile profile;

    @BeforeEach
    void setUp() {
        service = new ProfileAssetService(studentRepository, facultyRepository, storage,
                new UploadValidator(), guard, passPauseClient, currentUser, 2L, 15);

        profile = StudentProfile.builder()
                .id(PROFILE_ID)
                .userId(HOLDER_ACCOUNT)
                .campusId(CAMPUS)
                .photoS3Key(OLD_KEY)
                .build();

        when(studentRepository.findById(PROFILE_ID)).thenReturn(Optional.of(profile));
        when(studentRepository.save(any(StudentProfile.class)))
                .thenAnswer(i -> i.getArgument(0));
        when(currentUser.canSeeCampus(CAMPUS)).thenReturn(true);
        when(currentUser.userId()).thenReturn(EDITOR_ACCOUNT);
        when(storage.put(anyString(), any(), anyLong(), anyString()))
                .thenAnswer(i -> new StoredObject(i.getArgument(0), i.getArgument(3), 8L));
        when(passPauseClient.pauseAllForHolder(anyLong(), anyString(), anyLong())).thenReturn(true);
        when(storage.presignedReadUrl(anyString(), any())).thenReturn("https://example.invalid/signed");
    }

    @Test
    @DisplayName("uploading a photo pauses the holder's pass")
    void uploadPausesThePass() {
        service.uploadStudentPhoto(PROFILE_ID, png("me.png"));

        ArgumentCaptor<String> reason = ArgumentCaptor.forClass(String.class);
        verify(passPauseClient).pauseAllForHolder(eq(HOLDER_ACCOUNT), reason.capture(), eq(EDITOR_ACCOUNT));
        assertThat(reason.getValue()).contains("photo");
    }

    @Test
    @DisplayName("the old object is deleted, and only after the new one is saved")
    void replacesTheOldObject() {
        var order = org.mockito.Mockito.inOrder(storage, studentRepository);

        service.uploadStudentPhoto(PROFILE_ID, png("me.png"));

        // Deleting first would be tidier and is wrong: if the upload then
        // failed, the person would be left with no photo at all.
        order.verify(storage).put(anyString(), any(), anyLong(), anyString());
        order.verify(studentRepository).save(any(StudentProfile.class));
        order.verify(storage).delete(OLD_KEY);
    }

    @Test
    @DisplayName("the new key is scoped to this campus and this account")
    void keyIsScopedToTheHolder() {
        service.uploadStudentPhoto(PROFILE_ID, png("me.png"));

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(storage).put(key.capture(), any(), anyLong(), eq("image/png"));

        assertThat(key.getValue()).startsWith("profiles/campus-1/students/108/photo-");
    }

    @Test
    @DisplayName("a file that is not really an image never reaches storage")
    void rejectedFileIsNeverStored() {
        MultipartFile fake = new MockMultipartFile(
                "file", "me.png", "image/png", "<html>".getBytes());

        assertThatThrownBy(() -> service.uploadStudentPhoto(PROFILE_ID, fake))
                .isInstanceOf(IllegalArgumentException.class);

        verify(storage, never()).put(anyString(), any(), anyLong(), anyString());
        verify(passPauseClient, never()).pauseAllForHolder(anyLong(), anyString(), anyLong());
    }

    @Test
    @DisplayName("removing a photo also pauses the pass")
    void removePausesToo() {
        // The pass still shows a face, and now there is nothing to check it against.
        service.removeStudentPhoto(PROFILE_ID);

        verify(storage).delete(OLD_KEY);
        verify(passPauseClient).pauseAllForHolder(eq(HOLDER_ACCOUNT), anyString(), eq(EDITOR_ACCOUNT));
    }

    @Test
    @DisplayName("removing a photo that was never there changes nothing")
    void removeIsANoOpWhenThereIsNoPhoto() {
        profile.setPhotoS3Key(null);

        service.removeStudentPhoto(PROFILE_ID);

        verify(storage, never()).delete(anyString());
        verify(passPauseClient, never()).pauseAllForHolder(anyLong(), anyString(), anyLong());
    }

    @Test
    @DisplayName("asking for a link when there is no photo is a 404, not an empty URL")
    void noPhotoMeansNotFound() {
        profile.setPhotoS3Key(null);

        assertThatThrownBy(() -> service.studentPhotoUrl(PROFILE_ID))
                .isInstanceOf(com.perimity.user.exception.ResourceNotFoundException.class)
                .hasMessageContaining("no photo");
    }

    @Test
    @DisplayName("a profile on another campus reads as not found")
    void anotherCampusIsInvisible() {
        when(currentUser.canSeeCampus(CAMPUS)).thenReturn(false);

        assertThatThrownBy(() -> service.uploadStudentPhoto(PROFILE_ID, png("me.png")))
                .isInstanceOf(com.perimity.user.exception.ResourceNotFoundException.class);
    }

    private MultipartFile png(String name) {
        return new MockMultipartFile("file", name, "image/png", PNG);
    }
}
