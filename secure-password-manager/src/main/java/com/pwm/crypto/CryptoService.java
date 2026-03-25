package com.pwm.crypto;

import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * Provides all cryptographic operations for the password manager.
 *
 * <h3>Design Decisions (NIST / OWASP aligned):</h3>
 * <ul>
 *   <li><b>KDF:</b> Argon2id (RFC 9106) — memory-hard, resistant to GPU/ASIC attacks</li>
 *   <li><b>Encryption:</b> AES-256-GCM — authenticated encryption (AEAD), prevents tampering</li>
 *   <li><b>Salt:</b> 16 bytes, cryptographically random per vault</li>
 *   <li><b>Nonce:</b> 12 bytes, cryptographically random per encryption</li>
 *   <li><b>Key length:</b> 256 bits (32 bytes)</li>
 * </ul>
 */
public class CryptoService {

    private static final int SALT_LENGTH = 16;
    private static final int NONCE_LENGTH = 12;
    private static final int KEY_LENGTH = 32;
    private static final int GCM_TAG_BITS = 128;
    private static final String AES_GCM = "AES/GCM/NoPadding";
    private static final String HMAC_SHA256 = "HmacSHA256";

    private final SecureRandom secureRandom;

    public CryptoService() {
        this.secureRandom = new SecureRandom();
    }

    /**
     * Generates a cryptographically secure random salt.
     */
    public byte[] generateSalt() {
        byte[] salt = new byte[SALT_LENGTH];
        secureRandom.nextBytes(salt);
        return salt;
    }

    /**
     * Generates a cryptographically secure random nonce for AES-GCM.
     */
    public byte[] generateNonce() {
        byte[] nonce = new byte[NONCE_LENGTH];
        secureRandom.nextBytes(nonce);
        return nonce;
    }

    /**
     * Derives a 256-bit encryption key from a password using Argon2id.
     *
     * @param password       Master password (char[] for secure memory handling)
     * @param salt           Random salt (16 bytes)
     * @param memoryKiB      Memory cost in KiB (default: 65536 = 64 MiB)
     * @param iterations     Time cost / iterations (default: 3)
     * @param parallelism    Parallelism factor (default: 4)
     * @return 32-byte derived key
     */
    public byte[] deriveKey(char[] password, byte[] salt, int memoryKiB, int iterations, int parallelism) {
        Argon2Parameters params = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withSalt(salt)
                .withMemoryAsKB(memoryKiB)
                .withIterations(iterations)
                .withParallelism(parallelism)
                .build();

        Argon2BytesGenerator generator = new Argon2BytesGenerator();
        generator.init(params);

        byte[] key = new byte[KEY_LENGTH];
        generator.generateBytes(new String(password).getBytes(java.nio.charset.StandardCharsets.UTF_8), key);
        return key;
    }

    /**
     * Encrypts plaintext using AES-256-GCM with the given key and nonce.
     * The header JSON is used as Associated Data (AD) for authentication.
     *
     * @param plaintext      Data to encrypt
     * @param key            256-bit encryption key
     * @param nonce          12-byte GCM nonce (must be unique per encryption)
     * @param associatedData Additional authenticated data (vault header)
     * @return Ciphertext with appended GCM authentication tag
     */
    public byte[] encrypt(byte[] plaintext, byte[] key, byte[] nonce, byte[] associatedData) throws Exception {
        Cipher cipher = Cipher.getInstance(AES_GCM);
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_BITS, nonce);
        SecretKeySpec keySpec = new SecretKeySpec(key, "AES");

        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec);
        if (associatedData != null && associatedData.length > 0) {
            cipher.updateAAD(associatedData);
        }

        return cipher.doFinal(plaintext);
    }

    /**
     * Decrypts ciphertext using AES-256-GCM.
     * Verifies the authentication tag — throws on tampered data.
     */
    public byte[] decrypt(byte[] ciphertext, byte[] key, byte[] nonce, byte[] associatedData) throws Exception {
        Cipher cipher = Cipher.getInstance(AES_GCM);
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_BITS, nonce);
        SecretKeySpec keySpec = new SecretKeySpec(key, "AES");

        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);
        if (associatedData != null && associatedData.length > 0) {
            cipher.updateAAD(associatedData);
        }

        return cipher.doFinal(ciphertext);
    }

    /**
     * Computes HMAC-SHA256 for vault integrity verification.
     */
    public byte[] computeHmac(byte[] key, byte[] data) throws Exception {
        Mac mac = Mac.getInstance(HMAC_SHA256);
        mac.init(new SecretKeySpec(key, HMAC_SHA256));
        return mac.doFinal(data);
    }

    /**
     * Constant-time comparison to prevent timing attacks.
     */
    public boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a == null || b == null || a.length != b.length) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length; i++) {
            result |= a[i] ^ b[i];
        }
        return result == 0;
    }

    /**
     * Securely wipes a byte array from memory.
     */
    public void wipeBytes(byte[] data) {
        if (data != null) {
            Arrays.fill(data, (byte) 0);
        }
    }

    /**
     * Securely wipes a char array from memory.
     */
    public void wipeChars(char[] data) {
        if (data != null) {
            Arrays.fill(data, '\0');
        }
    }

    /**
     * Generates a cryptographically secure random password.
     */
    public String generatePassword(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()-_=+[]{}|;:,.<>?";
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(secureRandom.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
