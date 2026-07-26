package com.perimity.gatepass.entity.enums;

/**
 * What kind of permission a pass grants.
 *
 * DAILY - a standing pass (student / faculty). valid_to is NULL: no end date.
 * EVENT - tied to one event, valid only for that event's date range.
 *
 * One person can hold both at the same time. That is normal and expected.
 */
public enum PassType {
    DAILY,
    EVENT
}
