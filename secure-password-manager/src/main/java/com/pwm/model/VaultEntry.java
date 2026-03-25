package com.pwm.model;

import java.time.Instant;
import java.util.UUID;

/**
 * A single credential entry within the vault.
 */
public class VaultEntry {

    private String id;
    private String name;
    private String username;
    private String password;
    private String url;
    private String notes;
    private long createdAt;
    private long modifiedAt;

    public VaultEntry() {
        this.id = UUID.randomUUID().toString();
        this.createdAt = Instant.now().getEpochSecond();
        this.modifiedAt = this.createdAt;
    }

    public VaultEntry(String name, String username, String password) {
        this();
        this.name = name;
        this.username = username;
        this.password = password;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getModifiedAt() { return modifiedAt; }
    public void setModifiedAt(long modifiedAt) { this.modifiedAt = modifiedAt; }

    public void touch() {
        this.modifiedAt = Instant.now().getEpochSecond();
    }
}
