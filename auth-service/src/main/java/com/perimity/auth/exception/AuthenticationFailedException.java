package com.perimity.auth.exception;

/**
 * Any failed authentication attempt. Maps to 401.
 *
 * The message stays GENERIC for a wrong password and for an unknown email -
 * "Invalid email or password" for both. Saying "no such account" turns login
 * into a free account-enumeration tool: submit ten thousand addresses, keep the
 * ones that answer differently.
 *
 * A lockout message may be specific, because by then the attacker already knows
 * the account exists - they just triggered its lockout.
 */
public class AuthenticationFailedException extends RuntimeException {

    public AuthenticationFailedException(String message) {
        super(message);
    }

    public static AuthenticationFailedException invalidCredentials() {
        return new AuthenticationFailedException("Invalid email or password");
    }
}
