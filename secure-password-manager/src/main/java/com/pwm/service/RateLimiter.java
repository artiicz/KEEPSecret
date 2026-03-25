package com.pwm.service;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Rate limiter to prevent brute-force attacks on the master password.
 *
 * Strategy:
 * - Max 5 attempts in a 60-second window
 * - Exponential backoff after exceeding limit
 * - Lockout after 10 consecutive failures
 */
public class RateLimiter {

    private static final int MAX_ATTEMPTS = 5;
    private static final int WINDOW_SECONDS = 60;
    private static final int LOCKOUT_THRESHOLD = 10;
    private static final int BASE_DELAY_MS = 1000;

    private final AtomicInteger failedAttempts = new AtomicInteger(0);
    private final AtomicInteger windowAttempts = new AtomicInteger(0);
    private final AtomicLong windowStart = new AtomicLong(Instant.now().getEpochSecond());
    private final AtomicLong lockoutUntil = new AtomicLong(0);

    /**
     * Checks if an attempt is currently allowed.
     * @return null if allowed, or a message string if blocked
     */
    public String checkAllowed() {
        long now = Instant.now().getEpochSecond();

        // Check lockout
        long lockout = lockoutUntil.get();
        if (lockout > 0 && now < lockout) {
            long remaining = lockout - now;
            return String.format("Account locked. Try again in %d seconds.", remaining);
        }

        // Reset window if expired
        if (now - windowStart.get() >= WINDOW_SECONDS) {
            windowStart.set(now);
            windowAttempts.set(0);
        }

        // Check rate limit
        if (windowAttempts.get() >= MAX_ATTEMPTS) {
            int delay = BASE_DELAY_MS * (1 << Math.min(failedAttempts.get() - MAX_ATTEMPTS, 5));
            return String.format("Too many attempts. Wait %d seconds.", delay / 1000);
        }

        return null;
    }

    /**
     * Records a failed authentication attempt.
     */
    public void recordFailure() {
        int total = failedAttempts.incrementAndGet();
        windowAttempts.incrementAndGet();

        if (total >= LOCKOUT_THRESHOLD) {
            // Lock out for exponentially increasing time
            int lockoutSeconds = 60 * (1 << Math.min(total - LOCKOUT_THRESHOLD, 4));
            lockoutUntil.set(Instant.now().getEpochSecond() + lockoutSeconds);
        }
    }

    /**
     * Resets the rate limiter on successful authentication.
     */
    public void recordSuccess() {
        failedAttempts.set(0);
        windowAttempts.set(0);
        lockoutUntil.set(0);
    }

    public int getFailedAttempts() {
        return failedAttempts.get();
    }
}
