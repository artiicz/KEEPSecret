package com.pwm.cli;

import com.pwm.service.AutoLockService;
import com.pwm.service.RateLimiter;
import com.pwm.service.VaultService;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.Console;
import java.nio.file.Path;
import java.util.Scanner;

/**
 * Secure Password Manager — CLI entry point.
 *
 * Supports two modes:
 * 1. Single command:   java -jar pwm.jar unlock
 * 2. Interactive shell: java -jar pwm.jar  (no args → starts shell)
 *
 * The interactive shell keeps the vault unlocked in memory so you
 * can run multiple commands without re-entering the master password.
 */
@Command(
    name = "pwm",
    mixinStandardHelpOptions = true,
    version = "Secure Password Manager 1.0.0",
    description = "A security-focused CLI password manager with 2FA support.",
    subcommands = {
        InitCommand.class,
        UnlockCommand.class,
        AddCommand.class,
        GetCommand.class,
        ListCommand.class,
        GenerateCommand.class,
        TotpSetupCommand.class,
        LockCommand.class,
        StatusCommand.class,
    }
)
public class PasswordManagerCli implements Runnable {

    @Option(names = {"--vault", "-f"},
            description = "Path to vault file (default: ~/.pwm_vault.enc)",
            scope = CommandLine.ScopeType.INHERIT)
    static String vaultPathOption;

    // Shared state across commands within a session
    static VaultService vaultService;
    static RateLimiter rateLimiter = new RateLimiter();
    static AutoLockService autoLock = new AutoLockService();

    // Session password kept in memory while vault is unlocked (wiped on lock/exit)
    static char[] sessionPassword;

    // Shared scanner for interactive shell mode (avoids stdin conflicts)
    static Scanner sharedScanner;

    /**
     * Initializes the VaultService with the configured path.
     */
    static void ensureVaultService() {
        if (vaultService == null) {
            if (vaultPathOption != null && !vaultPathOption.isBlank()) {
                vaultService = new VaultService(Path.of(vaultPathOption));
            } else {
                vaultService = new VaultService();
            }
        }
    }

