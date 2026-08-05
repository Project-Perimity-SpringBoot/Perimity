package com.perimity.gatepass.entity.enums;

/**
 * The category of visit, chosen from a list.
 *
 * Free-text purpose is kept alongside this as optional detail. The category is
 * what a queue can be filtered and counted by; the sentence is what the
 * approver actually reads.
 */
public enum PurposeType {
    MEETING,
    INTERVIEW,
    DELIVERY,
    MAINTENANCE,
    EVENT,
    ACADEMIC,
    PERSONAL,
    OTHER
}
