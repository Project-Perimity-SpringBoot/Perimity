package com.perimity.campus.service;

import com.perimity.campus.entity.CampusConfig;
import com.perimity.campus.entity.enums.ConfigValueType;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * The policy settings every new campus starts with, and the rules for what
 * counts as a valid value.
 *
 * THESE ARE THE SIX KEYS FROM THE SRS, spelled the way the SRS spells them.
 * The previous set was invented independently and matched nothing: seven keys
 * in dot notation, none of the documented names, and - the one that mattered -
 * repeat.entry.allowed as a BOOLEAN where the design calls for
 * repeat_entry_result as GREEN or AMBER.
 *
 * That difference is not cosmetic. Guard Service reads this key to decide what
 * a second scan on the same day shows. A boolean can say allowed or not
 * allowed; it cannot say AMBER, which is the entire point of the setting. The
 * Team Guide is blunt about it: "repeat_entry_result unblocks Palash's
 * scanner. Ship it early." It was never shipped, and the scanner work is due.
 *
 * Renaming settings after a campus exists is a migration, so this is a Day 12
 * fix rather than a Day 20 one - the cost only goes up.
 *
 * Still a key-value store, deliberately. A campus may add keys nobody here
 * anticipated, and those are stored as given. Only KNOWN keys are validated,
 * because only known keys have a documented meaning to validate against.
 */
final class CampusConfigDefaults {

    private CampusConfigDefaults() { }

    /**
     * @param allowed  non-empty only for a key whose value is one of a fixed
     *                 set. Stored as STRING - adding an ENUM to ConfigValueType
     *                 would need a schema change and would still not carry
     *                 WHICH values are legal, which is the part that matters.
     * @param min,max  inclusive bounds for INTEGER keys. FR-CFG-4 asks for a
     *                 value to be checked against "its declared type and
     *                 permitted range", and a range needs somewhere to live.
     */
    record Default(String key, String value, ConfigValueType type, String description,
                   Set<String> allowed, Long min, Long max) {

        static Default bool(String key, String value, String description) {
            return new Default(key, value, ConfigValueType.BOOLEAN, description, Set.of(), null, null);
        }

        static Default integer(String key, String value, long min, long max, String description) {
            return new Default(key, value, ConfigValueType.INTEGER, description, Set.of(), min, max);
        }

        static Default choice(String key, String value, Set<String> allowed, String description) {
            return new Default(key, value, ConfigValueType.STRING, description, allowed, null, null);
        }
    }

    static final List<Default> ALL = List.of(

            Default.bool("visitor_approval_required", "true",
                    "Whether a verified visitor request needs host approval before a pass is issued"),

            // The one guard-service is waiting for. GREEN or AMBER - never a
            // boolean. The entry is logged either way; this only decides the
            // colour the guard sees on a repeat scan.
            Default.choice("repeat_entry_result", "AMBER", Set.of("GREEN", "AMBER"),
                    "Result shown when a holder is scanned a second time on the same day"),

            Default.integer("daily_pass_validity_days", "365", 1, 3650,
                    "Validity window of a student daily pass"),

            Default.integer("max_visitor_duration_days", "7", 1, 365,
                    "Maximum length of a single visitor pass"),

            Default.integer("otp_expiry_minutes", "10", 1, 60,
                    "OTP validity window"),

            Default.bool("photo_required_for_pass", "true",
                    "Whether a pass may be issued without a holder photo")
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

    static Optional<Default> known(String key) {
        return ALL.stream().filter(d -> d.key().equals(key)).findFirst();
    }

    /**
     * FR-CFG-4. Throws with a message the admin can act on, or returns quietly.
     *
     * The messages name the legal values on purpose. This is a Campus Admin
     * editing their own campus's settings, not an anonymous caller - there is
     * nothing to protect by being vague, and "invalid value" would just send
     * them to ask someone.
     */
    static void validate(String key, String value) {
        Optional<Default> maybe = known(key);
        if (maybe.isEmpty()) {
            return;   // a campus-invented key. Stored as given.
        }
        Default d = maybe.get();

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Setting \"" + key + "\" cannot be empty.");
        }
        String v = value.trim();

        switch (d.type()) {
            case BOOLEAN -> {
                if (!v.equalsIgnoreCase("true") && !v.equalsIgnoreCase("false")) {
                    throw new IllegalArgumentException(
                            "Setting \"" + key + "\" must be true or false, not \"" + v + "\".");
                }
            }
            case INTEGER -> {
                long parsed;
                try {
                    parsed = Long.parseLong(v);
                } catch (NumberFormatException ex) {
                    throw new IllegalArgumentException(
                            "Setting \"" + key + "\" must be a whole number, not \"" + v + "\".");
                }
                if ((d.min() != null && parsed < d.min()) || (d.max() != null && parsed > d.max())) {
                    throw new IllegalArgumentException(
                            "Setting \"" + key + "\" must be between " + d.min()
                                    + " and " + d.max() + ". Given " + parsed + ".");
                }
            }
            default -> {
                if (!d.allowed().isEmpty()
                        && !d.allowed().contains(v.toUpperCase(Locale.ROOT))) {
                    throw new IllegalArgumentException(
                            "Setting \"" + key + "\" must be one of " + d.allowed()
                                    + ", not \"" + v + "\".");
                }
            }
        }
    }
}
