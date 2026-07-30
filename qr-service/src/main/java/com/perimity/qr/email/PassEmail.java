package com.perimity.qr.email;

/**
 * One pass email, fully assembled. Nothing here is composed by qr-service.
 *
 * subject and body arrive on the queue as QrGenerationJob.emailSubject and
 * emailGreeting, written by gatepass-service. That split is Tushar's and it is
 * the right one: gatepass knows whether this is a daily or an event pass and
 * what the event is called, while this service knows how to render a PDF and
 * talk to a mail server. Changing the wording then never touches the service
 * that sends it - and it keeps every campus-specific string out of qr-service,
 * which is what lets the Day 21 branding guard-rail job stay green.
 *
 * pdf is passed as bytes rather than a storage key so EmailSender never needs
 * StorageService. It makes the sender trivially unit testable and keeps one
 * reason to change per class.
 */
public record PassEmail(String to, String subject, String body, byte[] pdf) {
}
