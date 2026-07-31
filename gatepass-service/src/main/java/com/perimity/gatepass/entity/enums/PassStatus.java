package com.perimity.gatepass.entity.enums;

import java.util.EnumSet;
import java.util.Set;

/**
 * Lifecycle state of a gate pass (SRS v1.1, FR-PASS-1 ... FR-PASS-7).
 *
 * Legal transitions:
 *   PENDING -> ACTIVE
 *   ACTIVE  -> PAUSED -> ACTIVE
 *   ACTIVE  -> EXPIRED
 *   ACTIVE  -> REVOKED
 *   PAUSED  -> REVOKED
 *
 * PAUSED is new in v1.1 and is entered when a sensitive profile field changes
 * and the pass needs re-approval.
 *
 * Only ACTIVE produces a GREEN scan. Every other state is RED at the gate.
 */
public enum PassStatus {

    PENDING,
    ACTIVE,
    PAUSED,
    EXPIRED,
    REVOKED;

    /** True only for ACTIVE. Guard-service turns this into GREEN vs RED. */
    public boolean isScannable() {
        return this == ACTIVE;
    }

    /** Guards the state machine so no service can write an illegal transition. */
    public boolean canTransitionTo(PassStatus target) {
        return allowedNextStates().contains(target);
    }

    public Set<PassStatus> allowedNextStates() {
        return switch (this) {
            case PENDING -> EnumSet.of(ACTIVE, REVOKED);
            case ACTIVE  -> EnumSet.of(PAUSED, EXPIRED, REVOKED);
            case PAUSED  -> EnumSet.of(ACTIVE, REVOKED);
            case EXPIRED, REVOKED -> EnumSet.noneOf(PassStatus.class);
        };
    }
}