    @Override
    public void run() {
        ensureVaultService();
        startInteractiveShell();
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            // Interactive shell mode
            new PasswordManagerCli().run();
        } else {
            // Single command mode
            ensureVaultServiceFromArgs(args);
            int exitCode = new CommandLine(new PasswordManagerCli()).execute(args);
            cleanup();
            System.exit(exitCode);
        }
    }

    /**
     * Pre-parse --vault option before picocli runs.
     */
    private static void ensureVaultServiceFromArgs(String[] args) {
        for (int i = 0; i < args.length - 1; i++) {
            if ("--vault".equals(args[i]) || "-f".equals(args[i])) {
                vaultPathOption = args[i + 1];
                break;
            }
        }
        ensureVaultService();
    }

    /**
     * Interactive shell — keeps vault state in memory across commands.
     */
    private void startInteractiveShell() {
        sharedScanner = new Scanner(System.in);

        System.out.println();
        System.out.println("=============================================");
        System.out.println("  Secure Password Manager v1.0.0");
        System.out.println("  Interactive Shell");
        System.out.println("---------------------------------------------");
        System.out.println("  Vault: " + vaultService.getVaultPath());
        System.out.println("  Type 'help' for commands, 'exit' to quit");
        System.out.println("=============================================");
        System.out.println();

        while (true) {
            // Show lock status in prompt
            String status = vaultService.isUnlocked() ? "[unlocked]" : "[locked]";
            System.out.print("pwm " + status + "> ");

            if (!sharedScanner.hasNextLine()) break;
            String line = sharedScanner.nextLine().trim();

            if (line.isEmpty()) continue;

            // Built-in shell commands
            String lower = line.toLowerCase();
            if (lower.equals("exit") || lower.equals("quit") || lower.equals("q")) {
                cleanup();
                System.out.println("Vault locked. Goodbye!");
                break;
            }
            if (lower.equals("help") || lower.equals("?")) {
                printShellHelp();
                continue;
            }
            if (lower.equals("clear") || lower.equals("cls")) {
                System.out.print("\033[H\033[2J");
                System.out.flush();
                continue;
            }
            if (lower.equals("status")) {
                printStatus();
                continue;
            }

            // Check auto-lock before processing
            checkAutoLock();

            // Parse and execute picocli command
            try {
                String[] shellArgs = parseShellArgs(line);
                new CommandLine(new PasswordManagerCli()).execute(shellArgs);
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
            }
        }
    }

    /**
     * Parses a shell input line into arguments, respecting quoted strings.
     * E.g.: add -n "My GitHub" -u user@mail.com --generate
     */
    private String[] parseShellArgs(String line) {
        java.util.List<String> args = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        char quoteChar = 0;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == quoteChar) {
                    inQuotes = false;
                } else {
                    current.append(c);
                }
            } else if (c == '"' || c == '\'') {
                inQuotes = true;
                quoteChar = c;
            } else if (c == ' ') {
                if (current.length() > 0) {
                    args.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            args.add(current.toString());
        }
        return args.toArray(new String[0]);
    }

    private void printShellHelp() {
        System.out.println();
        System.out.println("  Vault Commands:");
        System.out.println("    init                       Create a new vault");
        System.out.println("    unlock                     Unlock the vault");
        System.out.println("    lock                       Lock & wipe keys from memory");
        System.out.println("    status                     Show vault status");
        System.out.println();
        System.out.println("  Entry Commands:");
        System.out.println("    add -n <Name> -u <User>    Add an entry");
        System.out.println("        --generate             Auto-generate password");
        System.out.println("        -l <length>            Password length (default: 24)");
        System.out.println("    get -n <Name>              Retrieve an entry");
        System.out.println("        --show-password        Show password in plaintext");
        System.out.println("    list                       List all entries");
        System.out.println("        -q <search>            Filter by name");
        System.out.println();
        System.out.println("  Security:");
        System.out.println("    totp-setup                 Set up 2FA (TOTP)");
        System.out.println("    generate -l <length>       Generate random password");
        System.out.println();
        System.out.println("  Shell:");
        System.out.println("    help / ?                   Show this help");
        System.out.println("    clear / cls                Clear screen");
        System.out.println("    exit / quit / q            Exit (auto-locks vault)");
        System.out.println();
    }

    private void printStatus() {
        System.out.println();
        System.out.println("  Vault file:    " + vaultService.getVaultPath());
        System.out.println("  Exists:        " + (vaultService.vaultExists() ? "Yes" : "No"));
        System.out.println("  Status:        " + (vaultService.isUnlocked() ? "UNLOCKED" : "LOCKED"));
        if (vaultService.isUnlocked()) {
            System.out.println("  Entries:       " + vaultService.listEntries().size());
            System.out.println("  Auto-lock in:  " + autoLock.secondsUntilLock() + "s");
            var totp = vaultService.getCurrentVault().getTotpConfig();
            System.out.println("  2FA:           " + (totp != null && totp.isEnabled() ? "Enabled" : "Not configured"));
        }
        System.out.println();
    }

    /** Stores session password after successful unlock. */
    static void setSessionPassword(char[] password) {
        clearSessionPassword();
        sessionPassword = password.clone();
    }

    /** Returns stored session password, or null if not set. */
    static char[] getSessionPassword() {
        return sessionPassword;
    }

    /**
     * Reads a line of input using the shared scanner (shell mode) or a new one.
     * In real terminal, uses Console.readPassword for hidden input.
     */
    static String readLine(String prompt) {
        System.out.print(prompt);
        if (sharedScanner != null) {
            return sharedScanner.nextLine();
        }
        return new Scanner(System.in).nextLine();
    }

    /**
     * Reads a password. Uses Console.readPassword if available (hides input),
     * otherwise falls back to shared scanner.
     */
    static char[] readPassword(String prompt) {
        Console console = System.console();
        if (console != null) {
            return console.readPassword(prompt);
        }
        return readLine(prompt).toCharArray();
    }

    static boolean checkAutoLock() {
        if (autoLock.shouldLock() && vaultService.isUnlocked()) {
            vaultService.lock();
            clearSessionPassword();
            System.err.println("[SECURITY] Vault auto-locked due to inactivity.");
            return false;
        }
        if (vaultService.isUnlocked()) {
            autoLock.recordActivity();
        }
        return true;
    }

    static void cleanup() {
        if (vaultService != null && vaultService.isUnlocked()) {
            vaultService.lock();
        }
        clearSessionPassword();
    }

    static void clearSessionPassword() {
        if (sessionPassword != null && vaultService != null) {
            vaultService.getCrypto().wipeChars(sessionPassword);
        }
        sessionPassword = null;
    }
}
