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

    /**
     * May confirm run on this batch?
     *
     * VALIDATED is the normal case. FAILED is included deliberately, and it is
     * the more important one.
     *
     * A confirm that dies partway - a timeout, a restart, auth-service
     * disappearing after it had already created ninety accounts - leaves the
     * batch FAILED while some of the work landed. If FAILED were terminal, the
     * only options would be re-uploading the sheet, which starts a second batch
     * describing the same students, or editing the database. Both are worse
     * than resuming.
     *
     * Resuming is safe because confirm skips rows that already have an outcome
     * and auth-service matches on email, so a row that got through the first
     * time is recognised rather than duplicated.
     *
     * PROCESSING is NOT confirmable: a batch that is mid-flight has a live
     * caller, and a second confirm would race it.
     */
    public boolean isConfirmable() {
        return this == VALIDATED || this == FAILED;
    }

    public boolean isFinished() {
        return this == COMPLETED || this == FAILED;
    }
}
