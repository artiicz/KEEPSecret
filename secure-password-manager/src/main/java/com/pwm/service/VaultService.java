package com.pwm.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.pwm.crypto.CryptoService;
import com.pwm.model.Vault;
import com.pwm.model.VaultEntry;
import com.pwm.model.VaultFileHeader;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Core service for vault CRUD operations.
 * Handles serialization, encryption, file I/O, and permission management.
 */
public class VaultService {

    private static final String VAULT_FILE = ".pwm_vault.enc";

    private static final String VAULT_SEPARATOR = "\n---VAULT---\n";

    private final CryptoService crypto;
    private final Gson gson;         // Pretty for vault data
    private final Gson headerGson;   // Compact for header (must be single-line!)
    private final Path vaultPath;

    private Vault currentVault;
    private byte[] currentKey;

    public VaultService() {
        this(Path.of(System.getProperty("user.home"), VAULT_FILE));
    }

    public VaultService(Path vaultPath) {
        this.crypto = new CryptoService();
        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .disableHtmlEscaping()
                .create();
        this.headerGson = new GsonBuilder()
                .disableHtmlEscaping()  // Compact, single-line, no HTML escaping
                .create();
        this.vaultPath = vaultPath;
    }

    /**
     * Initializes a new vault with a master password.
     */
    public void initVault(char[] masterPassword) throws Exception {
        if (Files.exists(vaultPath)) {
            throw new IllegalStateException("Vault already exists at: " + vaultPath);
        }

        Vault vault = new Vault();
        saveVault(vault, masterPassword);
        this.currentVault = vault;
    }

    /**
     * Unlocks an existing vault with the master password.
     */
    public boolean unlockVault(char[] masterPassword) throws Exception {
        if (!Files.exists(vaultPath)) {
            throw new IllegalStateException("No vault found. Run 'init' first.");
        }

        String fileContent = Files.readString(vaultPath, StandardCharsets.UTF_8);
        int separatorIdx = fileContent.indexOf(VAULT_SEPARATOR);
        if (separatorIdx < 0) {
            throw new IllegalStateException("Invalid vault file format (missing separator).");
        }

        String headerJson = fileContent.substring(0, separatorIdx);
        String ciphertextBase64 = fileContent.substring(separatorIdx + VAULT_SEPARATOR.length()).trim();

        VaultFileHeader header = headerGson.fromJson(headerJson, VaultFileHeader.class);
        byte[] salt = Base64.getDecoder().decode(header.getSaltBase64());
        byte[] nonce = Base64.getDecoder().decode(header.getNonceBase64());
        byte[] ciphertext = Base64.getDecoder().decode(ciphertextBase64);

        // Derive key from password
        byte[] key = crypto.deriveKey(masterPassword,
                salt,
                header.getKdfMemoryKiB(),
                header.getKdfIterations(),
                header.getKdfParallelism());

        try {
            // Decrypt with header as associated data for authentication
            byte[] plaintext = crypto.decrypt(ciphertext, key, nonce,
                    headerJson.getBytes(StandardCharsets.UTF_8));

            String jsonStr = new String(plaintext, StandardCharsets.UTF_8);
            this.currentVault = gson.fromJson(jsonStr, Vault.class);
            this.currentKey = key;

            // Wipe plaintext from memory
            crypto.wipeBytes(plaintext);

            return true;
        } catch (javax.crypto.AEADBadTagException e) {
            crypto.wipeBytes(key);
            return false; // Wrong password — GCM tag mismatch
        } catch (Exception e) {
            crypto.wipeBytes(key);
            throw e; // Rethrow other errors (corrupt file, etc.)
        }
    }

    /**
     * Adds a new entry to the vault.
     */
    public VaultEntry addEntry(String name, String username, String password, char[] masterPassword) throws Exception {
        ensureUnlocked();
        VaultEntry entry = new VaultEntry(name, username, password);
        currentVault.getEntries().add(entry);
        currentVault.touch();
        saveVault(currentVault, masterPassword);
        return entry;
    }

