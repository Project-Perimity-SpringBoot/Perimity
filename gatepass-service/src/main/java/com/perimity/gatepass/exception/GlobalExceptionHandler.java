package com.perimity.gatepass.exception;

import com.perimity.gatepass.dto.ApiResponse;
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

    /**
     * A row the caller named does not exist, or lives on another campus.
     *
     * Without this handler ResourceNotFoundException falls through as an
     * unhandled RuntimeException and Spring returns 500 with a stack trace -
     * which is what happened before it was added. A missing row is a 404.
     */
    @org.springframework.web.bind.annotation.ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.fail(ex.getMessage(), List.of()));
    }

    /** The caller is signed in but not allowed to touch this particular record. */
    @org.springframework.web.bind.annotation.ExceptionHandler(
            com.perimity.gatepass.security.CurrentUser.AccessDeniedInThisServiceException.class)
    public ResponseEntity<ApiResponse<Void>> handleForbidden(
            com.perimity.gatepass.security.CurrentUser.AccessDeniedInThisServiceException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.fail(ex.getMessage(), List.of()));
    }

    /** A @PreAuthorize refusal. Spring throws its own type for these. */
    @org.springframework.web.bind.annotation.ExceptionHandler(
            org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleSpringForbidden(
            org.springframework.security.access.AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.fail("Your role is not permitted to perform this action",
                        List.of()));
    }

    /** Business-rule failures thrown deliberately by the service layer. */
    @org.springframework.web.bind.annotation.ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<ApiResponse<Void>> handleBusinessRule(RuntimeException ex) {
        return badRequest(ex.getMessage(), List.of());
    }

    private ResponseEntity<ApiResponse<Void>> badRequest(String message, List<String> errors) {
        return ResponseEntity.badRequest().body(ApiResponse.fail(message, errors));
    }
}
