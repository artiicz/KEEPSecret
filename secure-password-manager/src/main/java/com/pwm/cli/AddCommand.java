package com.pwm.cli;

import com.pwm.model.VaultEntry;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "add", description = "Add a new credential entry to the vault.")
public class AddCommand implements Runnable {

    @Option(names = {"-n", "--name"}, description = "Entry name (e.g. 'GitHub')", required = true)
    private String name;

    @Option(names = {"-u", "--user"}, description = "Username or email", required = true)
    private String username;

    @Option(names = {"--url"}, description = "Website URL (optional)")
    private String url;

    @Option(names = {"--notes"}, description = "Additional notes (optional)")
    private String notes;

    @Option(names = {"-g", "--generate"}, description = "Generate a random password")
    private boolean generate;

    @Option(names = {"-l", "--length"}, description = "Generated password length (default: 24)", defaultValue = "24")
    private int length;

    @Override
    public void run() {
        try {
            PasswordManagerCli.ensureVaultService();
            if (!PasswordManagerCli.vaultService.isUnlocked()) {
                System.err.println("Error: Vault is locked. Run 'unlock' first.");
                return;
            }
            if (!PasswordManagerCli.checkAutoLock()) return;

            // Check for duplicate names
            if (PasswordManagerCli.vaultService.getEntry(name) != null) {
                System.err.println("Error: An entry with name '" + name + "' already exists.");
                return;
            }

            String password;
            if (generate) {
                password = PasswordManagerCli.vaultService.getCrypto().generatePassword(length);
                System.out.println("Generated password: " + password);
            } else {
                char[] pw = PasswordManagerCli.readPassword("Password for '" + name + "': ");
                password = new String(pw);
                PasswordManagerCli.vaultService.getCrypto().wipeChars(pw);
            }

            // Use session password if available, otherwise ask
            char[] masterPw = PasswordManagerCli.getSessionPassword();
            boolean ownedPassword = false;
            if (masterPw == null) {
                masterPw = PasswordManagerCli.readPassword("Master password (to save): ");
                ownedPassword = true;
            }

            VaultEntry entry = PasswordManagerCli.vaultService.addEntry(name, username, password, masterPw);

            if (url != null) entry.setUrl(url);
            if (notes != null) entry.setNotes(notes);
            PasswordManagerCli.vaultService.saveVault(
                    PasswordManagerCli.vaultService.getCurrentVault(), masterPw);

            System.out.println("Entry '" + name + "' added successfully.");
            if (ownedPassword) {
                PasswordManagerCli.vaultService.getCrypto().wipeChars(masterPw);
            }

        } catch (Exception e) {
            System.err.println("Error adding entry: " + e.getMessage());
        }
    }
}