    /**
     * Retrieves an entry by name (case-insensitive search).
     */
    public VaultEntry getEntry(String name) {
        ensureUnlocked();
        return currentVault.getEntries().stream()
                .filter(e -> e.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }

    /**
     * Lists all entry names and usernames (never passwords in listing).
     */
    public List<VaultEntry> listEntries() {
        ensureUnlocked();
        return currentVault.getEntries();
    }

    /**
     * Searches entries by name substring.
     */
    public List<VaultEntry> searchEntries(String query) {
        ensureUnlocked();
        String lowerQuery = query.toLowerCase();
        return currentVault.getEntries().stream()
                .filter(e -> e.getName().toLowerCase().contains(lowerQuery))
                .collect(Collectors.toList());
    }

    /**
     * Returns the current vault (for TOTP config access etc.).
     */
    public Vault getCurrentVault() {
        return currentVault;
    }

    /**
     * Saves the vault to disk.
     */
    public void saveVault(Vault vault, char[] masterPassword) throws Exception {
        byte[] salt = crypto.generateSalt();
        byte[] nonce = crypto.generateNonce();

        byte[] key = crypto.deriveKey(masterPassword,
                salt,
                65536,  // 64 MiB
                3,      // iterations
                4);     // parallelism

        VaultFileHeader header = new VaultFileHeader();
        header.setSaltBase64(Base64.getEncoder().encodeToString(salt));
        header.setNonceBase64(Base64.getEncoder().encodeToString(nonce));
        // integrityHash is left null for now — the AAD used for encryption
        // is the final header JSON (which includes null integrityHash).
        // On decryption, the same header is read from file and used as AAD.

        // Serialize header as compact single-line JSON
        String headerJson = headerGson.toJson(header);
        byte[] headerBytes = headerJson.getBytes(StandardCharsets.UTF_8);
        byte[] vaultJson = gson.toJson(vault).getBytes(StandardCharsets.UTF_8);

        // Encrypt vault with header as associated data
        byte[] ciphertext = crypto.encrypt(vaultJson, key, nonce, headerBytes);

        // Write file: header + separator + ciphertext
        String ciphertextBase64 = Base64.getEncoder().encodeToString(ciphertext);
        String fileContent = headerJson + VAULT_SEPARATOR + ciphertextBase64;
        Files.writeString(vaultPath, fileContent, StandardCharsets.UTF_8);

        // Set restrictive file permissions (owner read/write only)
        try {
            File file = vaultPath.toFile();
            file.setReadable(false, false);
            file.setWritable(false, false);
            file.setExecutable(false, false);
            file.setReadable(true, true);
            file.setWritable(true, true);
        } catch (Exception e) {
            // Best-effort: permission setting may not work on all filesystems
        }

        // Update current key for session
        if (this.currentKey != null) {
            crypto.wipeBytes(this.currentKey);
        }
        this.currentKey = key;

        // Wipe sensitive data
        crypto.wipeBytes(vaultJson);
        crypto.wipeBytes(salt);
        crypto.wipeBytes(nonce);
    }

    /**
     * Locks the vault: wipes key material from memory.
     */
    public void lock() {
        if (currentKey != null) {
            crypto.wipeBytes(currentKey);
            currentKey = null;
        }
        currentVault = null;
    }

    /**
     * Checks if the vault file exists.
     */
    public boolean vaultExists() {
        return Files.exists(vaultPath);
    }

    public boolean isUnlocked() {
        return currentVault != null && currentKey != null;
    }

    public CryptoService getCrypto() {
        return crypto;
    }

    public Path getVaultPath() {
        return vaultPath;
    }

    private void ensureUnlocked() {
        if (!isUnlocked()) {
            throw new IllegalStateException("Vault is locked. Run 'unlock' first.");
        }
    }
}
