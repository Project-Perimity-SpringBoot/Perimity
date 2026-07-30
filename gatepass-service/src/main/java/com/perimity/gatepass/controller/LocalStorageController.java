package com.perimity.gatepass.controller;

import com.perimity.gatepass.storage.LocalFileStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * DEVELOPMENT ONLY. Serves files back when storage is local.
 *
 * Exists purely so a browser can display an uploaded logo without an AWS
 * account. @ConditionalOnBean means it does not even load when storage is s3 -
 * in production S3 serves its own presigned URLs and this class is absent.
 *
 * There is no auth on it, which is exactly why it must never reach production.
 */
@RestController
@RequestMapping("/api/gatepass/storage/local")
@ConditionalOnBean(LocalFileStorageService.class)
@Tag(name = "Local storage (dev)", description = "Development only. Absent when storage is S3.")
public class LocalStorageController {

    private final LocalFileStorageService storage;

    public LocalStorageController(LocalFileStorageService storage) {
        this.storage = storage;
    }

    @GetMapping("/**")
    @Operation(summary = "Serve a locally stored file")
    public ResponseEntity<Resource> serve(jakarta.servlet.http.HttpServletRequest request)
            throws IOException {

        String key = request.getRequestURI()
                .substring("/api/gatepass/storage/local/".length());

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
                .body(new FileSystemResource(file));
    }
}
