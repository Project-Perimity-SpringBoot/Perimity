package com.perimity.user.entity.enums;

/**
 * user-service holds two kinds of profile, kept as separate tables because they
 * share almost no fields. This enum exists for the rare query that needs to ask
 * "what kind of person is this" without joining both tables.
 */
public enum ProfileType {
    STUDENT,
    FACULTY
}
