package com.perimity.gatepass.entity.enums;

/**
 * Who the visitor is, which is not the same as why they are here.
 *
 * A parent and a vendor may both be visiting "for a meeting"; a guard reading
 * a pass at the gate wants to know which one is standing in front of them.
 */
public enum VisitorType {
    GUEST,
    PARENT,
    VENDOR,
    CONTRACTOR,
    ALUMNI,
    CANDIDATE,
    OTHER
}
