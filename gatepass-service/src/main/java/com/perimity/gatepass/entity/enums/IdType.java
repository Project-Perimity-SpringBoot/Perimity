package com.perimity.gatepass.entity.enums;

/**
 * The identity documents a visitor may present at the gate.
 *
 * Four, deliberately. DRIVING_LICENCE and OTHER were dropped: a licence number
 * has no single national format (each state issues its own), and OTHER meant a
 * number nobody could validate or check against anything at the gate.
 *
 * Each of these four has one published format, which IdDocumentValidator
 * enforces - see it for the per-type rules and why they are checked there
 * rather than as a regex on the field.
 */
public enum IdType {
    AADHAAR,
    PAN,
    PASSPORT,
    VOTER_ID
}
