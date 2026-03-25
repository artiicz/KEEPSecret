package com.pwm.service;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Auto-lock mechanism that locks the vault after a period of inactivity.
 * Default timeout: 5 minutes (300 seconds).
 */
public class AutoLockService {

    private static final int DEFAULT_TIMEOUT_SECONDS = 300;

    private final int timeoutSeconds;
    private final AtomicLong lastActivity;

    public AutoLockService() {
        this(DEFAULT_TIMEOUT_SECONDS);
    }

    public AutoLockService(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
        this.lastActivity = new AtomicLong(Instant.now().getEpochSecond());
    }

    /**
     * Records user activity to reset the inactivity timer.
     */
    public void recordActivity() {
        lastActivity.set(Instant.now().getEpochSecond());
    }

    /**
     * Checks if the vault should be auto-locked due to inactivity.
     */
    public boolean shouldLock() {
        long now = Instant.now().getEpochSecond();
        return (now - lastActivity.get()) >= timeoutSeconds;
    }

    /**
     * Returns seconds until auto-lock triggers.
     */
    public long secondsUntilLock() {
        long elapsed = Instant.now().getEpochSecond() - lastActivity.get();
        return Math.max(0, timeoutSeconds - elapsed);
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }
}
