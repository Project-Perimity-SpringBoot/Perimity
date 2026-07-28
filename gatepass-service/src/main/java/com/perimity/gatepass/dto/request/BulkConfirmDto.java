package com.perimity.gatepass.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Body of POST /api/gatepass/bulk/{batchId}/confirm - phase two.
 *
 * The batch moves VALIDATED -> PROCESSING and the valid rows are handed to
 * RabbitMQ. The uploader is free to close the browser after this point.
 */
@Schema(description = "Release a validated batch to the asynchronous generation queue")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BulkConfirmDto {

    @NotNull(message = "The confirming user is required")
    @Positive(message = "User id must be a positive number")
    @Schema(example = "42")
    private Long confirmedBy;

    @NotNull(message = "Explicit confirmation is required")
    @AssertTrue(message = "The batch cannot be released until confirmed is true")
    @Schema(description = "Must be true. Guards against an accidental POST releasing 600 passes.",
            example = "true")
    private Boolean confirmed;
}
