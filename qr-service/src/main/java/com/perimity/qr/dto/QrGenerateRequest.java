package com.perimity.qr.dto;

import com.perimity.qr.validation.ValidDateRange;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The pass.generate message payload dropped on RabbitMQ by gatepass-service.
 *
 * This is the single most important DTO in qr-service to validate hard,
 * because it is the only thing that ever causes a QrRecord to exist. A bad
 * message here becomes a bad QR, which becomes a guard staring at a RED
 * result while a real student stands at the gate.
 *
 * @NoArgsConstructor and @Setter are required, not stylistic: Jackson
 * deserialises into this class and needs a no-arg constructor plus mutators.
 * The response DTOs in this package are outbound only and correctly have
 * neither.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
// endNullable = true: a standing DAILY pass has no end date. This is the same
// rule QrRecord enforces, applied one layer earlier so a bad range is a 400
// at the edge rather than a constraint violation deep in the consumer.
@ValidDateRange(from = "validFrom", to = "validTo", endNullable = true)
public class QrGenerateRequest {

    /**
     * @Positive as well as @NotNull. Ids in this system are IDENTITY columns
     * starting at 1, so 0 or a negative is always a caller bug - usually an
     * uninitialised long. Catching it here turns a silent orphan row into a
     * loud 400.
     */
    @NotNull(message = "passId is required")
    @Positive(message = "passId must be a positive id")
    private Long passId;

    @NotNull(message = "campusId is required")
    @Positive(message = "campusId must be a positive id")
    private Long campusId;

    /**
     * Null for a single approval, set for every row of a bulk upload.
     * Deliberately no @NotNull - but if it IS present it must still be sane,
     * which is why @Positive stays. Bean Validation skips a null value for
     * @Positive, so the two rules coexist without contradiction.
     */
    @Positive(message = "batchId must be a positive id when present")
    private Long batchId;

    @NotNull(message = "validFrom is required")
    private LocalDate validFrom;

    /**
     * Null for a standing DAILY pass. No @FutureOrPresent on either date:
     * re-issuing a QR for an existing pass keeps that pass's original
     * validFrom, which is legitimately in the past.
     */
    private LocalDate validTo;
}
