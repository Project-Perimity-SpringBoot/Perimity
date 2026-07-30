package com.perimity.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

/**
 * The Day 9 rule the SRS is explicit about: the content type a browser sends is
 * a CLAIM, not a fact.
 *
 * Every test here is a file that lies about what it is. Renaming an executable
 * to photo.png takes one second and the browser reports image/png quite
 * honestly - it is only repeating what the extension said. If these checks ever
 * stop running, uploads keep succeeding and nothing errors; the damage shows up
 * later, when an admin opens what they think is an ID proof.
 */
class UploadValidatorTest {

    private static final long TWO_MB = 2L * 1024 * 1024;
    private static final long FIVE_MB = 5L * 1024 * 1024;

    private final UploadValidator validator = new UploadValidator();

    // Real leading bytes for each format.
    private static final byte[] PNG_HEADER = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] JPEG_HEADER = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
    private static final byte[] PDF_HEADER = {'%', 'P', 'D', 'F', '-', '1', '.', '4'};

    @Test
    @DisplayName("a real PNG is accepted as a photo")
    void acceptsRealPng() {
        assertThat(validator.validatePhoto(file("photo.png", "image/png", PNG_HEADER), TWO_MB))
                .isEqualTo("image/png");
    }

    @Test
    @DisplayName("a real WebP is accepted - RIFF container, WEBP at byte 8")
    void acceptsRealWebp() {
        // WebP is the awkward one: the first four bytes are RIFF, which GIF-era
        // AVI files also use. The format only becomes unambiguous at byte 8.
        byte[] webp = new byte[16];
        System.arraycopy("RIFF".getBytes(StandardCharsets.US_ASCII), 0, webp, 0, 4);
        System.arraycopy("WEBP".getBytes(StandardCharsets.US_ASCII), 0, webp, 8, 4);

        assertThatCode(() -> validator.validatePhoto(file("photo.webp", "image/webp", webp), TWO_MB))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("an executable renamed to .png and sent as image/png is refused")
    void refusesAnExecutableWearingAPngName() {
        // "MZ" is the DOS header every Windows executable starts with.
        byte[] executable = {'M', 'Z', (byte) 0x90, 0x00, 0x03, 0x00, 0x00, 0x00};

        assertThatThrownBy(() ->
                validator.validatePhoto(file("photo.png", "image/png", executable), TWO_MB))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not the image it claims to be");
    }

    @Test
    @DisplayName("an HTML file sent as image/png is refused")
    void refusesHtmlWearingAPngName() {
        // This is the one that matters most. Stored HTML served back from our
        // own origin is stored cross-site scripting against whoever opens it.
        byte[] html = "<html><script>alert(1)</script>".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() ->
                validator.validatePhoto(file("photo.png", "image/png", html), TWO_MB))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("an SVG is refused outright - it is XML and can carry script")
    void refusesSvg() {
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"></svg>".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() ->
                validator.validatePhoto(file("logo.svg", "image/svg+xml", svg), TWO_MB))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be one of");
    }

    @Test
    @DisplayName("a PDF is a valid document but not a valid photo")
    void pdfIsADocumentNotAPhoto() {
        assertThat(validator.validateDocument(file("id.pdf", "application/pdf", PDF_HEADER), FIVE_MB))
                .isEqualTo("application/pdf");

        assertThatThrownBy(() ->
                validator.validatePhoto(file("id.pdf", "application/pdf", PDF_HEADER), TWO_MB))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a file over the cap is refused with the limit in the message")
    void refusesOversizedFiles() {
        byte[] big = new byte[(int) TWO_MB + 1];
        System.arraycopy(JPEG_HEADER, 0, big, 0, JPEG_HEADER.length);

        assertThatThrownBy(() -> validator.validatePhoto(file("big.jpg", "image/jpeg", big), TWO_MB))
                .isInstanceOf(IllegalArgumentException.class)
                // A number the person can act on, not just "too large".
                .hasMessageContaining("2 MB");
    }

    @Test
    @DisplayName("an empty upload is refused before anything else runs")
    void refusesEmptyUpload() {
        assertThatThrownBy(() ->
                validator.validatePhoto(file("empty.png", "image/png", new byte[0]), TWO_MB))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No file");
    }

    @Test
    @DisplayName("a missing content type is refused rather than assumed")
    void refusesMissingContentType() {
        assertThatThrownBy(() -> validator.validatePhoto(file("photo.png", null, PNG_HEADER), TWO_MB))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private MultipartFile file(String name, String contentType, byte[] content) {
        return new MockMultipartFile("file", name, contentType, content);
    }
}
