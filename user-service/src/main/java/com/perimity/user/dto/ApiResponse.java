package com.perimity.user.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** The uniform response shape used by all six Perimity services. */
@Getter
@AllArgsConstructor
public class ApiResponse<T> {

    private boolean success;
    private String message;
    private T data;
    private List<String> errors;

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, "OK", data, null);
    }

    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(true, message, data, null);
    }

    public static <T> ApiResponse<T> fail(String message, List<String> errors) {
        return new ApiResponse<>(false, message, null, errors);
    }
}
