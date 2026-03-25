package com.pwm.model;

/**
 * Configuration for TOTP-based two-factor authentication.
 */
public class TotpConfig {

    private boolean enabled;
    private String secretBase32;
    private String issuer = "SecurePWM";
    private String accountName;
    private int digits = 6;
    private int periodSeconds = 30;
    private String algorithm = "SHA1";
    private long lastUsedCounter = -1;  // Replay protection

    public TotpConfig() {}

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getSecretBase32() { return secretBase32; }
    public void setSecretBase32(String secretBase32) { this.secretBase32 = secretBase32; }

    public String getIssuer() { return issuer; }
    public void setIssuer(String issuer) { this.issuer = issuer; }

    public String getAccountName() { return accountName; }
    public void setAccountName(String accountName) { this.accountName = accountName; }

    public int getDigits() { return digits; }
    public void setDigits(int digits) { this.digits = digits; }

    public int getPeriodSeconds() { return periodSeconds; }
    public void setPeriodSeconds(int periodSeconds) { this.periodSeconds = periodSeconds; }

    public String getAlgorithm() { return algorithm; }
    public void setAlgorithm(String algorithm) { this.algorithm = algorithm; }

    public long getLastUsedCounter() { return lastUsedCounter; }
    public void setLastUsedCounter(long lastUsedCounter) { this.lastUsedCounter = lastUsedCounter; }
}
