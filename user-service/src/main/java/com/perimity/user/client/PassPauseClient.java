package com.perimity.user.client;

/**
 * Tells gatepass-service to hold every active pass a person holds, because a
 * sensitive profile field just changed (SRS v1.1).
 *
 * WHY THIS IS AN INTERFACE
 * user-service knows a photo, roll number or government id was edited.
 * gatepass-service owns the pass state machine. Neither can do the other's job,
 * and neither may read the other's database - so this is an HTTP call, and an
 * interface so the call can be switched off in local development and stubbed in
 * a test without a running gatepass container.
 *
 * WHY IT MUST NOT THROW
 * The profile edit has already been committed by the time this runs. If
 * gatepass-service is down, the correct outcome is a saved profile and a loud
 * log line, not a 500 that tells the user their edit failed when it did not.
 * Implementations swallow transport failures and report them.
 */
public interface PassPauseClient {

    /**
     * @param holderUserId the auth-service account whose passes should be held
     * @param reason       what changed, shown to whoever re-approves
     * @param changedBy    the authenticated user who made the edit
     * @return true when gatepass-service accepted the request
     */
    boolean pauseAllForHolder(Long holderUserId, String reason, Long changedBy);
}
