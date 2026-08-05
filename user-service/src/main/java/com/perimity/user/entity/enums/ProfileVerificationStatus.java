package com.perimity.user.entity.enums;

import java.util.EnumSet;
import java.util.Set;

/**
 * Where a student's own profile details are in the verification cycle.
 *
 * ==========================================================================
 * WHY A PROFILE NEEDS VERIFYING AT ALL
 * ==========================================================================
 * A student fills in their own name, date of birth, gender, address and phone
 * numbers. Nobody checked any of it. The gate pass built from that profile is
 * only as trustworthy as the moment somebody looked at the details and said
 * yes - so this records who did that, and when.
 *
 * This is deliberately the SAME shape as Document's verified/verifiedBy/
 * verifiedAt trio, because it is the same idea applied to a different record.
 * The difference is the state machine: a document is uploaded once and then
 * judged, whereas a profile is edited repeatedly and only judged when the
 * student says they are finished.
 *
 * ==========================================================================
 * THE TRANSITIONS, AND WHY EACH ONE EXISTS
 * ==========================================================================
 *   DRAFT     -> SUBMITTED   the student has finished and asked to be checked
 *   SUBMITTED -> VERIFIED    faculty accepted it
 *   SUBMITTED -> REJECTED    faculty refused it, with mandatory remarks
 *   REJECTED  -> SUBMITTED   the student corrected it and asked again
 *   VERIFIED  -> DRAFT       the student edited a verified profile
 *
 * That last one is the important one. A verified profile that can be silently
 * edited afterwards is worse than no verification at all: the record would
 * claim somebody checked details that have since changed. Editing therefore
 * drops it back to DRAFT and the student has to resubmit.
 *
 * SUBMITTED is intentionally NOT editable. Otherwise a student could change
 * the details while faculty are looking at them.
 */
public enum ProfileVerificationStatus {

    /** Being filled in. The only state in which the student may edit. */
    DRAFT,

    /** Handed to faculty. Locked to the student until a decision is made. */
    SUBMITTED,

    /** Faculty accepted it. Editing sends it back to DRAFT. */
    VERIFIED,

    /** Faculty refused it. The remarks say why; the student may correct and resubmit. */
    REJECTED;

    private static final Set<ProfileVerificationStatus> STUDENT_EDITABLE =
            EnumSet.of(DRAFT, REJECTED);

    /**
     * May the student change their details right now?
     *
     * False while SUBMITTED (faculty are reading it) and false while VERIFIED -
     * though an edit to a VERIFIED profile is allowed as an explicit action
     * that returns it to DRAFT, which is a different thing from editing in
     * place. See StudentProfileService.
     */
    public boolean isStudentEditable() {
        return STUDENT_EDITABLE.contains(this);
    }

    /** Only a submitted profile can be accepted or refused. */
    public boolean isAwaitingDecision() {
        return this == SUBMITTED;
    }

    /** Can the student ask for a check from here? */
    public boolean canSubmit() {
        return this == DRAFT || this == REJECTED;
    }
}
