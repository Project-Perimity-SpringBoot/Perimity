package com.perimity.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Body of POST /api/user/internal/profiles/summaries
 *
 * WHY A BATCH ENDPOINT EXISTS AT ALL
 * A bulk upload is up to a thousand rows. Enriching each one through
 * GET /profiles/{userId}/summary is a thousand HTTP round trips against a
 * service that could answer all of them in two queries. At 5 ms each that is
 * five seconds of pure network for something that should take milliseconds,
 * and every one of those requests opens a connection, parses a token and
 * starts a transaction.
 *
 * WHY POST AND NOT GET
 * A thousand ids in a query string is roughly 8 KB of URL. Tomcat's default
 * max-http-header-size is 8 KB, so the request would start failing somewhere
 * past 900 rows - and it would fail as a 400 with no useful message, on the
 * large batches rather than the small ones anybody tests with. A body has no
 * such limit. This is a read expressed as a POST, which is unusual and
 * deliberate.
 *
 * THE CAP IS NOT ARBITRARY
 * 1000 matches BULK_MAX_ROWS in .env - the largest batch the system accepts -
 * so a caller can always ask about a whole batch in one call and never more.
 * Without a cap this endpoint is a way to make one request cost unbounded
 * work.
 */
@Schema(description = "Resolve many accounts to their profiles in one call")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProfileSummaryBatchRequest {

    @NotEmpty(message = "Provide at least one user id")
    @Size(max = 1000, message = "At most 1000 user ids per call")
    @Schema(description = "Account ids, as returned by auth-service", example = "[108, 109, 200]")
    private List<@Positive(message = "User ids must be positive") Long> userIds;
}
