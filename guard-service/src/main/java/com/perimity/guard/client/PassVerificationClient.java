package com.perimity.guard.client;

/**
 * Resolves a scanned token into a pass.
 *
 * An interface with a stub behind it, on purpose. Real verification needs
 * qr-service to decrypt the token and gatepass-service to report the pass's
 * current status, and neither call exists until Day 8. Naming the seam now
 * means Day 8 is a one-class swap rather than surgery on ScanService.
 */
public interface PassVerificationClient {

    PassVerification verify(String token);
}
