package com.perimity.gatepass.entity.enums;

/**
 * Stored as a string, like every other enum here - an ordinal would silently
 * remap every existing row the day someone reorders these.
 *
 * PREFER_NOT_TO_SAY is a real option, not padding. A gate pass does not need
 * this field to work, and forcing a choice on someone to get through a gate is
 * not a trade the product should make.
 */
public enum Gender {
    MALE,
    FEMALE,
    OTHER,
    PREFER_NOT_TO_SAY
}
