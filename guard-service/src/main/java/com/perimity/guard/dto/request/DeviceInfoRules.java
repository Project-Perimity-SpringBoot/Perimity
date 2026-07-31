package com.perimity.guard.dto.request;

import java.util.Map;

/**
 * Shared bounds for the free-form deviceInfo map.
 *
 * deviceInfo goes straight into the entry log, and the log is evidence. An
 * unbounded map from a scanner app means anyone who controls that app can write
 * arbitrary amounts of arbitrary content into the collection that replaces the
 * paper register.
 *
 * The document field is Map<String, Object>, which no annotation can constrain,
 * so the check lives here and is called from an @AssertTrue on each DTO that
 * accepts one.
 */
final class DeviceInfoRules {

    static final int MAX_ENTRIES = 10;
    static final int MAX_KEY_LENGTH = 40;
    static final int MAX_VALUE_LENGTH = 200;

    private DeviceInfoRules() { }

    static boolean isAcceptable(Map<String, Object> deviceInfo) {
        if (deviceInfo == null) {
            return true;
        }
        if (deviceInfo.size() > MAX_ENTRIES) {
            return false;
        }
        for (Map.Entry<String, Object> e : deviceInfo.entrySet()) {
            String key = e.getKey();
            if (key == null || key.isBlank() || key.length() > MAX_KEY_LENGTH
                    || containsControlCharacters(key)) {
                return false;
            }
            Object value = e.getValue();
            if (value == null) {
                continue;
            }
            // Only flat values. A nested map or list is a way to smuggle bulk
            // content into a log line.
            if (value instanceof Map || value instanceof Iterable) {
                return false;
            }
            String text = String.valueOf(value);
            if (text.length() > MAX_VALUE_LENGTH || containsControlCharacters(text)) {
                return false;
            }
        }
        return true;
    }

    /** Newlines and tabs in a logged value are how a log line gets forged. */
    private static boolean containsControlCharacters(String value) {
        return value.chars().anyMatch(c -> c == '\n' || c == '\r' || c == '\t' || c < 0x20);
    }
}
