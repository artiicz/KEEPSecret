package com.pwm.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents the encrypted vault structure.
 * The vault is stored as JSON, then encrypted with AES-256-GCM.
 */
public class Vault {

    private String version = "1.0.0";
    private long createdAt;
    private long modifiedAt;
    private List<VaultEntry> entries;
    private TotpConfig totpConfig;

    public Vault() {
        this.createdAt = Instant.now().getEpochSecond();
        this.modifiedAt = this.createdAt;
        this.entries = new ArrayList<>();
    }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getModifiedAt() { return modifiedAt; }
    public void setModifiedAt(long modifiedAt) { this.modifiedAt = modifiedAt; }

    public List<VaultEntry> getEntries() { return entries; }
    public void setEntries(List<VaultEntry> entries) { this.entries = entries; }

    public TotpConfig getTotpConfig() { return totpConfig; }
    public void setTotpConfig(TotpConfig totpConfig) { this.totpConfig = totpConfig; }

    public void touch() {
        this.modifiedAt = Instant.now().getEpochSecond();
    }
}
