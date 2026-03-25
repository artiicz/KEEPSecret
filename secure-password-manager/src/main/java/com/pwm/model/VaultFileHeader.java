package com.pwm.model;

/**
 * Header/metadata stored alongside the encrypted vault.
 * This data is NOT encrypted, but is authenticated via AEAD associated data.
 *
 * File format:
 * [VaultFileHeader as JSON] + '\n' + [encrypted vault bytes as Base64]
 */
public class VaultFileHeader {

    private String formatVersion = "1.0";
    private String kdfAlgorithm = "Argon2id";
    private int kdfMemoryKiB = 65536;       // 64 MiB
    private int kdfIterations = 3;
    private int kdfParallelism = 4;
    private String encryptionAlgorithm = "AES-256-GCM";
    private String saltBase64;              // Argon2 salt (16 bytes)
    private String nonceBase64;             // GCM nonce (12 bytes)
    private String integrityHashBase64;     // HMAC-SHA256 of entire file for integrity check

    public VaultFileHeader() {}

    // Getters and Setters
    public String getFormatVersion() { return formatVersion; }
    public void setFormatVersion(String formatVersion) { this.formatVersion = formatVersion; }

    public String getKdfAlgorithm() { return kdfAlgorithm; }
    public void setKdfAlgorithm(String kdfAlgorithm) { this.kdfAlgorithm = kdfAlgorithm; }

    public int getKdfMemoryKiB() { return kdfMemoryKiB; }
    public void setKdfMemoryKiB(int kdfMemoryKiB) { this.kdfMemoryKiB = kdfMemoryKiB; }

    public int getKdfIterations() { return kdfIterations; }
    public void setKdfIterations(int kdfIterations) { this.kdfIterations = kdfIterations; }

    public int getKdfParallelism() { return kdfParallelism; }
    public void setKdfParallelism(int kdfParallelism) { this.kdfParallelism = kdfParallelism; }

    public String getEncryptionAlgorithm() { return encryptionAlgorithm; }
    public void setEncryptionAlgorithm(String encryptionAlgorithm) { this.encryptionAlgorithm = encryptionAlgorithm; }

    public String getSaltBase64() { return saltBase64; }
    public void setSaltBase64(String saltBase64) { this.saltBase64 = saltBase64; }

    public String getNonceBase64() { return nonceBase64; }
    public void setNonceBase64(String nonceBase64) { this.nonceBase64 = nonceBase64; }

    public String getIntegrityHashBase64() { return integrityHashBase64; }
    public void setIntegrityHashBase64(String integrityHashBase64) { this.integrityHashBase64 = integrityHashBase64; }
}
