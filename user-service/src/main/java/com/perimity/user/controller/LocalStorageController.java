package com.perimity.user.controller;

import com.perimity.user.storage.LocalFileStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * DEVELOPMENT ONLY. Serves files back when storage is local.
 *
 * Exists purely so an uploaded photo can be displayed without an AWS account.
 * @ConditionalOnBean means it does not even load when storage is s3 - in
 * production S3 serves its own presigned URLs and this class is absent.
 *
 * =====================================================================
 *  THIS DIFFERS FROM campus-service's VERSION, DELIBERATELY
 * =====================================================================
 *
 * campus-service permits its local-storage path without authentication,
 * because what it serves is a campus logo - a public-facing image.
 *
 * This bucket holds people's photographs and identity documents. An open
 * endpoint over that, even in development, means anyone who learns a key can
 * read somebody's ID proof, and keys travel in API responses. So this path
 * stays behind the JWT filter like everything else here.
 *
 * The practical cost: a bare <img src="..."> will not work, because a browser
 * does not attach an Authorization header to image requests. The frontend has
 * to fetch the URL with the API client and turn the response into a blob URL.
 * That is one extra step in one component, which is a fair price.
 */
@RestController
@RequestMapping("/api/user/storage/local")
@ConditionalOnBean(LocalFileStorageService.class)
@Tag(name = "Local storage (dev)", description = "Development only. Absent when storage is S3.")
public class LocalStorageController {

    private static final String PREFIX = "/api/user/storage/local/";

    private final LocalFileStorageService storage;

    public LocalStorageController(LocalFileStorageService storage) {
        this.storage = storage;
    }

    @GetMapping("/**")
    @Operation(summary = "Serve a locally stored file")
    public ResponseEntity<Resource> serve(HttpServletRequest request) throws IOException {

        String key = request.getRequestURI().substring(PREFIX.length());

        // resolve() refuses anything that escapes the storage root.
        Path file = storage.resolve(key);
        if (!Files.exists(file) || Files.isDirectory(file)) {
            return ResponseEntity.notFound().build();
        }

        String contentType = Files.probeContentType(file);

        return ResponseEntity.ok()
                .contentType(contentType == null
                        ? MediaType.APPLICATION_OCTET_STREAM
                        : MediaType.parseMediaType(contentType))
                // Content-Disposition: inline with a fixed name, and nosniff, so
                // a file that somehow got stored as HTML cannot execute as a
                // page on our own origin.
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new FileSystemResource(file));
    }
}
