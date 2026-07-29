package com.perimity.gatepass.dto.request;

import com.perimity.gatepass.validation.ValidationPatterns;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Body of POST /api/gatepass/internal/passes/{id}/activate
 *
 * qr-service calls this once the QR image and the PDF are in object storage.
 * Both keys are mandatory: activating a pass without them would produce an
 * ACTIVE pass that scans green but has no QR for anyone to present.
 *
 * INTERNAL. A holder who could call this would activate their own pending pass
 * and skip the entire generation pipeline.
 */
@Schema(description = "qr-service reports that generation finished and hands over the keys")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PassActivationDto {

    @NotBlank(message = "The QR object key is required")
    @Size(max = 512)
    @Pattern(regexp = ValidationPatterns.OBJECT_KEY, message = ValidationPatterns.OBJECT_KEY_MESSAGE)
    @Schema(example = "campus-1/passes/pass-123-qr.png")
    private String qrKey;

    @NotBlank(message = "The PDF object key is required")
    @Size(max = 512)
    @Pattern(regexp = ValidationPatterns.OBJECT_KEY, message = ValidationPatterns.OBJECT_KEY_MESSAGE)
    @Schema(example = "campus-1/passes/pass-123.pdf")
    private String pdfKey;
}
