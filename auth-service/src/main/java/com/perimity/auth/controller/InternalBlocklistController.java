package com.perimity.auth.controller;

import com.perimity.auth.dto.ApiResponse;
import com.perimity.auth.dto.request.BulkScreenRequestDto;
import com.perimity.auth.dto.response.BulkScreenResponseDto;
import com.perimity.auth.service.BlocklistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service blocklist screening. Day 10.
 *
 * Guarded by InternalApiKeyFilter on the /api/internal/ prefix, never by a JWT -
 * gatepass-service's bulk engine is a service, not a person with a token.
 *
 * There is no GET here and never should be. The Campus Admin's blocklist screens
 * live on BlocklistController behind a JWT and a role check. This controller
 * answers exactly one question - "may these rows proceed" - and answers it with
 * OK or REFUSED and nothing else.
 */
@RestController
@RequestMapping("/api/internal/auth/blocklist")
@Validated
@Tag(name = "Internal - Blocklist", description = "Bulk screening for the upload engine")
public class InternalBlocklistController {

    private final BlocklistService service;
    private final int maxRows;

    public InternalBlocklistController(BlocklistService service,
                                       @Value("${perimity.bulk.max-rows}") int maxRows) {
        this.service = service;
        this.maxRows = maxRows;
    }

    /**
     * The fast-path check, called during validation before anything is created.
     *
     * @Valid is mandatory. Without it the nested row constraints never run and
     * a list of empty objects is accepted in silence.
     */
    @PostMapping("/screen")
    @Operation(summary = "Screen many rows against one campus's blocklist. "
            + "A refused row carries no reason, per FR-BLK-4.")
    public ApiResponse<BulkScreenResponseDto> screen(@Valid @RequestBody BulkScreenRequestDto dto) {
        enforceRowLimit(dto.getRows().size());
        return ApiResponse.ok("Screened", service.screen(dto));
    }

    /**
     * States the limit in the message, per FR-BULK-8. "Too many rows" sends the
     * uploader back to guess; naming the number lets them split the sheet.
     *
     * A second guard rail behind Tushar's, not a replacement for it. He rejects
     * the oversized upload at the door with the campus's own configured limit;
     * this stops a caller that skipped that check from putting an unbounded list
     * into one transaction here.
     */
    private void enforceRowLimit(int rows) {
        if (rows > maxRows) {
            throw new IllegalArgumentException(
                    "This request has " + rows + " rows. The maximum is " + maxRows + ".");
        }
    }
}
