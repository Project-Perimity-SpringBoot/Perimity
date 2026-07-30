package com.perimity.guard.exception;

import com.perimity.guard.dto.ApiResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
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

    /** A document the caller named does not exist. */
    @org.springframework.web.bind.annotation.ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.fail(ex.getMessage(), List.of()));
    }

    /** Business-rule failures thrown deliberately by the service layer. */
    @org.springframework.web.bind.annotation.ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<ApiResponse<Void>> handleBusinessRule(RuntimeException ex) {
        return badRequest(ex.getMessage(), List.of());
    }

    /**
     * The authorisation check a role annotation cannot express: right role,
     * wrong record. Thrown by CurrentUser.
     *
     * 403, not 401 - we know exactly who the caller is, they simply may not do
     * this. Unmapped, this surfaces as a 500, and the Day 7 gate is stated in
     * terms of a 403, so the day would fail on a case that is working correctly.
     */
    @org.springframework.web.bind.annotation.ExceptionHandler(
            com.perimity.guard.security.CurrentUser.ForbiddenException.class)
    public ResponseEntity<ApiResponse<Void>> handleForbidden(
            com.perimity.guard.security.CurrentUser.ForbiddenException ex) {

        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.fail(ex.getMessage(), List.of()));
    }

    /**
     * qr-service or gatepass-service is unreachable, so we cannot say whether a
     * pass is valid.
     *
     * 503, not 200-with-a-denial. FR-SCAN-10 requires the guard to tell "this
     * pass is invalid" apart from "the scanner is broken" - they demand opposite
     * actions. The scanner UI renders its full-screen red card from a 200 body,
     * so a distinct status code is what keeps an outage off that screen.
     *
     * Note that no EntryLog is written on this path. An outage is not a scan, and
     * recording it as a refusal would put a denial against the name of someone
     * who may hold a perfectly valid pass.
     */
    @org.springframework.web.bind.annotation.ExceptionHandler(
            com.perimity.guard.client.PassVerificationUnavailableException.class)
    public ResponseEntity<ApiResponse<Void>> handleVerificationUnavailable(
            com.perimity.guard.client.PassVerificationUnavailableException ex) {

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.fail(ex.getMessage(),
                        List.of("SCANNER_UNAVAILABLE")));
    }

    private ResponseEntity<ApiResponse<Void>> badRequest(String message, List<String> errors) {
        return ResponseEntity.badRequest().body(ApiResponse.fail(message, errors));
    }
}
