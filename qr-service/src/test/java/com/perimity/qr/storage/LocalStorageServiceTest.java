package com.perimity.qr.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityNotFoundException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Plain JUnit, no Spring: LocalStorageService takes its root through the
 * constructor, so a temp directory is the whole fixture.
 *
 * The traversal cases are the reason this class exists. resolve() is a security
 * control - the only thing standing between a malformed object key and a write
 * outside the storage root - and it had no test at all. Its own comment says
 * why that matters: "writing outside the storage root is not a recoverable
 * mistake". A regression there is a file-write primitive, not a bug.
 *
 * Note these are deliberately NOT the same checks as ValidationPatterns
 * .OBJECT_KEY. That regex validates keys arriving from outside; this guards the
 * filesystem itself, which is the thing that actually gets damaged if a bad key
 * ever gets past validation. Two independent layers, tested independently.
 */
class LocalStorageServiceTest {

    private static final byte[] CONTENT = "qr-bytes".getBytes(StandardCharsets.UTF_8);
    private static final String PNG = "image/png";

    @TempDir
    Path root;

    private LocalStorageService storage;

    @BeforeEach
    void setUp() {
        storage = new LocalStorageService(root.toString());
    }

    // ---------------------------------------------------------------
    // The traversal guard
    // ---------------------------------------------------------------

    /**
     * normalize() collapses ".." before the startsWith check, so traversal is
     * caught however it is spelled - leading, buried mid-key, or repeated.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "../escaped.png",
            "..",
            "../../etc/passwd",
            "2/pdf/../../../escaped.pdf",
            "2/../../escaped.png",
            "./../../escaped.png"
    })
    void put_refusesAnyKeyThatEscapesTheRoot(String key) {
        assertThatThrownBy(() -> storage.put(key, CONTENT, PNG))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("escapes the storage root");
    }

    @ParameterizedTest
    @ValueSource(strings = {"../escaped.png", "../../etc/passwd", "2/../../escaped.png"})
    void get_refusesAnyKeyThatEscapesTheRoot(String key) {
        assertThatThrownBy(() -> storage.get(key))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("escapes the storage root");
    }

    /**
     * An absolute key is the case a startsWith check on the raw string would
     * miss entirely. Path.resolve(absolute) DISCARDS the base and returns the
     * absolute path, so "/etc/passwd" resolves to /etc/passwd rather than to
     * root + "/etc/passwd" - which is exactly why the guard compares the
     * resolved path against the root instead of inspecting the key.
     */
    @Test
    void put_refusesAnAbsoluteKey() {
        assertThatThrownBy(() -> storage.put("/etc/passwd", CONTENT, PNG))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("escapes the storage root");
    }

    /** Nothing may be written outside the root, even on a refused call. */
    @Test
    void aRefusedTraversalWritesNothingOutsideTheRoot() throws Exception {
        Path sibling = root.getParent().resolve("escaped.png");

        assertThatThrownBy(() -> storage.put("../escaped.png", CONTENT, PNG))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(Files.exists(sibling)).isFalse();
    }

    @Test
    void put_refusesABlankKey() {
        assertThatThrownBy(() -> storage.put("   ", CONTENT, PNG))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be blank");
    }

    @Test
    void put_refusesANullKey() {
        assertThatThrownBy(() -> storage.put(null, CONTENT, PNG))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * ".." is only dangerous as a whole path segment. A filename that merely
     * contains dots stays inside the root and must still work - over-tightening
     * the guard would reject legitimate keys, and the failure would look like
     * storage being broken.
     */
    @Test
    void put_allowsDotsInsideAFilename() {
        assertThatCode(() -> storage.put("2/pdf/1/pass..v2.pdf", CONTENT, PNG))
                .doesNotThrowAnyException();
    }

    // ---------------------------------------------------------------
    // Normal operation
    // ---------------------------------------------------------------

    @Test
    void put_thenGet_roundTripsTheBytes() {
        storage.put("2/qr/1/2410.png", CONTENT, PNG);

        assertThat(storage.get("2/qr/1/2410.png")).isEqualTo(CONTENT);
    }

    /** The key is returned so the caller can persist exactly what was written. */
    @Test
    void put_returnsTheKeyItWrote() {
        assertThat(storage.put("2/qr/1/2410.png", CONTENT, PNG)).isEqualTo("2/qr/1/2410.png");
    }

    /** The campus-prefixed key is several levels deep and the parents will not exist. */
    @Test
    void put_createsMissingParentDirectories() {
        storage.put("2/qr/1/2410.png", CONTENT, PNG);

        assertThat(Files.exists(root.resolve("2/qr/1/2410.png"))).isTrue();
    }

    /** No temp files may survive - the write is temp-then-atomic-move. */
    @Test
    void put_leavesNoTemporaryFilesBehind() throws Exception {
        storage.put("2/qr/1/2410.png", CONTENT, PNG);

        try (var entries = Files.list(root.resolve("2/qr/1"))) {
            assertThat(entries.map(p -> p.getFileName().toString()))
                    .containsExactly("2410.png");
        }
    }

    @Test
    void put_overwritesAnExistingObject() {
        byte[] replacement = "new-bytes".getBytes(StandardCharsets.UTF_8);

        storage.put("2/qr/1/2410.png", CONTENT, PNG);
        storage.put("2/qr/1/2410.png", replacement, PNG);

        assertThat(storage.get("2/qr/1/2410.png")).isEqualTo(replacement);
    }

    /**
     * EntityNotFoundException, not an IO error: QrRecordService lets this
     * propagate and GlobalExceptionHandler maps it to a 404, which is what the
     * pass view needs when generation has not finished yet.
     */
    @Test
    void get_missingObjectThrowsEntityNotFound() {
        assertThatThrownBy(() -> storage.get("2/qr/1/nothing-here.png"))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("No stored object for key");
    }

    @Test
    void exists_reflectsWhetherTheObjectIsThere() {
        assertThat(storage.exists("2/qr/1/2410.png")).isFalse();

        storage.put("2/qr/1/2410.png", CONTENT, PNG);

        assertThat(storage.exists("2/qr/1/2410.png")).isTrue();
    }

    /** The root is created on construction, so a fresh deployment just works. */
    @Test
    void constructor_createsTheRootWhenItDoesNotExist() {
        Path nested = root.resolve("does/not/exist/yet");

        new LocalStorageService(nested.toString());

        assertThat(Files.isDirectory(nested)).isTrue();
    }
}
