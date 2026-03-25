package com.pwm.cli;

import picocli.CommandLine.Command;
import java.util.Arrays;

@Command(name = "init", description = "Initialize a new password vault.")
public class InitCommand implements Runnable {

    @Override
    public void run() {
        try {
            PasswordManagerCli.ensureVaultService();
            if (PasswordManagerCli.vaultService.vaultExists()) {
                System.err.println("Error: Vault already exists at " + PasswordManagerCli.vaultService.getVaultPath());
                System.err.println("Delete the existing vault file to create a new one.");
                return;
            }

            char[] password = PasswordManagerCli.readPassword("Enter master password: ");
            char[] confirm = PasswordManagerCli.readPassword("Confirm master password: ");

            if (!Arrays.equals(password, confirm)) {
                System.err.println("Error: Passwords do not match.");
                PasswordManagerCli.vaultService.getCrypto().wipeChars(password);
                PasswordManagerCli.vaultService.getCrypto().wipeChars(confirm);
                return;
            }

            if (password.length < 12) {
                System.err.println("Error: Master password must be at least 12 characters.");
                PasswordManagerCli.vaultService.getCrypto().wipeChars(password);
                PasswordManagerCli.vaultService.getCrypto().wipeChars(confirm);
                return;
            }

            System.out.println("Deriving encryption key (this may take a moment)...");
            PasswordManagerCli.vaultService.initVault(password);

            System.out.println("Vault initialized successfully at: " + PasswordManagerCli.vaultService.getVaultPath());
            System.out.println();
            System.out.println("IMPORTANT: Remember your master password!");
            System.out.println("There is no recovery mechanism if you forget it.");

            PasswordManagerCli.vaultService.getCrypto().wipeChars(password);
            PasswordManagerCli.vaultService.getCrypto().wipeChars(confirm);

        } catch (Exception e) {
            System.err.println("Error initializing vault: " + e.getMessage());
        }
    }
}
