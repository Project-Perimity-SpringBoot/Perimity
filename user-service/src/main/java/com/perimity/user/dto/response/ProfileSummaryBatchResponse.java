package com.perimity.user.dto.response;

import java.util.List;

/**
 * What a batch summary lookup returns.
 *
 * ==========================================================
 *  A MISS IS NOT AN ERROR. This is the important part.
 * ==========================================================
 *
 * The bulk engine creates lightweight VISITOR identities in auth-service for
 * attendees nobody has seen before. Those people have an account and a pass -
 * and no profile here, because they are not students or staff. Asking about 600
 * attendees and getting 480 back is the NORMAL result for a mixed sheet, not a
 * partial failure.
 *
 * So the misses come back as data rather than as a 404 or an exception. The
 * caller learns which ids have no profile in one field and can decide what that
 * means for them - gatepass prints a pass without a photo, the scanner shows a
 * name with no face. Neither is a failure worth stopping for.
 *
 * requested is echoed back so a caller can assert found + missing adds up
 * without holding on to the list it sent.
 */
public record ProfileSummaryBatchResponse(
        int requested,
        int foundCount,
        List<ProfileSummaryResponse> found,
        List<Long> missing
) {

    public static ProfileSummaryBatchResponse of(int requested,
                                                 List<ProfileSummaryResponse> found,
                                                 List<Long> missing) {
        return new ProfileSummaryBatchResponse(requested, found.size(), found, missing);
    }
}
