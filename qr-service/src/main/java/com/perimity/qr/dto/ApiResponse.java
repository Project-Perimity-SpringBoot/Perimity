// ============================================================
// PERIMITY - SHARED FILE. Every service must have this class,
// identical in every way EXCEPT the package line below.
//
// PUT IT HERE:
//   src/main/java/com/perimity/<service>/dto/ApiResponse.java
//
// CHANGE ONLY LINE 1: replace <service> with your service name
//   Omkar  -> com.perimity.auth.dto
//   Mukul  -> com.perimity.user.dto
//   Tushar -> com.perimity.gatepass.dto
//   Arham  -> com.perimity.campus.dto
//   Palash -> com.perimity.guard.dto
//   Sanjay -> com.perimity.qr.dto
//
// Do NOT rename the fields. The React frontend parses these exact
// names on every response from every service. If one service uses
// different names, every screen calling it breaks.
// ============================================================

package com.perimity.qr.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ApiResponse<T> {

    private boolean success;
    private String message;
    private T data;
    private List<String> errors;

    /** Success with data only. */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, "OK", data, null);
    }

    /** Success with a custom message. */
    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(true, message, data, null);
    }

    /** Failure with a list of error strings. */
    public static <T> ApiResponse<T> fail(String message, List<String> errors) {
        return new ApiResponse<>(false, message, null, errors);
    }
}
