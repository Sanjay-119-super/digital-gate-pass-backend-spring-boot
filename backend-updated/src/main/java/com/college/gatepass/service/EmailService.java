package com.college.gatepass.service;

import com.college.gatepass.entity.GatePass;
import com.college.gatepass.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Sends HTML emails to students when their gate pass is approved, rejected, or expired.
 *
 * <p>All methods are annotated with {@code @Async} so they run on a separate thread
 * ({@code appTaskExecutor} defined in {@code AsyncConfig}). This means the HTTP request
 * that triggered the approval/rejection returns immediately — the student's email is
 * sent in the background without making the warden wait.
 *
 * <p>If the mail server is not configured (e.g. in local dev), failures are simply
 * logged as warnings rather than crashing the server.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    /** Display name shown in the "From" field of outgoing emails. */
    @Value("${app.mail.from-name:Gate Pass System}")
    private String fromName;

    /** Email address shown in the "From" field. */
    @Value("${app.mail.from-address:no-reply@gatepass.college.edu}")
    private String fromAddress;

    /** Public base URL used to build the "View Pass" button link in emails. */
    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    /** Formats timestamps in a human-readable way for email bodies. */
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a")
                             .withZone(ZoneId.of("Asia/Kolkata"));

    /**
     * Sends an approval email to the student with their gate pass details.
     * The email includes the leave time, return deadline, and a link to view
     * or download the QR code.
     *
     * @param pass the approved gate pass entity (must have student email and qrToken set)
     */
    @Async("appTaskExecutor")
    public void sendApprovalEmail(GatePass pass) {
        if (pass.getStudent() == null || pass.getStudent().getEmail() == null) return;

        String to          = pass.getStudent().getEmail();
        String studentName = pass.getStudent().getFullName();
        String subject     = "✅ Gate Pass Approved — " + pass.getPassType();

        String qrLink = baseUrl + "/api/passes/" + pass.getId() + "/qr";

        String html = """
                <!DOCTYPE html>
                <html>
                <body style="font-family: Arial, sans-serif; background:#f4f4f4; padding:20px;">
                  <div style="max-width:600px; margin:auto; background:#fff; border-radius:8px;
                              box-shadow:0 2px 8px rgba(0,0,0,0.1); padding:32px;">
                    <h2 style="color:#2e7d32;">✅ Your Gate Pass Has Been Approved</h2>
                    <p>Hi <strong>%s</strong>,</p>
                    <p>Your gate pass request has been <strong style="color:#2e7d32;">approved</strong>
                       by your warden. Here are the details:</p>
                    <table style="width:100%%;border-collapse:collapse;margin:16px 0;">
                      <tr><td style="padding:8px;background:#f9f9f9;font-weight:bold;">Pass Type</td>
                          <td style="padding:8px;">%s</td></tr>
                      <tr><td style="padding:8px;background:#f9f9f9;font-weight:bold;">Destination</td>
                          <td style="padding:8px;">%s</td></tr>
                      <tr><td style="padding:8px;background:#f9f9f9;font-weight:bold;">Leave At</td>
                          <td style="padding:8px;">%s</td></tr>
                      <tr><td style="padding:8px;background:#f9f9f9;font-weight:bold;">Return By</td>
                          <td style="padding:8px;color:#c62828;font-weight:bold;">%s</td></tr>
                      %s
                    </table>
                    <p>Show the QR code at the gate when leaving and returning:</p>
                    <a href="%s" style="display:inline-block;padding:12px 24px;background:#1976d2;
                       color:#fff;text-decoration:none;border-radius:4px;font-weight:bold;">
                       📱 View / Download QR Code
                    </a>
                    <p style="margin-top:24px;color:#888;font-size:12px;">
                      Please make sure to return before the deadline. Late returns will expire your pass.
                    </p>
                  </div>
                </body>
                </html>
                """.formatted(
                studentName,
                pass.getPassType(),
                pass.getDestination(),
                FORMATTER.format(pass.getLeaveAt()),
                FORMATTER.format(pass.getReturnBy()),
                pass.getDecisionNote() != null && !pass.getDecisionNote().isBlank()
                        ? "<tr><td style=\"padding:8px;background:#f9f9f9;font-weight:bold;\">Warden Note</td>"
                          + "<td style=\"padding:8px;\">" + pass.getDecisionNote() + "</td></tr>"
                        : "",
                qrLink
        );

        send(to, subject, html);
    }

    /**
     * Sends a rejection email to the student explaining why their request was denied.
     *
     * @param pass the rejected gate pass entity (must have student email and decisionNote set)
     */
    @Async("appTaskExecutor")
    public void sendRejectionEmail(GatePass pass) {
        if (pass.getStudent() == null || pass.getStudent().getEmail() == null) return;

        String to          = pass.getStudent().getEmail();
        String studentName = pass.getStudent().getFullName();
        String subject     = "Gate Pass Rejected — " + pass.getPassType();
        String reason      = pass.getDecisionNote() != null ? pass.getDecisionNote() : "No reason provided.";

        String html = """
                <!DOCTYPE html>
                <html>
                <body style="font-family: Arial, sans-serif; background:#f4f4f4; padding:20px;">
                  <div style="max-width:600px; margin:auto; background:#fff; border-radius:8px;
                              box-shadow:0 2px 8px rgba(0,0,0,0.1); padding:32px;">
                    <h2 style="color:#c62828;"> Your Gate Pass Has Been Rejected</h2>
                    <p>Hi <strong>%s</strong>,</p>
                    <p>We are sorry, your gate pass request for
                       <strong>%s</strong> to <strong>%s</strong> has been
                       <strong style="color:#c62828;">rejected</strong>.</p>
                    <div style="background:#ffebee;border-left:4px solid #c62828;padding:12px;
                                border-radius:4px;margin:16px 0;">
                      <strong>Reason given by warden:</strong><br/>%s
                    </div>
                    <p>You may submit a new request with updated details if needed.</p>
                    <p style="margin-top:24px;color:#888;font-size:12px;">
                      If you believe this decision is wrong, please contact your hostel warden directly.
                    </p>
                  </div>
                </body>
                </html>
                """.formatted(studentName, pass.getPassType(), pass.getDestination(), reason);

        send(to, subject, html);
    }

    /**
     * Sends an expiry warning email to a student whose approved pass is about to expire.
     * This is called by the {@code PassExpiryScheduler} before marking a pass as EXPIRED.
     *
     * @param pass the gate pass that is about to expire
     */
    @Async("appTaskExecutor")
    public void sendExpiryWarningEmail(GatePass pass) {
        if (pass.getStudent() == null || pass.getStudent().getEmail() == null) return;

        String to          = pass.getStudent().getEmail();
        String studentName = pass.getStudent().getFullName();
        String subject     = "Gate Pass Expired — Please Return to Campus";

        String html = """
                <!DOCTYPE html>
                <html>
                <body style="font-family: Arial, sans-serif; background:#f4f4f4; padding:20px;">
                  <div style="max-width:600px; margin:auto; background:#fff; border-radius:8px;
                              box-shadow:0 2px 8px rgba(0,0,0,0.1); padding:32px;">
                    <h2 style="color:#e65100;">⚠️ Your Gate Pass Has Expired</h2>
                    <p>Hi <strong>%s</strong>,</p>
                    <p>Your gate pass (Pass ID: <strong>%d</strong>, Type: <strong>%s</strong>)
                       was due back by <strong style="color:#c62828;">%s</strong>.</p>
                    <p>Your pass is now <strong>EXPIRED</strong>. Please return to campus immediately
                       and report to the hostel warden.</p>
                    <p style="margin-top:24px;color:#888;font-size:12px;">
                      This is an automated message. Please do not reply to this email.
                    </p>
                  </div>
                </body>
                </html>
                """.formatted(
                studentName,
                pass.getId(),
                pass.getPassType(),
                FORMATTER.format(pass.getReturnBy())
        );

        send(to, subject, html);
    }

    // ── Private helper ───────────────────────────────────────────────────────

    /**
     * Sends a single HTML email.
     * Failures are caught and logged as warnings so a bad mail config does not
     * crash the main application flow.
     *
     * @param to      the recipient's email address
     * @param subject the email subject line
     * @param html    the full HTML body
     */

    @Value("${app.mail.enabled:true}")   // agar property nahi mili toh default true (prod)
    private boolean mailEnabled;

    private void send(String to, String subject, String html) {

        if (!mailEnabled){
            log.info("[DEV EMAIL] To: {}, Subject: {}", to, subject);
            return;
        }
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, false, "UTF-8");
            helper.setFrom(fromAddress, fromName);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true); // true = send as HTML
            mailSender.send(msg);
            log.info("Email sent to {} — subject: {}", to, subject);
        } catch (MessagingException | java.io.UnsupportedEncodingException e) {
            // Log the failure but do not let it bubble up and ruin the main operation
            log.warn("Failed to send email to {} ({}): {}", to, subject, e.getMessage());
        }
    }

    /*-------New Changes---------*/
    @Async("appTaskExecutor")
    public void sendVerificationEmail(User user, String otp) {
        String to = user.getEmail();
        String subject = "Verify your email - Digital Gate Pass";
        String html = String.format("""
        <!DOCTYPE html>
        <html><body style="font-family: Arial, sans-serif;">
          <h2>Email Verification</h2>
          <p>Your OTP is: <strong>%s</strong></p>
          <p>It expires in 10 minutes.</p>
        </body></html>
        """, otp);
        send(to, subject, html);
    }

    @Async("appTaskExecutor")
    public void sendPasswordResetEmail(String to, String name, String resetLink) {
        String subject = "Password Reset - Digital Gate Pass";
        String html = String.format("""
        <!DOCTYPE html>
        <html><body style="font-family: Arial, sans-serif;">
          <h2>Password Reset</h2>
          <p>Hi %s,</p>
          <p>Click the link below to reset your password (valid 30 minutes):</p>
          <a href="%s">Reset Password</a>
        </body></html>
        """, name, resetLink);
        send(to, subject, html);
    }
}