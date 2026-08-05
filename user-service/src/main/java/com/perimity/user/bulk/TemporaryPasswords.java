package com.perimity.user.bulk;

import java.security.SecureRandom;

/**
 * Generates the one-time password an imported student signs in with.
 *
 * ==========================================================================
 * ONE PER STUDENT, NEVER ONE PER BATCH
 * ==========================================================================
 * The obvious shortcut is a single password for the whole intake, printed once
 * and told to everyone. It would mean every student in that batch can sign in
 * as every other student in it until each of them changes theirs - and some
 * never will. A shared credential across two hundred people is not a
 * convenience, it is two hundred accounts with the same key.
 *
 * ==========================================================================
 * SecureRandom, NOT Random
 * ==========================================================================
 * java.util.Random is a linear congruential generator: observe a couple of
 * outputs and you can predict the rest. These values are credentials, briefly,
 * and predicting the next one in a sequence is exactly the attack.
 *
 * ==========================================================================
 * NO AMBIGUOUS CHARACTERS
 * ==========================================================================
 * 0/O and 1/l/I are omitted. A student reads this off a phone screen and types
 * it into a login box; a password that cannot be transcribed reliably generates
 * failed sign-ins that look like the import broke, and after three of them the
 * account locks itself.
 *
 * The alphabet still satisfies auth-service's policy: at least one lowercase,
 * one uppercase and one digit, minimum eight characters.
 */
public final class TemporaryPasswords {

    private TemporaryPasswords() {
    }

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final String LOWER = "abcdefghijkmnopqrstuvwxyz";
    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String DIGITS = "23456789";
    private static final String ALL = LOWER + UPPER + DIGITS;

    /** 12 characters - long enough to be worth generating, short enough to type. */
    private static final int LENGTH = 12;

    public static String generate() {
        StringBuilder sb = new StringBuilder(LENGTH);

        // One of each class up front, so the policy is satisfied by
        // construction rather than by luck and a retry loop.
        sb.append(pick(LOWER));
        sb.append(pick(UPPER));
        sb.append(pick(DIGITS));

        while (sb.length() < LENGTH) {
            sb.append(pick(ALL));
        }

        /*
         * Shuffle, because the first three characters are otherwise always
         * lower-upper-digit. That is a pattern, and a pattern in a credential
         * is a shortcut for anyone guessing at it.
         */
        char[] chars = sb.toString().toCharArray();
        for (int i = chars.length - 1; i > 0; i--) {
            int j = RANDOM.nextInt(i + 1);
            char tmp = chars[i];
            chars[i] = chars[j];
            chars[j] = tmp;
        }
        return new String(chars);
    }

    private static char pick(String alphabet) {
        return alphabet.charAt(RANDOM.nextInt(alphabet.length()));
    }
}
