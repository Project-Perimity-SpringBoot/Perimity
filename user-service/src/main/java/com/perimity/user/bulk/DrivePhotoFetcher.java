package com.perimity.user.bulk;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Downloads a passport photo from Google Drive by file id.
 *
 * ==========================================================================
 * WHY A SERVICE ACCOUNT AND NOT OAuth
 * ==========================================================================
 * Nobody is signing in. The server reads files on its own behalf, unattended,
 * during an import that may run long after the faculty member closed the tab.
 * OAuth would mean a refresh token belonging to a person, which expires when
 * they change their password or leave the institution - and then intakes stop
 * working for a reason nobody connects to a leaver.
 *
 * The account is granted nothing by default. It reads only what has been
 * shared with its address, which is why setup ends with sharing the responses
 * folder rather than assigning a project role.
 *
 * ==========================================================================
 * OFF IS A SUPPORTED STATE, NOT A BROKEN ONE
 * ==========================================================================
 * With GOOGLE_DRIVE_ENABLED=false, or no readable key, this component starts
 * happily and returns empty for every request. The import then runs without
 * photos and those students are counted separately and prompted to upload one
 * when they sign in.
 *
 * That matters beyond convenience: a Drive outage during an intake must delay
 * passes, not fail two hundred accounts. Refusing to start would turn a Google
 * problem into a user-service problem.
 */
@Component
public class DrivePhotoFetcher {

    private static final Logger log = LoggerFactory.getLogger(DrivePhotoFetcher.class);

    /**
     * A passport photo is tens of kilobytes. This cap exists because a Drive
     * file id can point at ANYTHING the account can read - a student uploading
     * a 200MB video to a photo question is careless rather than malicious, and
     * either way it must not be pulled into heap.
     */
    private static final long MAX_BYTES = 5L * 1024 * 1024;

    /** Magic bytes. The declared mime type is metadata and can be wrong. */
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G'};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] GIF = {'G', 'I', 'F', '8'};
    private static final byte[] RIFF = {'R', 'I', 'F', 'F'};

    private final boolean enabled;
    private final String credentialsPath;
    private Drive drive;

    public DrivePhotoFetcher(
            @Value("${perimity.google.drive-enabled:false}") boolean enabled,
            @Value("${perimity.google.credentials-path:}") String credentialsPath) {
        this.enabled = enabled;
        this.credentialsPath = credentialsPath;
    }

    /** One fetched image. Never holds anything that is not a real image. */
    public record Photo(byte[] bytes, String contentType, String filename) { }

