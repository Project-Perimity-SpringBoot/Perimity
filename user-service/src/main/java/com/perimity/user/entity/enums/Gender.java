package com.perimity.user.entity.enums;

/**
 * Recorded because an identity document shows it and a guard may need to match
 * a person against a pass. Nothing in the access decision reads it.
 *
 * PREFER_NOT_TO_SAY is a real option rather than a gap, and OTHER exists so the
 * list does not force anyone into a wrong answer. A campus-agnostic product
 * cannot assume which categories an institution or a country uses, so the set
 * stays small and neutral rather than trying to be exhaustive.
 *
 * Stored as a STRING column, never ordinal. An ordinal would silently change
 * meaning the day somebody inserts a value into the middle of this enum.
 */
public enum Gender {
    MALE,
    FEMALE,
    OTHER,
    PREFER_NOT_TO_SAY
}
