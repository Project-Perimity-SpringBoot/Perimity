package com.perimity.auth.entity.enums;

/**
 * Why an OTP was issued. Storing the purpose stops an OTP sent for one flow
 * being replayed in another - a login code must not unlock a password reset.
 */
public enum OtpPurpose {
    LOGIN,
    REGISTRATION,
    VISITOR_VERIFICATION,
    PASS_RETRIEVAL,
    PASSWORD_RESET
}
