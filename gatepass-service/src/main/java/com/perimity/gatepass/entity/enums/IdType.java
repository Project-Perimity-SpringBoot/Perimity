package com.perimity.gatepass.entity.enums;

/**
 * The kind of identity document a visitor presents at the gate.
 *
 * OTHER exists because a guard cannot turn someone away for holding a document
 * this list did not anticipate - a foreign national's residence card, a service
 * ID. The number is still recorded; only its type is unlisted.
 */
public enum IdType {
    AADHAAR,
    PAN,
    DRIVING_LICENCE,
    PASSPORT,
    VOTER_ID,
    OTHER
}
