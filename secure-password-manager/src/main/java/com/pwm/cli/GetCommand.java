package com.pwm.cli;

import com.pwm.model.VaultEntry;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Command(name = "get", description = "Retrieve a credential from the vault.")
public class GetCommand implements Runnable {

    @Option(names = {"-n", "--name"}, description = "Entry name to look up", required = true)
    private String name;

    @Option(names = {"--show-password"}, description = "Display the password in plaintext", defaultValue = "false")
    private boolean showPassword;

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    @Override
    public void run() {
        try {
            if (!PasswordManagerCli.vaultService.isUnlocked()) {
                System.err.println("Error: Vault is locked. Run 'pwm unlock' first.");
                return;
            }
            if (!PasswordManagerCli.checkAutoLock()) return;

            VaultEntry entry = PasswordManagerCli.vaultService.getEntry(name);
            if (entry == null) {
                System.err.println("No entry found with name: " + name);
                return;
            }

            System.out.println("┌─────────────────────────────────────────");
            System.out.println("│ Name:     " + entry.getName());
            System.out.println("│ Username: " + entry.getUsername());
            if (showPassword) {
                System.out.println("│ Password: " + entry.getPassword());
            } else {
                System.out.println("│ Password: ********** (use --show-password to reveal)");
            }
            if (entry.getUrl() != null) {
                System.out.println("│ URL:      " + entry.getUrl());
            }
            if (entry.getNotes() != null) {
                System.out.println("│ Notes:    " + entry.getNotes());
            }
            System.out.println("│ Created:  " + FMT.format(Instant.ofEpochSecond(entry.getCreatedAt())));
            System.out.println("│ Modified: " + FMT.format(Instant.ofEpochSecond(entry.getModifiedAt())));
            System.out.println("└─────────────────────────────────────────");

        } catch (Exception e) {
            System.err.println("Error retrieving entry: " + e.getMessage());
        }
    }
}
