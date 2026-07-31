package com.perimity.qr.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Renders a token into a QR PNG.
 *
 * The settings below are chosen for the actual scanning conditions on Day 18:
 * a guard's phone camera, a printed or screen-displayed pass, often in poor
 * light at a gate.
 */
@Service
public class QrImageService {

    private static final String IMAGE_FORMAT = "PNG";

    private final int size;
    private final int margin;
    private final ErrorCorrectionLevel errorCorrection;

    public QrImageService(
            @Value("${qr.image.size:512}") int size,
            @Value("${qr.image.margin:2}") int margin,
            @Value("${qr.image.error-correction:M}") String errorCorrection) {

        this.size = size;
        this.margin = margin;
        this.errorCorrection = ErrorCorrectionLevel.valueOf(errorCorrection);
    }

    /**
     * @param token the plain AES token - it goes into the image and nowhere else
     */
    public byte[] render(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Cannot render a QR for a blank token");
        }

        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        /*
         * Level M recovers ~15% damage. Not L, because a printed pass in a
         * pocket picks up creases and a phone screen picks up cracks, and
         * Palash's Day 18 task explicitly includes decoding on a cracked
         * screen. Not Q or H, because higher correction means a denser
         * matrix for the same ~75-character token, and denser modules are
         * harder for a cheap camera to resolve in bad light. M is the point
         * where damage tolerance stops costing more than it buys.
         */
        hints.put(EncodeHintType.ERROR_CORRECTION, errorCorrection);
        /*
         * The quiet zone. The QR spec wants 4 modules; 2 is the practical
         * floor and keeps the image smaller. It is a hint, not a guarantee -
         * the PDF adds real whitespace around the image anyway.
         */
        hints.put(EncodeHintType.MARGIN, margin);
        // The token is URL-safe Base64, so ASCII - but stating it stops ZXing
        // guessing a charset and encoding the same token differently on a
        // machine with a different default.
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");

        try {
            BitMatrix matrix = new QRCodeWriter()
                    .encode(token, BarcodeFormat.QR_CODE, size, size, hints);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, IMAGE_FORMAT, out);
            return out.toByteArray();

        } catch (WriterException ex) {
            throw new IllegalStateException("Could not encode token as a QR code", ex);
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not write QR PNG", ex);
        }
    }
}
