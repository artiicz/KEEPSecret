# 🔐 KEEPSecret

Ein sicherheitsfokussierter, kommandozeilenbasierter Passwortmanager mit Zwei-Faktor-Authentifizierung, entwickelt nach **OWASP Secure Design Principles** und **NIST Crypto Guidelines**.

---

## Inhaltsverzeichnis

- [Tech Stack](#tech-stack)
- [Voraussetzungen](#voraussetzungen)
- [Installation & Build](#installation--build)
- [Verwendung](#verwendung)
- [Befehle](#befehle)
- [Sicherheitsarchitektur](#sicherheitsarchitektur)
- [Dateiformat](#dateiformat)
- [Projektstruktur](#projektstruktur)
- [Projekt-Roadmap](#projekt-roadmap)
- [Bekannte Limitationen](#bekannte-limitationen)
- [Lizenz](#lizenz)

---

## Tech Stack

| Technologie | Version | Zweck |
|---|---|---|
| **Java** | 21 (LTS) | Laufzeitumgebung |
| **Apache Maven** | 3.9.13 | Build-Management |
| **Picocli** | 4.7.7 | CLI-Framework |
| **Bouncy Castle** | 1.83 | Kryptografie (Argon2id, AES-GCM) |
| **Google Gson** | 2.13.2 | JSON-Serialisierung |
| **Google ZXing** | 3.5.3 | QR-Code-Generierung (TOTP) |
| **JUnit** | 5.14.2 | Tests |

## Voraussetzungen

- Java JDK 21 oder neuer ([Adoptium](https://adoptium.net/))
- Apache Maven 3.9+ ([maven.apache.org](https://maven.apache.org/))

## Installation & Build

```bash
# Repository klonen
git clone https://github.com/<username>/keepsecret.git
cd keepsecret

# Projekt bauen
mvn clean package -DskipTests

# Starten
java -jar target\keepsecret-1.0.0-SNAPSHOT.jar
```

---

## Verwendung

### Interaktive Shell (empfohlen)

```bash
java -jar target\keepsecret-1.0.0-SNAPSHOT.jar
```

```
=============================================
  KEEPSecret v1.0.0
  Interactive Shell
---------------------------------------------
  Vault: C:\Users\<user>\.keepsecret_vault.enc
  Type 'help' for commands, 'exit' to quit
=============================================

keepsecret [locked]> init
Enter master password: ************
Confirm master password: ************
Deriving encryption key (this may take a moment)...
Vault initialized successfully.

keepsecret [locked]> unlock
Master password: ************
Deriving key and decrypting vault...
Vault unlocked successfully.

keepsecret [unlocked]> add -n "GitHub" -u "user@mail.com" --generate
Generated password: aB3$kL9#mP2&xQ7!wR5^yT8
Entry 'GitHub' added successfully.

keepsecret [unlocked]> list

#    NAME                      USERNAME
────────────────────────────────────────────────────────────
1    GitHub                    user@mail.com

Total: 1 entries

keepsecret [unlocked]> get -n "GitHub" --show-password
┌─────────────────────────────────────────
│ Name:     GitHub
│ Username: user@mail.com
│ Password: aB3$kL9#mP2&xQ7!wR5^yT8
│ Created:  2026-04-11 14:23:01
│ Modified: 2026-04-11 14:23:01
└─────────────────────────────────────────

keepsecret [unlocked]> totp-setup

=== Two-Factor Authentication Setup ===

Scan this QR code with Google Authenticator:

  ██████████████████████████████████
  ██ ▄▄▄▄▄ █▄█ ▀█▀▄█ ▄▄▄▄▄ ██
  ...

Can't scan? Enter manually in your app:
  Account: KEEPSecret:vault
  Secret:  JBSWY3DPEHPK3PXP...
  Type:    Time-based (TOTP)

keepsecret [unlocked]> lock
Vault locked. Key material wiped from memory.

keepsecret [locked]> exit
Vault locked. Goodbye!
```

### Eigene Vault-Datei

```bash
java -jar target\keepsecret-1.0.0-SNAPSHOT.jar --vault C:\pfad\zu\mein-vault.enc
```

---

## Befehle

| Befehl | Beschreibung |
|---|---|
| `init` | Neuen Vault erstellen (Passwort min. 12 Zeichen) |
| `unlock` | Vault entsperren (mit 2FA wenn aktiviert) |
| `lock` | Vault sperren & Keys aus Speicher löschen |
| `add -n <n> -u <User>` | Eintrag hinzufügen |
| `add ... --generate` | Mit zufällig generiertem Passwort |
| `add ... -l <Länge>` | Passwortlänge festlegen (Standard: 24) |
| `add ... --url <URL>` | URL zum Eintrag hinzufügen |
| `add ... --notes <Text>` | Notizen zum Eintrag hinzufügen |
| `get -n <n>` | Eintrag abrufen |
| `get ... --show-password` | Passwort im Klartext anzeigen |
| `list` | Alle Einträge auflisten (ohne Passwörter) |
| `list -q <Suche>` | Einträge nach Name durchsuchen |
| `generate` | Zufallspasswort erzeugen (ohne zu speichern) |
| `generate -l <Länge>` | Passwortlänge festlegen (Standard: 24) |
| `totp-setup` | 2FA einrichten (QR-Code im Terminal) |
| `status` | Vault-Status, Einträge, Auto-Lock-Timer anzeigen |
| `help` / `?` | Befehlsübersicht (in der Shell) |
| `clear` / `cls` | Bildschirm leeren (in der Shell) |
| `exit` / `quit` / `q` | Beenden (Vault wird automatisch gesperrt) |

---

## Sicherheitsarchitektur

### Kryptografischer Ablauf

```
Master Password
      │
      ▼
┌─────────────────────────┐
│  Argon2id KDF           │  ← Salt: 16 Bytes (SecureRandom)
│  Memory:     64 MiB     │  ← Iterations: 3
│  Parallelism: 4         │  ← Output: 256-bit Key
└───────────┬─────────────┘
            │
            ▼
┌─────────────────────────┐
│  AES-256-GCM            │  ← Nonce: 12 Bytes (SecureRandom)
│  Associated Data: Header │  ← Auth Tag: 128-bit
└───────────┬─────────────┘
            │
            ▼
  .keepsecret_vault.enc
```

### Sicherheitskomponenten

| Komponente | Verfahren | Standard |
|---|---|---|
| Key Derivation | Argon2id (64 MiB, 3 Iter., 4 Threads) | RFC 9106 |
| Verschlüsselung | AES-256-GCM (128-bit Auth Tag) | NIST SP 800-38D |
| Salt | 16 Bytes, `SecureRandom` | NIST SP 800-132 |
| Nonce | 12 Bytes, `SecureRandom`, pro Encryption neu | NIST SP 800-38D |
| 2FA | TOTP (SHA-1, 6 Digits, 30s, ±1 Window) | RFC 6238 |
| Vergleiche | Constant-Time (XOR-basiert) | CWE-208 |
| Dateiberechtigungen | Owner-only | CIS Benchmark |

### Defense-in-Depth

```
Schicht 1:  Argon2id KDF ──────── Brute-Force-resistent (Speicher + CPU)
Schicht 2:  AES-256-GCM ──────── Vertraulichkeit + Integrität (AEAD)
Schicht 3:  TOTP 2FA ──────────── Zweiter Faktor mit Replay-Schutz
Schicht 4:  Rate Limiting ─────── Max 5 Versuche/60s, Lockout nach 10
Schicht 5:  Auto-Lock ─────────── Automatisch nach 5 Min. Inaktivität
Schicht 6:  Secure Memory ─────── Keys + Passwörter explizit überschrieben
Schicht 7:  Constant-Time ─────── Schutz gegen Timing-Seitenkanalangriffe
Schicht 8:  File Permissions ──── Vault nur für Owner lesbar/schreibbar
```

---

## Dateiformat

Die Vault-Datei (`.keepsecret_vault.enc`) besteht aus drei Teilen:

```
{"formatVersion":"1.0","kdfAlgorithm":"Argon2id","kdfMemoryKiB":65536,...}
---VAULT---
RhBD0XR31ZfDOh18dzV0Qgiq6tcoxE9EYNOKj+gX3MZr...
```

| Teil | Inhalt | Verschlüsselt |
|---|---|---|
| **Header** | KDF-Parameter, Salt, Nonce (kompaktes JSON, einzeilig) | Nein (aber als AAD authentifiziert) |
| **Separator** | `---VAULT---` | — |
| **Ciphertext** | Vault-Daten (Base64-kodiert) | Ja (AES-256-GCM) |

Der Header wird als **Associated Data (AAD)** an AES-GCM übergeben. Jede Manipulation am Header führt zu einem Authentication-Tag-Fehler bei der Entschlüsselung.

---

## Projektstruktur

```
keepsecret/
├── pom.xml                          # Maven Build-Konfiguration
├── README.md
└── src/main/java/com/keepsecret/
    ├── cli/                         # CLI-Commands (Picocli)
    │   ├── KeepSecretCli.java       #   Haupteinstieg + Interaktive Shell
    │   ├── InitCommand.java         #   keepsecret init
    │   ├── UnlockCommand.java       #   keepsecret unlock (+ 2FA)
    │   ├── AddCommand.java          #   keepsecret add
    │   ├── GetCommand.java          #   keepsecret get
    │   ├── ListCommand.java         #   keepsecret list
    │   ├── GenerateCommand.java     #   keepsecret generate
    │   ├── TotpSetupCommand.java    #   keepsecret totp-setup (QR-Code)
    │   ├── LockCommand.java         #   keepsecret lock
    │   └── StatusCommand.java       #   keepsecret status
    ├── crypto/                      # Kryptografie-Kern
    │   ├── CryptoService.java       #   Argon2id KDF + AES-256-GCM
    │   └── TotpService.java         #   TOTP (RFC 6238) + Base32
    ├── model/                       # Datenmodelle
    │   ├── Vault.java               #   Vault-Container
    │   ├── VaultEntry.java          #   Einzelner Credential-Eintrag
    │   ├── VaultFileHeader.java     #   Datei-Header (KDF-Params, Salt, Nonce)
    │   └── TotpConfig.java          #   TOTP-Konfiguration + Replay-Counter
    └── service/                     # Business-Logik
        ├── VaultService.java        #   Vault CRUD + Encryption I/O
        ├── RateLimiter.java         #   Brute-Force-Schutz
        └── AutoLockService.java     #   Inaktivitäts-Sperre (Auto-Lock)
```

---

## Projekt-Roadmap

### Meilenstein 2 — Architektur & Kryptografie-Definition
Sicherheitskritische Architektur und kryptografische Grundlagen: sicheres Dateiformat, KDF-Auswahl (Argon2id), AEAD-Modus (AES-256-GCM), 2FA-Design, verbindliche Secure Defaults. Deliverables: Architekturdiagramm, kryptographische Begründung, Defense-in-Depth-Erklärung, Datenstruktur-Definition.

### Meilenstein 3 — Kern-Implementierung
Funktionaler Kern: CLI-Operationen (`init`, `unlock`, `add`, `get`, `list`), Argon2id-Schlüsselableitung, Authenticated Encryption, korrekte Salt/Nonce-Generierung, restriktive Dateiberechtigungen, kein Klartext-Logging. Deliverables: funktionsfähiges CLI, dokumentiertes Security Code Review.

### Meilenstein 4 — 2FA & Defense-in-Depth
Erweiterung: TOTP-Setup mit QR-Code, Rate Limiting, Constant-Time-Vergleiche, Auto-Lock, Vault Integrity Check, TOTP-Replay-Schutz, Secure Memory Handling. Deliverables: voll funktionsfähiger Passwortmanager, dokumentierte 2FA-Architektur.

### Meilenstein 5 — Security Audit & Härtung
Analyse aus Angreiferperspektive: Security Code Review, Testfälle für Bruteforce, manipulierte Vault-Dateien, veränderte Ciphertexte, fehlerhafte TOTP-Eingaben. Überprüfung von Logging, temporären Dateien, Speicherbehandlung. Optional: 1–2 kontrollierte Schwachstellen für Pentest. Deliverables: Dokument „Bekannte Limitationen", Angriffsflächen-Beschreibung, Pentester-Installationsanleitung.

---

## Bekannte Limitationen

- **JVM Memory:** Garbage Collector kann Kopien sensibler Daten im Speicher hinterlassen — byte[]/char[]-Wiping ist best-effort
- **Kein Clipboard-Management:** Kein automatisches Leeren der Zwischenablage nach Copy
- **Kein Cloud-Sync:** Vault-Datei ist lokal, kein Multi-Device-Support
- **Single-User:** Kein Sharing von Vaults zwischen mehreren Nutzern

> Dieses Kapitel wird im Rahmen von Meilenstein 5 um weitere bewusst belassene Schwachstellen und Angriffsflächen ergänzt.
