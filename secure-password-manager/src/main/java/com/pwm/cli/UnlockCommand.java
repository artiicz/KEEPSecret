package com.pwm.cli;

import com.pwm.crypto.TotpService;
import com.pwm.model.TotpConfig;
import picocli.CommandLine.Command;

@Command(name = "unlock", description = "Unlock the password vault.")
public class UnlockCommand implements Runnable {

    @Override
    public void run() {
        try {
            PasswordManagerCli.ensureVaultService();
            if (!PasswordManagerCli.vaultService.vaultExists()) {
                System.err.println("Error: No vault found. Run 'init' first.");
                return;
            }

            if (PasswordManagerCli.vaultService.isUnlocked()) {
                System.out.println("Vault is already unlocked.");
                return;
            }

            // Rate limiting check
            String rateLimitMsg = PasswordManagerCli.rateLimiter.checkAllowed();
            if (rateLimitMsg != null) {
                System.err.println("[SECURITY] " + rateLimitMsg);
                return;
            }

            char[] password = PasswordManagerCli.readPassword("Master password: ");

            System.out.println("Deriving key and decrypting vault...");
            boolean success = PasswordManagerCli.vaultService.unlockVault(password);

            if (!success) {
                PasswordManagerCli.rateLimiter.recordFailure();
                int attempts = PasswordManagerCli.rateLimiter.getFailedAttempts();
                System.err.println("Error: Wrong password or corrupted vault.");
                System.err.println("Failed attempts: " + attempts);
                PasswordManagerCli.vaultService.getCrypto().wipeChars(password);
                return;
            }

            // Check if 2FA is enabled
            TotpConfig totpConfig = PasswordManagerCli.vaultService.getCurrentVault().getTotpConfig();
            if (totpConfig != null && totpConfig.isEnabled()) {
                String totpCode = PasswordManagerCli.readLine("TOTP code: ").trim();

                TotpService totpService = new TotpService();
                if (!totpService.validateCode(totpConfig, totpCode)) {
                    System.err.println("Error: Invalid or expired TOTP code.");
                    PasswordManagerCli.vaultService.lock();
                    PasswordManagerCli.rateLimiter.recordFailure();
                    PasswordManagerCli.vaultService.getCrypto().wipeChars(password);
                    return;
                }

                // Save updated counter for replay protection
                PasswordManagerCli.vaultService.saveVault(
                        PasswordManagerCli.vaultService.getCurrentVault(), password);
            }

            PasswordManagerCli.rateLimiter.recordSuccess();
            PasswordManagerCli.autoLock.recordActivity();

            // Store password in session
            PasswordManagerCli.setSessionPassword(password);
            PasswordManagerCli.vaultService.getCrypto().wipeChars(password);

            System.out.println("Vault unlocked successfully.");

        } catch (Exception e) {
            System.err.println("Error unlocking vault: " + e.getMessage());
        }
    }
}
