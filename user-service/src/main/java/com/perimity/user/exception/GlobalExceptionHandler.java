package com.perimity.user.exception;

import com.perimity.user.dto.ApiResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import com.perimity.user.storage.StorageException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Turns every validation failure into the same ApiResponse shape the rest of
 * the team consumes, instead of Spring's default error body.
 *
 * Without this class a failed @Valid returns a 400 whose body is nothing like
 * ApiResponse, and a Hibernate constraint violation surfaces as a raw 500. The
 * frontend would need two extra code paths for no reason.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** @Valid failed on a request body. One entry per bad field. */
    @org.springframework.web.bind.annotation.ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleBodyValidation(MethodArgumentNotValidException ex) {
        List<String> errors = new ArrayList<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            errors.add(fe.getField() + ": " + fe.getDefaultMessage());
        }
        ex.getBindingResult().getGlobalErrors()
                .forEach(ge -> errors.add(ge.getObjectName() + ": " + ge.getDefaultMessage()));
        return badRequest("Validation failed", errors);
    }

    /** @Validated failed on a path variable or request parameter. */
    @org.springframework.web.bind.annotation.ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleParamValidation(ConstraintViolationException ex) {
        List<String> errors = new ArrayList<>();
        for (ConstraintViolation<?> cv : ex.getConstraintViolations()) {
            errors.add(cv.getPropertyPath() + ": " + cv.getMessage());
        }
        return badRequest("Validation failed", errors);
    }

    /** Malformed JSON, or a date string that is not a date. */
    @org.springframework.web.bind.annotation.ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException ex) {
        return badRequest("Request body could not be read",
                List.of("Check the JSON is well formed and dates are in yyyy-MM-dd format"));
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParam(MissingServletRequestParameterException ex) {
        return badRequest("Missing required parameter", List.of(ex.getParameterName() + " is required"));
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return badRequest("Invalid parameter type", List.of(ex.getName() + " has the wrong type"));
    }

    /** A unique or not-null constraint the database rejected. */
    @org.springframework.web.bind.annotation.ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.fail("The request conflicts with existing data",
                        List.of("A record with these details already exists, or a required value was missing")));
    }

    /**
     * A row the caller named does not exist, or lives on another campus.
     *
     * Without this handler ResourceNotFoundException falls through as an
     * unhandled RuntimeException and Spring returns 500 with a stack trace. A
     * missing row is a 404.
     */
    @org.springframework.web.bind.annotation.ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.fail(ex.getMessage(), List.of()));
    }

    /**
     * The caller is signed in but may not touch THIS record - a student reading
     * another student's profile, or a Campus Admin reaching across campuses.
     *
     * 403, not 404 and not 401. 401 would tell them to log in again when they
     * already are, and they would keep trying.
     */
    @org.springframework.web.bind.annotation.ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiResponse<Void>> handleForbidden(ForbiddenException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.fail(ex.getMessage(), List.of()));
    }

    /**
     * @PreAuthorize refused the call - the wrong ROLE, rather than the wrong
     * record. Handled here as well as in SecurityConfig because method security
     * throws after the filter chain has already let the request through, so the
     * access-denied handler never sees it and Spring would return its own error
     * body instead of ApiResponse.
     */
    @org.springframework.web.bind.annotation.ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.fail(
                        "Your role is not permitted to perform this action", List.of()));
    }

    /**
     * The file was bigger than spring.servlet.multipart.max-file-size.
     *
     * Spring rejects it before any controller method runs, so the friendly
     * size message in UploadValidator never gets the chance to fire. Without
     * this handler the caller sees a bare 500 and has no idea what went wrong.
     *
     * 413, not 400: the request was well formed, there was simply too much of it.
     */
    @org.springframework.web.bind.annotation.ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleTooLarge(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiResponse.fail("That file is too large.",
                        List.of("Photos must be under 2 MB and documents under 5 MB.")));
    }

    /** A multipart request with no file part - usually a client sending JSON by mistake. */
    @org.springframework.web.bind.annotation.ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingPart(MissingServletRequestPartException ex) {
        return badRequest("No file was uploaded",
                List.of("Send this as multipart/form-data with a part named \"" + ex.getRequestPartName() + "\""));
    }

    /**
     * Object storage failed - the disk, the bucket, the network. 500, because
     * the caller did nothing wrong and retrying the same request is reasonable.
     */
    @org.springframework.web.bind.annotation.ExceptionHandler(StorageException.class)
    public ResponseEntity<ApiResponse<Void>> handleStorage(StorageException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail("The file could not be stored. Please try again.",
                        List.of()));
    }

    /**
     * The caller asked for something that cannot be done with the data they
     * sent - a Super Admin listing with no campus named, a photo declared as a
     * PDF. Their request is wrong, so 400.
     */
    @org.springframework.web.bind.annotation.ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(IllegalArgumentException ex) {
        return badRequest(ex.getMessage(), List.of());
    }

    /**
     * The request was well formed but collides with the state of the data - a
     * roll number already in use, an account that already has a profile, a
     * department that still has students attached.
     *
     * 409, not 400. The client sent nothing wrong and retrying the identical
     * request will not help; something in the database has to change first, and
     * a 400 would send the frontend hunting for a field to highlight that does
     * not exist.
     */
    @org.springframework.web.bind.annotation.ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleConflict(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.fail(ex.getMessage(), List.of()));
    }

    private ResponseEntity<ApiResponse<Void>> badRequest(String message, List<String> errors) {
        return ResponseEntity.badRequest().body(ApiResponse.fail(message, errors));
    }
}
