package com.pwm.cli;

import com.pwm.model.VaultEntry;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.List;

@Command(name = "list", description = "List all entries in the vault.")
public class ListCommand implements Runnable {

    @Option(names = {"-q", "--query"}, description = "Search filter (substring match)")
    private String query;

    @Override
    public void run() {
        try {
            if (!PasswordManagerCli.vaultService.isUnlocked()) {
                System.err.println("Error: Vault is locked. Run 'pwm unlock' first.");
                return;
            }
            if (!PasswordManagerCli.checkAutoLock()) return;

            List<VaultEntry> entries;
            if (query != null && !query.isBlank()) {
                entries = PasswordManagerCli.vaultService.searchEntries(query);
                System.out.println("Search results for: \"" + query + "\"");
            } else {
                entries = PasswordManagerCli.vaultService.listEntries();
            }

            if (entries.isEmpty()) {
                System.out.println("No entries found.");
                return;
            }

            // NEVER show passwords in the listing
            System.out.println();
            System.out.printf("%-4s %-25s %-30s%n", "#", "NAME", "USERNAME");
            System.out.println("─".repeat(60));

            int i = 1;
            for (VaultEntry entry : entries) {
                System.out.printf("%-4d %-25s %-30s%n",
                        i++,
                        truncate(entry.getName(), 24),
                        truncate(entry.getUsername(), 29));
            }

            System.out.println();
            System.out.println("Total: " + entries.size() + " entries");
            System.out.println("Use 'pwm get -n <name>' to view details.");

        } catch (Exception e) {
            System.err.println("Error listing entries: " + e.getMessage());
        }
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max - 2) + ".." : s;
    }
}
