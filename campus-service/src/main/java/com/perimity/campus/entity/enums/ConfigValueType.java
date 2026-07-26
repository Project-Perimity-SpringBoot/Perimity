package com.perimity.campus.entity.enums;

/**
 * campus_config is a key-value store so each campus can carry its own rules
 * without a schema change. The value is always stored as text; this enum tells
 * a reader how to interpret that text, so "true" is read as a boolean and "5"
 * as a number, consistently across every service.
 */
public enum ConfigValueType {
    STRING,
    BOOLEAN,
    INTEGER,
    JSON
}
