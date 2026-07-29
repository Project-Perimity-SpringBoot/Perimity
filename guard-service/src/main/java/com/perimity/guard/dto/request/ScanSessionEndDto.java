package com.perimity.guard.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Body of POST /api/guard/sessions/{id}/end
 *
 * guardUserId is checked against the stored session so one guard cannot close
 * another's shift by guessing an id.
 *
 * endedAt is not accepted from the client. The server stamps it - a shift that
 * can be back-dated is a shift that can be falsified.
 */
@Schema(description = "Close an open shift")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScanSessionEndDto {

    @NotNull(message = "Guard is required")
    @Positive(message = "Guard user id must be a positive number")
    @Schema(description = "Must match the guard who opened the session", example = "55")
    private Long guardUserId;
}
