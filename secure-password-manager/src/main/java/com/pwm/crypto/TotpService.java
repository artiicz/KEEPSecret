package com.pwm.crypto;

import com.pwm.model.TotpConfig;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

/**
 * TOTP (Time-Based One-Time Password) implementation per RFC 6238.
 *
 * Features:
 * - Secure random secret generation (160 bits)
 * - Replay protection via counter tracking
 * - Configurable time step and digit count
 * - OTPAuth URI generation for QR code display
 */
public class TotpService {

    private static final int SECRET_BYTES = 20; // 160 bits
    private static final String HMAC_SHA1 = "HmacSHA1";

    private final SecureRandom secureRandom;

    public TotpService() {
        this.secureRandom = new SecureRandom();
    }

    /**
     * Generates a new TOTP secret (160 bits, Base32 encoded).
     */
    public String generateSecret() {
        byte[] secret = new byte[SECRET_BYTES];
        secureRandom.nextBytes(secret);
        return base32Encode(secret);
    }

    /**
     * Generates a TOTP code for the current time.
     */
    public String generateCode(TotpConfig config) throws Exception {
        long counter = Instant.now().getEpochSecond() / config.getPeriodSeconds();
        return generateCodeForCounter(config.getSecretBase32(), counter, config.getDigits());
    }

    /**
     * Validates a TOTP code with a ±1 window to account for clock skew.
     * Includes replay protection: rejects codes from already-used counters.
     *
     * @return true if the code is valid and not replayed
     */
    public boolean validateCode(TotpConfig config, String code) throws Exception {
        long currentCounter = Instant.now().getEpochSecond() / config.getPeriodSeconds();

        // Check window: current ± 1
        for (long counter = currentCounter - 1; counter <= currentCounter + 1; counter++) {
            // Replay protection
            if (counter <= config.getLastUsedCounter()) {
                continue;
            }

            String expected = generateCodeForCounter(config.getSecretBase32(), counter, config.getDigits());
            if (constantTimeEquals(expected, code)) {
                config.setLastUsedCounter(counter);
                return true;
            }
        }
        return false;
    }

    /**
     * Generates the OTPAuth URI for QR code generation.
     * Format: otpauth://totp/{issuer}:{account}?secret={secret}&issuer={issuer}&digits={digits}&period={period}
     */
    public String generateOtpAuthUri(TotpConfig config) {
        return String.format("otpauth://totp/%s:%s?secret=%s&issuer=%s&digits=%d&period=%d&algorithm=%s",
                urlEncode(config.getIssuer()),
                urlEncode(config.getAccountName()),
                config.getSecretBase32(),
                urlEncode(config.getIssuer()),
                config.getDigits(),
                config.getPeriodSeconds(),
                config.getAlgorithm());
    }

    // --- Internal Methods ---

    private String generateCodeForCounter(String secretBase32, long counter, int digits) throws Exception {
        byte[] secret = base32Decode(secretBase32);
        byte[] counterBytes = ByteBuffer.allocate(8).putLong(counter).array();

        Mac mac = Mac.getInstance(HMAC_SHA1);
        mac.init(new SecretKeySpec(secret, HMAC_SHA1));
        byte[] hash = mac.doFinal(counterBytes);

        // Dynamic truncation (RFC 4226)
        int offset = hash[hash.length - 1] & 0x0F;
        int truncated = ((hash[offset] & 0x7F) << 24)
                | ((hash[offset + 1] & 0xFF) << 16)
                | ((hash[offset + 2] & 0xFF) << 8)
                | (hash[offset + 3] & 0xFF);

        int otp = truncated % (int) Math.pow(10, digits);
        return String.format("%0" + digits + "d", otp);
    }

    /**
     * Constant-time string comparison for TOTP codes.
     */
    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }

    // --- Base32 Encoding/Decoding (RFC 4648) ---

    private static final String BASE32_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    public String base32Encode(byte[] data) {
        StringBuilder sb = new StringBuilder();
        int buffer = 0, bitsLeft = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                sb.append(BASE32_CHARS.charAt((buffer >> (bitsLeft - 5)) & 0x1F));
                bitsLeft -= 5;
            }
        }
        if (bitsLeft > 0) {
            sb.append(BASE32_CHARS.charAt((buffer << (5 - bitsLeft)) & 0x1F));
        }
        return sb.toString();
    }

    public byte[] base32Decode(String encoded) {
        encoded = encoded.toUpperCase().replaceAll("[^A-Z2-7]", "");
        byte[] output = new byte[encoded.length() * 5 / 8];
        int buffer = 0, bitsLeft = 0, index = 0;
        for (char c : encoded.toCharArray()) {
            int val = BASE32_CHARS.indexOf(c);
            if (val < 0) continue;
            buffer = (buffer << 5) | val;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                output[index++] = (byte) (buffer >> (bitsLeft - 8));
                bitsLeft -= 8;
            }
        }
        return java.util.Arrays.copyOf(output, index);
    }

    private String urlEncode(String value) {
        if (value == null) return "";
        return value.replace(" ", "%20")
                     .replace(":", "%3A")
                     .replace("@", "%40");
    }
}
