package com.perimity.user.bulk;

import com.perimity.user.entity.enums.Gender;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Maps what a student picked in the form's gender dropdown to the enum.
 *
 * ==========================================================================
 * THE FORM SHOWS FRIENDLY LABELS, NOT ENUM NAMES
 * ==========================================================================
 * A dropdown reading MALE / FEMALE / OTHER / PREFER_NOT_TO_SAY parses without
 * any of this, and looks like a database leaked onto a form students are asked
 * to fill in on their phone. So the form says "Male", "Prefer not to say", and
 * this translates.
 *
 * ==========================================================================
 * AN UNKNOWN LABEL FAILS THE ROW. IT DOES NOT GUESS.
 * ==========================================================================
 * The obvious shortcut is to default anything unrecognised to
 * PREFER_NOT_TO_SAY - it is the neutral option, so it feels harmless.
 *
 * It is not. It would mean a form question somebody edited - renaming an option,
 * translating it, adding "Male " with a trailing space - silently rewrites what
 * students said about themselves, across a whole intake, with nothing to
 * indicate it happened. A row that fails loudly gets fixed in a minute. Two
 * hundred rows quietly recorded as "prefer not to say" get noticed never.
 *
 * Adding a label here is a one-line change. That is the intended way to handle
 * a form that says something new.
 */
public final class GenderLabels {

    private GenderLabels() {
    }

    private static final Map<String, Gender> BY_LABEL = Map.ofEntries(
            Map.entry("male", Gender.MALE),
            Map.entry("m", Gender.MALE),
            Map.entry("man", Gender.MALE),

            Map.entry("female", Gender.FEMALE),
            Map.entry("f", Gender.FEMALE),
            Map.entry("woman", Gender.FEMALE),

            Map.entry("other", Gender.OTHER),
            Map.entry("non-binary", Gender.OTHER),
            Map.entry("non binary", Gender.OTHER),
            Map.entry("nonbinary", Gender.OTHER),

            Map.entry("prefer not to say", Gender.PREFER_NOT_TO_SAY),
            Map.entry("prefer not to disclose", Gender.PREFER_NOT_TO_SAY),
            Map.entry("rather not say", Gender.PREFER_NOT_TO_SAY),
            Map.entry("not specified", Gender.PREFER_NOT_TO_SAY),

            // The enum names themselves, in case a form was built from this
            // list literally. Cheap to accept, and refusing them would be
            // pedantic about a sheet that is unambiguous.
            Map.entry("male_", Gender.MALE),
            Map.entry("prefer_not_to_say", Gender.PREFER_NOT_TO_SAY));

    /** Empty when the label is unrecognised. The caller fails the row. */
    public static Optional<Gender> parse(String label) {
        if (label == null || label.isBlank()) {
            return Optional.empty();
        }
        String key = label.toLowerCase(Locale.ROOT)
                .replace('_', ' ')
                .replaceAll("\\s+", " ")
                .trim();

        Gender direct = BY_LABEL.get(key);
        if (direct != null) {
            return Optional.of(direct);
        }
        // A last try against the enum name, so PREFER_NOT_TO_SAY works whether
        // or not somebody thought to add it above.
        for (Gender gender : Gender.values()) {
            if (gender.name().equalsIgnoreCase(label.trim().replace(' ', '_'))) {
                return Optional.of(gender);
            }
        }
        return Optional.empty();
    }

    /** For the error message, so faculty can see what the form should offer. */
    public static String acceptedLabels() {
        return "Male, Female, Other, Prefer not to say";
    }
}
