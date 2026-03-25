package com.pwm.cli;

import picocli.CommandLine.Command;

@Command(name = "lock", description = "Lock the vault and wipe key material from memory.")
public class LockCommand implements Runnable {

    @Override
    public void run() {
        if (!PasswordManagerCli.vaultService.isUnlocked()) {
            System.out.println("Vault is already locked.");
            return;
        }

        PasswordManagerCli.vaultService.lock();
        PasswordManagerCli.clearSessionPassword();
        System.out.println("Vault locked. Key material wiped from memory.");
    }
}
