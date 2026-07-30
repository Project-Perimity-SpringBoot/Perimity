package com.perimity.user.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.perimity.user.validation.ValidationPatterns;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Keys are generated here and never accepted from a client (SRS v1.1).
 *
 * The old upload endpoint took an s3Key in the request body, which let a caller
 * name a path in somebody else's folder. These tests pin down the two
 * properties that replaced it: a key always lands under the right person's
 * prefix, and whatever a filename contains cannot change that.
 */
class StorageKeysTest {

    @Test
    @DisplayName("a student photo key is scoped to the campus and the account")
    void studentPhotoIsScoped() {
        String key = StorageKeys.studentPhoto(1L, 108L, "my photo.JPG");

        assertThat(key).startsWith("profiles/campus-1/students/108/photo-");
        assertThat(key).endsWith(".jpg");
    }

    @Test
    @DisplayName("faculty and students land in different folders")
    void facultyAndStudentsAreSeparated() {
        assertThat(StorageKeys.facultyPhoto(1L, 42L, "x.png"))
                .startsWith("profiles/campus-1/faculty/42/");
        assertThat(StorageKeys.studentPhoto(1L, 42L, "x.png"))
                .startsWith("profiles/campus-1/students/42/");
    }

    @Test
    @DisplayName("two uploads never produce the same key")
    void everyKeyIsUnique() {
        // Overwriting one key means a cached copy in CloudFront keeps serving
        // the old image, and on a gate pass that is the wrong face beside the
        // right name.
        assertThat(StorageKeys.studentPhoto(1L, 108L, "photo.png"))
                .isNotEqualTo(StorageKeys.studentPhoto(1L, 108L, "photo.png"));
    }

    @Test
    @DisplayName("a hostile filename cannot walk out of the person's folder")
    void filenameCannotEscapeThePrefix() {
        String key = StorageKeys.document(1L, 108L, "../../../etc/passwd");

        assertThat(key).startsWith("profiles/campus-1/users/108/documents/");
        assertThat(key).doesNotContain("..");
        assertThat(key).doesNotContain("/etc/");
    }

    @Test
    @DisplayName("an absurdly long filename cannot overflow the 512-character column")
    void longFilenamesAreTruncated() {
        String key = StorageKeys.document(1L, 108L, "a".repeat(400) + ".pdf");

        assertThat(key.length()).isLessThanOrEqualTo(512);
    }

    @Test
    @DisplayName("every generated key satisfies the OBJECT_KEY pattern the entity enforces")
    void keysAlwaysMatchTheEntityPattern() {
        // If this ever fails, uploads succeed and the row is then rejected by
        // bean validation - a 500 after the bytes are already in storage, which
        // is the worst possible ordering.
        assertThat(StorageKeys.studentPhoto(1L, 108L, "photo.png")).matches(ValidationPatterns.OBJECT_KEY);
        assertThat(StorageKeys.facultyPhoto(2L, 42L, "IMG_0042.JPEG")).matches(ValidationPatterns.OBJECT_KEY);
        assertThat(StorageKeys.document(3L, 7L, "id proof (final).pdf")).matches(ValidationPatterns.OBJECT_KEY);
        assertThat(StorageKeys.document(1L, 1L, "../../escape")).matches(ValidationPatterns.OBJECT_KEY);
    }

    @Test
    @DisplayName("a filename with no usable extension still produces a valid key")
    void handlesMissingExtension() {
        assertThat(StorageKeys.studentPhoto(1L, 108L, "photo")).matches(ValidationPatterns.OBJECT_KEY);
        assertThat(StorageKeys.studentPhoto(1L, 108L, null)).matches(ValidationPatterns.OBJECT_KEY);
    }
}
