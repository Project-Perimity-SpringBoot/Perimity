package com.perimity.auth.service;

import com.perimity.auth.entity.enums.OtpPurpose;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * OTP and password-reset email. Per the Team Guide this is the ONE thing
 * auth-service emails - pass, approval, rejection and welcome emails belong to
 * qr-service (Sanjay, Day 9).
 *
 * Two rules that matter more than the HTML:
 *
 * 1. NEVER throw out of here. AuthService.requestOtp and
 *    UserAccountService.requestPasswordReset both return the same generic
 *    response whether or not the address has an account (anti-enumeration).
 *    If a send failure became an exception that reached the controller, the
 *    response would start silently telling the truth about which addresses
 *    exist. A failed send is logged and swallowed - the same "fail open"
 *    posture RateLimiter already uses when Redis is down.
 *
 * 2. NEVER put the plain OTP or reset token anywhere but the email body -
 *    not in a log line once mailEnabled is true, not in the audit trail
 *    (FR-AUD-5, FR-NOT-6).
 *
 * perimity.mail.enabled is the dev switch. Not everyone on the team has Gmail
 * SMTP set up locally - with it false, this logs the plain code/link instead
 * of sending, clearly marked "dev mode", so nobody is blocked on an App
 * Password to keep working. It must be true before anything resembling a real
 * environment, and MAIL_ENABLED belongs in .env, never hardcoded here.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final boolean mailEnabled;
    private final String fromName;
    private final String fromAddress;

    public EmailService(JavaMailSender mailSender,
                        @Value("${perimity.mail.enabled:true}") boolean mailEnabled,
                        @Value("${perimity.mail.from-name:Perimity}") String fromName,
                        @Value("${spring.mail.username:}") String fromAddress) {
        this.mailSender = mailSender;
        this.mailEnabled = mailEnabled;
        this.fromName = fromName;
        this.fromAddress = fromAddress;
    }

    /** OTP for login, registration, visitor verification, pass retrieval or password reset. */
    /**
     * Fire-and-forget. The OTP row is already committed before this runs, so
     * the code is valid whether or not the mail lands, and every failure path
     * below already logs rather than throws - nothing here is worth making a
     * visitor wait five seconds for.
     */
    @Async("mailExecutor")
    public void sendOtp(String toEmail, String code, OtpPurpose purpose, int expiryMinutes) {
        if (!mailEnabled) {
            log.warn("MAIL DISABLED (dev mode) - OTP for {} ({}) is {}", toEmail, purpose, code);
            return;
        }

        String subject = "Your Perimity verification code";
        String html = otpTemplate(subject, introFor(purpose), code, expiryMinutes);

        try {
            deliver(toEmail, subject, html);
            log.info("OTP email dispatched to {} for purpose {}", toEmail, purpose);
        } catch (MessagingException | UnsupportedEncodingException | MailException ex) {
            // Swallowed on purpose - see the class comment. The OTP row is
            // already saved; the caller still gets the same generic response
            // whether this send worked or not.
            log.error("Could not send OTP email to {}: {}", toEmail, ex.getMessage());
        }
    }

    /** Forgot-password link (FR-SESS-5). Off the request thread, as above. */
    @Async("mailExecutor")
    public void sendPasswordResetLink(String toEmail, String resetLink, int expiryMinutes) {
        if (!mailEnabled) {
            log.warn("MAIL DISABLED (dev mode) - password reset link for {} is {}",
                    toEmail, resetLink);
            return;
        }

        String subject = "Reset your Perimity password";
        String html = resetTemplate(subject, resetLink, expiryMinutes);

        try {
            deliver(toEmail, subject, html);
            log.info("Password reset email dispatched to {}", toEmail);
        } catch (MessagingException | UnsupportedEncodingException | MailException ex) {
            log.error("Could not send password reset email to {}: {}", toEmail, ex.getMessage());
        }
    }

    private void deliver(String toEmail, String subject, String html)
            throws MessagingException, UnsupportedEncodingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
        helper.setTo(toEmail);
        helper.setSubject(subject);
        helper.setText(html, true);
        if (fromAddress != null && !fromAddress.isBlank()) {
            helper.setFrom(fromAddress, fromName);
        }
        mailSender.send(message);
    }

    private String introFor(OtpPurpose purpose) {
        return switch (purpose) {
            case LOGIN -> "Use this code to sign in.";
            case REGISTRATION -> "Use this code to verify your registration.";
            case VISITOR_VERIFICATION -> "Use this code to verify your visit request.";
            case PASS_RETRIEVAL -> "Use this code to view your gate pass again.";
            case PASSWORD_RESET -> "Use this code to reset your password.";
        };
    }

    private String otpTemplate(String title, String intro, String code, int expiryMinutes) {
        return """
                <div style="font-family:Arial,sans-serif;max-width:480px;margin:auto">
                  <h2 style="margin-bottom:4px">%s</h2>
                  <p style="color:#444">%s</p>
                  <p style="font-size:32px;font-weight:bold;letter-spacing:6px;
                            background:#f4f4f4;padding:16px 24px;border-radius:8px;
                            display:inline-block">%s</p>
                  <p style="color:#666;font-size:13px">
                    This code expires in %d minutes and can only be used once.
                    If you did not request it, you can ignore this email.
                  </p>
                </div>
                """.formatted(title, intro, code, expiryMinutes);
    }

    /**
     * Sign-in details for a student created by a bulk import.
     *
     * ==================================================================
     *  THIS EMAIL CONTAINS A PASSWORD, WHICH IS NORMALLY UNTHINKABLE
     * ==================================================================
     * It is defensible here only because of what that password is: generated
     * per student, never reused, and burned on first use - the account carries
     * mustChangePassword, so it buys exactly one sign-in and then must be
     * replaced.
     *
     * The alternative is worse. Without it, an imported student has an account
     * they cannot reach and no way to discover they have one. A reset-link
     * flow would be better still and is the right eventual answer; it is not
     * this one because a link tied to an account the student has never heard of
     * reads like phishing.
     *
     * The same rule as every other credential here applies: the plain value
     * goes in the email body and NOWHERE else. Not a log line, not the audit
     * trail. The dev-mode branch below is the one exception, and it exists only
     * because mailEnabled=false means nothing was sent to read.
     */
    public void sendStudentWelcome(String toEmail, String name,
                                   String temporaryPassword, String loginUrl) {
        if (!mailEnabled) {
            log.warn("MAIL DISABLED (dev mode) - welcome for {} with temporary password {}",
                    toEmail, temporaryPassword);
            return;
        }

        String subject = "Your Perimity account is ready";
        String html = welcomeTemplate(subject, name, toEmail, temporaryPassword, loginUrl);

        try {
            deliver(toEmail, subject, html);
            // The address, never the password.
            log.info("Welcome email dispatched to {}", toEmail);
        } catch (MessagingException | UnsupportedEncodingException | MailException ex) {
            /*
             * Swallowed, like the others. The account is already created and
             * correct; only the notification failed. Throwing would fail a
             * whole import because one mailbox bounced.
             *
             * The cost is a student who does not know they have an account, and
             * the answer to that is the log line above plus a password reset -
             * not a failed intake.
             */
            log.error("Could not send welcome email to {}: {}", toEmail, ex.getMessage());
        }
    }

    private String welcomeTemplate(String title, String name, String email,
                                   String temporaryPassword, String loginUrl) {
        return """
                <div style="font-family:Arial,sans-serif;max-width:480px;margin:auto">
                  <h2 style="margin-bottom:4px">%s</h2>
                  <p style="color:#444">
                    Hello %s, your campus account has been created.
                  </p>
                  <table style="border-collapse:collapse;margin:16px 0">
                    <tr><td style="padding:4px 12px 4px 0;color:#666">Email</td>
                        <td style="padding:4px 0"><strong>%s</strong></td></tr>
                    <tr><td style="padding:4px 12px 4px 0;color:#666">Temporary password</td>
                        <td style="padding:4px 0">
                          <code style="background:#f3f4f6;padding:3px 8px;border-radius:4px">%s</code>
                        </td></tr>
                  </table>
                  <p><a href="%s" style="background:#2563eb;color:#fff;padding:10px 20px;
                            border-radius:6px;text-decoration:none;display:inline-block">
                            Sign in</a></p>
                  <p style="color:#444">
                    You will be asked to choose your own password the first time you
                    sign in. After that, add your passport photo from your profile -
                    your gate pass cannot be issued until it is there.
                  </p>
                  <p style="color:#666;font-size:13px">
                    If you were not expecting this, please tell the person who runs
                    your course rather than replying to this message.
                  </p>
                </div>
                """.formatted(title, name, email, temporaryPassword, loginUrl);
    }

    private String resetTemplate(String title, String resetLink, int expiryMinutes) {
        return """
                <div style="font-family:Arial,sans-serif;max-width:480px;margin:auto">
                  <h2 style="margin-bottom:4px">%s</h2>
                  <p style="color:#444">
                    Click the button below to choose a new password. The link
                    expires in %d minutes and can only be used once.
                  </p>
                  <p><a href="%s" style="background:#2563eb;color:#fff;padding:10px 20px;
                            border-radius:6px;text-decoration:none;display:inline-block">
                            Reset password</a></p>
                  <p style="color:#666;font-size:13px">
                    If you did not request this, you can ignore this email -
                    your password will not change.
                  </p>
                </div>
                """.formatted(title, expiryMinutes, resetLink);
    }
}
