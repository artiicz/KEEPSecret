package com.pwm.cli;

import com.pwm.crypto.TotpService;
import com.pwm.model.TotpConfig;
import picocli.CommandLine.Command;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

@Command(name = "totp-setup", description = "Set up two-factor authentication (TOTP).")
public class TotpSetupCommand implements Runnable {

    @Override
    public void run() {
        try {
            PasswordManagerCli.ensureVaultService();
            if (!PasswordManagerCli.vaultService.isUnlocked()) {
                System.err.println("Error: Vault is locked. Run 'unlock' first.");
                return;
            }
            if (!PasswordManagerCli.checkAutoLock()) return;

            TotpService totpService = new TotpService();
            TotpConfig config = new TotpConfig();

            String secret = totpService.generateSecret();
            config.setSecretBase32(secret);
            config.setAccountName("vault");
            config.setEnabled(true);

            String uri = totpService.generateOtpAuthUri(config);

            System.out.println();
            System.out.println("=== Two-Factor Authentication Setup ===");
            System.out.println();
            System.out.println("Scan this QR code with Google Authenticator:");
            System.out.println();

            // Generate and print ASCII QR code
            printQrCode(uri);

            System.out.println();
            System.out.println("Can't scan? Enter manually in your app:");
            System.out.println("  Account: SecurePWM:vault");
            System.out.println("  Secret:  " + secret);
            System.out.println("  Type:    Time-based (TOTP)");
            System.out.println();

            String verifyCode = PasswordManagerCli.readLine("Enter TOTP code to verify setup: ").trim();

            if (!totpService.validateCode(config, verifyCode)) {
                System.err.println("Error: Invalid TOTP code. Setup cancelled.");
                return;
            }

            PasswordManagerCli.vaultService.getCurrentVault().setTotpConfig(config);

            char[] masterPw = PasswordManagerCli.getSessionPassword();
            boolean ownedPassword = false;
            if (masterPw == null) {
                masterPw = PasswordManagerCli.readPassword("Master password (to save): ");
                ownedPassword = true;
            }

            PasswordManagerCli.vaultService.saveVault(
                    PasswordManagerCli.vaultService.getCurrentVault(), masterPw);

            System.out.println();
            System.out.println("2FA enabled successfully!");
            System.out.println("You will now need a TOTP code each time you unlock the vault.");

            if (ownedPassword) {
                PasswordManagerCli.vaultService.getCrypto().wipeChars(masterPw);
            }

        } catch (Exception e) {
            System.err.println("Error setting up 2FA: " + e.getMessage());
        }
    }

    /**
     * Generates and prints an ASCII QR code to the terminal using Unicode block characters.
     * Uses 2 QR rows per terminal line for a compact, scannable result.
     */
    private void printQrCode(String data) {
        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(data, BarcodeFormat.QR_CODE, 1, 1);

            int width = matrix.getWidth();
            int height = matrix.getHeight();

            // Unicode block elements:
            // Upper half block: \u2580
            // Lower half block: \u2584
            // Full block:       \u2588
            // Space for white

            // We process 2 rows at a time, using upper/lower half blocks
            // Black on white: QR modules are "black = data", we invert for dark terminals
            // White = QR module (dark on screen), Black = background

            StringBuilder sb = new StringBuilder();

            // Add quiet zone (white border) — top
            String whiteLine = "  " + "\u2588".repeat(width + 4);
            sb.append(whiteLine).append("\n");
            sb.append(whiteLine).append("\n");

            for (int y = 0; y < height; y += 2) {
                sb.append("  \u2588\u2588"); // Left quiet zone

                for (int x = 0; x < width; x++) {
                    boolean topBlack = matrix.get(x, y);
                    boolean bottomBlack = (y + 1 < height) && matrix.get(x, y + 1);

                    if (!topBlack && !bottomBlack) {
                        // Both white → full block (inverted: white = block)
                        sb.append('\u2588');
                    } else if (topBlack && bottomBlack) {
                        // Both black → space (inverted: black = space)
                        sb.append(' ');
                    } else if (topBlack && !bottomBlack) {
                        // Top black, bottom white → lower half block
                        sb.append('\u2584');
                    } else {
                        // Top white, bottom black → upper half block
                        sb.append('\u2580');
                    }
                }

                sb.append("\u2588\u2588"); // Right quiet zone
                sb.append("\n");
            }

            // Bottom quiet zone
            sb.append(whiteLine).append("\n");
            sb.append(whiteLine).append("\n");

            System.out.println(sb);

        } catch (Exception e) {
            System.err.println("  (Could not generate QR code: " + e.getMessage() + ")");
            System.err.println("  Please enter the secret manually in your authenticator app.");
        }
    }
}
