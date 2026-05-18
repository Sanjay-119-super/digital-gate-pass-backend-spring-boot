package com.college.gatepass.service;

import com.college.gatepass.exception.ApiException;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.EnumMap;
import java.util.Map;

/**
 * Generates QR code images for approved gate passes.
 *
 * <p>The QR code does NOT simply encode the pass ID — that would let anyone forge
 * a QR code by guessing an ID. Instead it encodes a URL that contains:
 * <ul>
 *   <li>the pass's opaque {@code qrToken} (a random UUID — already unguessable)</li>
 *   <li>an expiry timestamp in Unix epoch seconds</li>
 *   <li>an HMAC-SHA256 signature over the token + expiry, computed with
 *       the server-side secret ({@code app.pass.qr-hmac-secret})</li>
 * </ul>
 *
 * <p>Example encoded URL:
 * <pre>
 *   https://gatepass.college.edu/api/verify/a3f7b1...?exp=1722500000&sig=c4d9e2...
 * </pre>
 *
 * <p>When the security guard scans the code, the server re-computes the HMAC and
 * checks it against {@code sig}. If they match, the token has not been tampered with.
 * The expiry timestamp provides an extra safety net: even a genuine QR code cannot
 * be reused after the pass's {@code returnBy} time.
 */
@Slf4j
@Service
public class QrCodeService {

    /** The public base URL used inside every QR code link. */
    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    /** The HMAC secret used to sign QR code URLs — must be kept private. */
    @Value("${app.pass.qr-hmac-secret}")
    private String hmacSecret;

    /** Default QR code image width in pixels. */
    private static final int DEFAULT_WIDTH  = 300;

    /** Default QR code image height in pixels. */
    private static final int DEFAULT_HEIGHT = 300;

    /**
     * Generates the signed scan URL that will be embedded inside the QR code image.
     *
     * <p>Format: {@code {baseUrl}/api/verify/{qrToken}?exp={epochSeconds}&sig={hmacHex}}
     *
     * @param qrToken      the pass's unique QR token (random UUID without dashes)
     * @param expiryEpoch  the Unix epoch seconds when this link should stop being valid
     *                     (typically the pass's {@code returnBy} time)
     * @return a fully-formed, HMAC-signed URL string
     */
    public String buildSignedUrl(String qrToken, long expiryEpoch) {
        String payload = qrToken + ":" + expiryEpoch;
        String signature = hmacSha256Hex(payload, hmacSecret);
        return baseUrl + "/api/verify/" + qrToken
                + "?exp=" + expiryEpoch
                + "&sig=" + signature;
    }

    /**
     * Verifies that a QR URL's HMAC signature is correct and that it has not expired.
     *
     * @param qrToken     the token extracted from the URL path
     * @param expiryEpoch the expiry timestamp extracted from the URL query string
     * @param signature   the HMAC signature extracted from the URL query string
     * @return true if the signature matches and the link has not yet expired
     */
    public boolean verifySignedUrl(String qrToken, long expiryEpoch, String signature) {
        // Check expiry first — quick check before doing crypto work
        if (Instant.now().getEpochSecond() > expiryEpoch) {
            log.warn("QR URL for token {} has expired (exp={})", qrToken, expiryEpoch);
            return false;
        }
        String expected = hmacSha256Hex(qrToken + ":" + expiryEpoch, hmacSecret);
        return expected.equals(signature);
    }

    /**
     * Generates a QR code PNG image that encodes the signed scan URL for a pass.
     *
     * @param qrToken     the pass's unique QR token
     * @param expiryEpoch the Unix epoch seconds for the link expiry
     * @return the PNG image as a raw byte array (ready to send as HTTP response or embed in email)
     * @throws ApiException 500 if the QR generation fails for any reason
     */
    public byte[] generateQrPng(String qrToken, long expiryEpoch) {
        return generateQrPng(qrToken, expiryEpoch, DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }

    /**
     * Generates a QR code PNG image at a custom size.
     *
     * @param qrToken     the pass's unique QR token
     * @param expiryEpoch the Unix epoch seconds for the link expiry
     * @param width       image width in pixels
     * @param height      image height in pixels
     * @return the PNG image as a raw byte array
     * @throws ApiException 500 if ZXing fails to encode or the image cannot be written
     */
    public byte[] generateQrPng(String qrToken, long expiryEpoch, int width, int height) {
        String content = buildSignedUrl(qrToken, expiryEpoch);
        log.debug("Generating QR code for token {} — URL length {} chars", qrToken, content.length());

        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        // Error correction level M — recovers up to 15% data loss (good for phone screens)
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        // Small quiet zone (white border) — 1 module on each side
        hints.put(EncodeHintType.MARGIN, 1);

        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(content, BarcodeFormat.QR_CODE, width, height, hints);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return out.toByteArray();
        } catch (WriterException | IOException e) {
            log.error("Failed to generate QR code for token {}", qrToken, e);
            throw ApiException.badRequest("Could not generate QR code: " + e.getMessage());
        }
    }

    /**
     * Generates the QR code PNG and returns it as a Base64-encoded string
     * so it can be embedded in a JSON response or an HTML email directly.
     *
     * @param qrToken     the pass's unique QR token
     * @param expiryEpoch the Unix epoch seconds for the link expiry
     * @return a Base64-encoded string of the PNG bytes (without the data: URI prefix)
     */
    public String generateQrBase64(String qrToken, long expiryEpoch) {
        byte[] png = generateQrPng(qrToken, expiryEpoch);
        return Base64.getEncoder().encodeToString(png);
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    /**
     * Computes the HMAC-SHA256 digest of a message using the given secret key.
     * Returns the result as a lowercase hexadecimal string.
     *
     * @param message the text to sign
     * @param secret  the signing secret key
     * @return a 64-character lowercase hex HMAC digest
     * @throws IllegalStateException if HMAC-SHA256 is not available (should never happen on Java 17+)
     */
    private String hmacSha256Hex(String message, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("HmacSHA256 not available", e);
        }
    }
}