    /**
     * The bytes for a Drive file id, or empty for any reason at all.
     *
     * EMPTY RATHER THAN THROWN, deliberately. This is called once per student
     * inside a batch, and the caller's rule is that one bad row never fails the
     * batch. A student whose photo cannot be read is a student without a pass
     * yet - recorded, counted, and prompted to upload one - not a reason to
     * abandon the other hundred and ninety-nine.
     */
    public Optional<Photo> fetch(String fileId) {
        if (!enabled || fileId == null || fileId.isBlank()) {
            return Optional.empty();
        }

        Drive client = client();
        if (client == null) {
            return Optional.empty();
        }

        try {
            com.google.api.services.drive.model.File meta =
                    client.files().get(fileId).setFields("id, name, size, mimeType").execute();

            Long size = meta.getSize();
            if (size != null && size > MAX_BYTES) {
                log.warn("Drive file {} is {} bytes, over the {} MB cap - skipped.",
                        fileId, size, MAX_BYTES / 1024 / 1024);
                return Optional.empty();
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            client.files().get(fileId).executeMediaAndDownloadTo(out);
            byte[] bytes = out.toByteArray();

            if (bytes.length == 0) {
                log.warn("Drive file {} was empty.", fileId);
                return Optional.empty();
            }

            /*
             * Checked by CONTENT, not by the mime type Drive reports. A file id
             * can point at anything the service account can read, and a
             * declared image/jpeg proves nothing about the bytes. This is the
             * same check UploadValidator makes on an in-app upload, and it
             * belongs here for the same reason: the storage bucket should hold
             * images, and what a guard's screen renders should be an image.
             */
            if (!looksLikeImage(bytes)) {
                log.warn("Drive file {} ({}) is not an image whatever it claims to be.",
                        fileId, meta.getName());
                return Optional.empty();
            }

            return Optional.of(new Photo(bytes, contentTypeOf(bytes), meta.getName()));

        } catch (IOException | RuntimeException ex) {
            // A 404 here usually means the responses folder was never shared
            // with the service account - the step everyone misses.
            log.warn("Could not fetch Drive file {}: {}", fileId, ex.getMessage());
            return Optional.empty();
        }
    }

    /**
     * A Google Sheet exported as .xlsx, so the same parser reads it whether it
     * came from Drive or from a file somebody uploaded.
     *
     * ==================================================================
     *  export, NOT get
     * ==================================================================
     * A Google Sheet is not a file with bytes - it is a document in Google's
     * own format, and files().get() on one returns a 403 saying so. export()
     * is what renders it into something else, and asking for xlsx means the
     * result is byte-identical in shape to what a person would have downloaded
     * by hand.
     *
     * That matters more than it sounds: it means Pull and Download and Upload
     * all end up in exactly the same parser, so a bug can never appear on one
     * path and not the others.
     *
     * Returns empty rather than throwing, like fetch() above - the caller
     * reports a readable failure instead of a stack trace.
     */
    public Optional<byte[]> exportSheet(String sheetId) {
        if (!enabled || sheetId == null || sheetId.isBlank()) {
            return Optional.empty();
        }
        Drive client = client();
        if (client == null) {
            return Optional.empty();
        }

        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            client.files()
                    .export(sheetId,
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    .executeMediaAndDownloadTo(out);

            byte[] bytes = out.toByteArray();
            if (bytes.length == 0) {
                log.warn("Sheet {} exported as zero bytes.", sheetId);
                return Optional.empty();
            }
            /*
             * An .xlsx is a zip, so it starts "PK". Checked for the same reason
             * the photo fetcher checks magic bytes: a Drive id can point at
             * anything the account can read, and being handed a PDF here would
             * fail later inside POI with a message about the wrong thing.
             */
            if (bytes.length < 4 || bytes[0] != 'P' || bytes[1] != 'K') {
                log.warn("Sheet {} did not export as a workbook.", sheetId);
                return Optional.empty();
            }
            log.info("Exported sheet {} ({} bytes).", sheetId, bytes.length);
            return Optional.of(bytes);

        } catch (IOException | RuntimeException ex) {
            // A 404 here almost always means the sheet was never shared with
            // the service account - the step everyone misses.
            log.warn("Could not export sheet {}: {}", sheetId, ex.getMessage());
            return Optional.empty();
        }
    }

    /** True when Drive is switched on AND the credentials actually loaded. */
    public boolean isUsable() {
        return enabled && client() != null;
    }

    /**
     * Built once, lazily, on first use.
     *
     * Lazy rather than in the constructor so a missing or unreadable key cannot
     * stop the service starting. user-service serves profile reads, gate
     * lookups and the whole verification flow; none of that needs Drive, and
     * none of it should be unavailable because a JSON file is not where a
     * variable says it is.
     */
    private synchronized Drive client() {
        if (drive != null) {
            return drive;
        }
        try {
            Path path = Path.of(credentialsPath);
            if (credentialsPath.isBlank() || !Files.isReadable(path)) {
                log.warn("Drive is enabled but the credentials at '{}' are missing or "
                        + "unreadable. Imports will run without photos.", credentialsPath);
                return null;
            }

            GoogleCredentials credentials;
            try (InputStream in = new FileInputStream(path.toFile())) {
                // Read-only scope. This account never needs to write to anyone's
                // Drive, and a credential that cannot write cannot destroy
                // somebody's folder because of a bug in this file.
                credentials = GoogleCredentials.fromStream(in)
                        .createScoped(List.of(DriveScopes.DRIVE_READONLY));
            }

            drive = new Drive.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance(),
                    new HttpCredentialsAdapter(credentials))
                    .setApplicationName("Perimity")
                    .build();

            log.info("Google Drive client ready.");
            return drive;

        } catch (Exception ex) {
            log.error("Could not build the Drive client. Imports will run without photos: {}",
                    ex.getMessage());
            return null;
        }
    }

    private static boolean looksLikeImage(byte[] bytes) {
        return startsWith(bytes, PNG) || startsWith(bytes, JPEG)
                || startsWith(bytes, GIF) || isWebp(bytes);
    }

    /** WebP is RIFF....WEBP - the marker is at byte 8, not at the start. */
    private static boolean isWebp(byte[] bytes) {
        return bytes.length > 12 && startsWith(bytes, RIFF)
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (bytes[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    /** Derived from the bytes, never from what Drive said. */
    private static String contentTypeOf(byte[] bytes) {
        if (startsWith(bytes, PNG)) {
            return "image/png";
        }
        if (startsWith(bytes, JPEG)) {
            return "image/jpeg";
        }
        if (isWebp(bytes)) {
            return "image/webp";
        }
        return "image/gif";
    }
}
