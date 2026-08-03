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
 * The internal input to QR generation.
 *
 * NO LONGER the wire payload. As of Day 8 the queue carries Tushar's
 * QrGenerationJob record, and QrGenerationListener maps it onto this class -
 * partly because his contract is much wider than generation needs, and partly
 * because the constraints below are the only validation a queue message gets.
 * Keeping them on the narrow type means the mapping step is the checkpoint.
 *
 * This is the single most important DTO in qr-service to validate hard,
 * because it is the only thing that ever causes a QrRecord to exist. A bad
 * message here becomes a bad QR, which becomes a guard staring at a RED
 * result while a real student stands at the gate.
 *
 * @NoArgsConstructor and @Setter are retained even though Jackson no longer
 * deserialises into this class: the HTTP generate path still binds a body to it,
 * and QrGenerationListenerTest mutates an instance to build an invalid case.
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

    /**
     * PROPOSAL. The holder, carried through so QrRecordService can persist it.
     *
     * Not @NotNull: gatepass-service is already sending this field, but a
     * message published by an older build would not, and rejecting those to the
     * DLQ would stop QR generation for a field nothing depended on until now.
     */
    @Positive(message = "holderUserId must be a positive id when present")
    private Long holderUserId;

    @NotNull(message = "validFrom is required")
    private LocalDate validFrom;

    /**
     * Null for a standing DAILY pass. No @FutureOrPresent on either date:
     * re-issuing a QR for an existing pass keeps that pass's original
     * validFrom, which is legitimately in the past.
     */
    private LocalDate validTo;
}
