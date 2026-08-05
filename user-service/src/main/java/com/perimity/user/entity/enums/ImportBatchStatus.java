package com.perimity.user.entity.enums;

/**
 * Where a bulk student import has got to.
 *
 * ==========================================================================
 * VALIDATED IS A STOP, NOT A STEP
 * ==========================================================================
 * Parsing and writing are deliberately two requests. A sheet is uploaded,
 * every row is checked, and NOTHING is written until faculty look at the
 * preview and confirm.
 *
 * That pause is not politeness. It is the human act the whole import depends
 * on: rows land VERIFIED, and verifiedBy records the person who confirmed. If
 * upload wrote straight to the database there would be no moment a person took
 * responsibility, and the verification record would name somebody who never
 * saw the data.
 *
 * It also catches the ordinary disaster - the wrong file, last term's sheet,
 * a column somebody renamed - before it becomes two hundred accounts.
 *
 *   VALIDATING -> VALIDATED  every row parsed and checked; nothing written
 *   VALIDATED  -> PROCESSING faculty confirmed; accounts being created
 *   PROCESSING -> COMPLETED  finished, though individual rows may have failed
 *   any        -> FAILED     the batch itself could not proceed
 *
 * COMPLETED does NOT mean every row succeeded. One bad row must never fail the
 * batch - the same rule the visitor bulk engine and the expiry sweep follow -
 * so per-row outcomes are recorded separately and the summary reports both.
 */
public enum ImportBatchStatus {

    /** Reading the sheet. Nothing has been written. */
    VALIDATING,

    /** Checked and waiting for a human. Still nothing written. */
    VALIDATED,

    /** Confirmed. Accounts, profiles, photos and passes are being created. */
    PROCESSING,

    /** Finished. Check the row counts - some rows may have failed. */
    COMPLETED,

    /**
     * The batch could not proceed at all: an unreadable file, no recognisable
     * columns, or a failure that would have made every row wrong. Distinct
     * from COMPLETED-with-failures, which means the pipeline worked.
     */
    FAILED;

    /** Only a validated batch may be confirmed. */
    public boolean isConfirmable() {
        return this == VALIDATED;
    }

    public boolean isFinished() {
        return this == COMPLETED || this == FAILED;
    }
}
