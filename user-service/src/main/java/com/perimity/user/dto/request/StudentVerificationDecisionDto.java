package com.perimity.user.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Body of PATCH /api/user/students/{id}/verification - faculty accepting or
 * refusing a student's submitted details.
 *
 * ==========================================================================
 * WHY THERE IS NO verifiedBy FIELD HERE
 * ==========================================================================
 * DocumentVerificationDto, the older sibling of this class, has a @NotNull
 * verifiedBy in its body with a comment telling the service layer to ignore it.
 * That is a trap: the field is a lie the moment anyone trusts it, and the only
 * thing keeping it honest is a comment. A caller can put any user id in there.
 *
 * The identity of the person deciding is not an input. It is taken from the
 * authenticated principal in the controller. If it is not in the DTO, it cannot
 * be forged, and no future maintainer can accidentally start trusting it.
 *
 * Same reasoning as removing campusId from the guard-service scan DTOs.
 */
@Schema(description = "A faculty decision on a student's submitted details")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentVerificationDecisionDto {

    @NotNull(message = "A decision is required - approve or reject")
    @Schema(description = "true accepts the details, false sends them back", example = "true")
    private Boolean approved;

    /**
     * Optional on approval, mandatory on rejection. A student who is told "no"
     * without being told why cannot fix anything, and will simply resubmit the
     * same details - which wastes the reviewer's time as much as the student's.
     */
    @Size(max = 500, message = "Keep remarks under 500 characters")
    @Schema(description = "Required when approved is false")
    private String remarks;

    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "Say why you are rejecting - the student sees this and needs it to correct the details")
    public boolean isRemarksPresentWhenRejected() {
        if (approved == null || Boolean.TRUE.equals(approved)) {
            return true;
        }
        return remarks != null && !remarks.isBlank();
    }
}
