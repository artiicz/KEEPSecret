package com.pwm.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "generate", description = "Generate a secure random password.")
public class GenerateCommand implements Runnable {

    @Option(names = {"-l", "--length"}, description = "Password length (default: 24)", defaultValue = "24")
    private int length;

    @Override
    public void run() {
        if (length < 8) {
            System.err.println("Error: Minimum password length is 8 characters.");
            return;
        }
        if (length > 128) {
            System.err.println("Error: Maximum password length is 128 characters.");
            return;
        }

        String password = PasswordManagerCli.vaultService.getCrypto().generatePassword(length);
        System.out.println(password);
    }
}
