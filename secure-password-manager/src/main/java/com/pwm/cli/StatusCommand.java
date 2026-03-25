package com.pwm.cli;

import picocli.CommandLine.Command;

@Command(name = "status", description = "Show current vault status.")
public class StatusCommand implements Runnable {

    @Override
    public void run() {
        PasswordManagerCli.ensureVaultService();
        var vs = PasswordManagerCli.vaultService;

        System.out.println();
        System.out.println("  Vault file:    " + vs.getVaultPath());
        System.out.println("  Exists:        " + (vs.vaultExists() ? "Yes" : "No"));
        System.out.println("  Status:        " + (vs.isUnlocked() ? "UNLOCKED" : "LOCKED"));
        if (vs.isUnlocked()) {
            System.out.println("  Entries:       " + vs.listEntries().size());
            System.out.println("  Auto-lock in:  " + PasswordManagerCli.autoLock.secondsUntilLock() + "s");
            var totp = vs.getCurrentVault().getTotpConfig();
            System.out.println("  2FA:           " + (totp != null && totp.isEnabled() ? "Enabled" : "Not configured"));
        }
        System.out.println();
    }
}
