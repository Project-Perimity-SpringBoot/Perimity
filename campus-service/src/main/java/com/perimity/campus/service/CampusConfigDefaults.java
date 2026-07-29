package com.perimity.campus.service;

import com.perimity.campus.entity.CampusConfig;
import com.perimity.campus.entity.enums.ConfigValueType;
import java.util.List;

/**
 * The policy settings every new campus starts with.
 *
 * Why defaults exist at all: a campus with no config is a campus where five
 * other services each fall back to their own hard-coded guess, and those
 * guesses drift apart. Seeding at creation means every setting has one owner
 * and one place to read it.
 *
 * These are DEFAULTS, not a fixed list. A Campus Admin edits any of them, and
 * a campus may add keys nobody here anticipated - that is the whole point of a
 * key-value store rather than columns.
 *
 * Deliberately campus-agnostic. No institution's habits are baked in.
 */
final class CampusConfigDefaults {

    private CampusConfigDefaults() { }

    record Default(String key, String value, ConfigValueType type, String description) { }

    static final List<Default> ALL = List.of(
            new Default("approval.required", "true", ConfigValueType.BOOLEAN,
                    "Whether a visitor request needs host approval before a pass is issued"),
            new Default("visitor.self.registration", "true", ConfigValueType.BOOLEAN,
                    "Whether visitors may register themselves rather than being added by staff"),
            new Default("pass.default.validity.days", "1", ConfigValueType.INTEGER,
                    "How many days a visitor pass lasts when no end date is given"),
            new Default("repeat.entry.allowed", "true", ConfigValueType.BOOLEAN,
                    "Whether the same pass may be scanned more than once in a day"),
            new Default("bulk.upload.max.rows", "1000", ConfigValueType.INTEGER,
                    "Largest spreadsheet a single bulk upload will accept"),
            new Default("event.attendance.export", "true", ConfigValueType.BOOLEAN,
                    "Whether organisers may export an event attendance list"),
            new Default("notification.email.enabled", "true", ConfigValueType.BOOLEAN,
                    "Whether the campus sends pass emails")
    );

    static List<CampusConfig> forCampus(Long campusId) {
        return ALL.stream()
                .map(d -> CampusConfig.builder()
                        .campusId(campusId)
                        .configKey(d.key())
                        .configValue(d.value())
                        .valueType(d.type())
                        .description(d.description())
                        .build())
                .toList();
    }
}
