# 🔐 Secure Password Manager (CLI)

Ein sicherheitsfokussierter, kommandozeilenbasierter Passwortmanager mit Zwei-Faktor-Authentifizierung, entwickelt nach **OWASP Secure Design Principles** und **NIST Crypto Guidelines**.

---

## Inhaltsverzeichnis

- [Überblick](#überblick)
- [Tech Stack & Versionen](#tech-stack--versionen)
- [Features](#features)
- [Installation & Build](#installation--build)
- [Verwendung](#verwendung)
- [Architektur & Sicherheit](#architektur--sicherheit)
- [Dateiformat](#dateiformat)
- [Projekt-Roadmap](#projekt-roadmap)
  - [Meilenstein 2 — Architektur & Kryptografie-Definition](#meilenstein-2--architektur--kryptografie-definition)
  - [Meilenstein 3 — Kern-Implementierung](#meilenstein-3--kern-implementierung)
  - [Meilenstein 4 — 2FA & Defense-in-Depth](#meilenstein-4--2fa--defense-in-depth)
  - [Meilenstein 5 — Security Audit & Härtung](#meilenstein-5--security-audit--härtung)
- [Projektstruktur](#projektstruktur)
- [Bekannte Limitationen](#bekannte-limitationen)
- [Lizenz](#lizenz)

---

## Überblick

Dieses Projekt implementiert einen produktionsnahen Passwortmanager als CLI-Anwendung. Der Fokus liegt auf nachvollziehbarer Kryptografie, mehrschichtiger Absicherung (Defense-in-Depth) und einer transparenten Sicherheitsarchitektur, die auch für Pentests und Security-Audits geeignet ist.

## Tech Stack & Versionen

| Technologie | Version | Zweck |
|---|---|---|
| **Java (LTS)** | 21 (LTS, Support bis Sep 2031) | Laufzeitumgebung & Sprache |
| **Apache Maven** | 3.9.13 | Build-Management & Dependency Resolution |
| **Picocli** | 4.7.7 | CLI-Framework (Subcommands, Options, Help) |
| **Bouncy Castle** | 1.83 (`bcprov-jdk18on`) | Kryptografie (Argon2id, AES-GCM) |
| **Google Gson** | 2.13.2 | JSON-Serialisierung der Vault-Daten |
| **Google ZXing** | 3.5.3 | QR-Code-Generierung (TOTP-Setup) |
| **JUnit** | 5.14.2 / 6.0.2 | Unit- und Integrationstests |

> **Hinweis zu Java-Versionen:** Java 25 (LTS, Sep 2025) ist die neueste LTS-Version. Das Projekt verwendet Java 21, da dies die breiteste Tool- und Framework-Kompatibilität bietet. Ein Upgrade auf Java 25 ist jederzeit möglich.

> **Hinweis zu Maven:** Maven 4.0.0 befindet sich derzeit im Release-Candidate-Status und ist noch nicht für den Produktionseinsatz empfohlen. Daher wird Maven 3.9.13 verwendet.

## Features

- **CLI-Operationen:** `init`, `unlock`, `add`, `get`, `list`, `generate`, `totp-setup`, `lock`
- **Starke Schlüsselableitung:** Argon2id (RFC 9106) mit konfigurierbaren Parametern
- **Authenticated Encryption:** AES-256-GCM (AEAD) mit Associated Data
- **Zwei-Faktor-Authentifizierung:** TOTP (RFC 6238) mit QR-Code-Setup und Replay-Schutz
- **Defense-in-Depth:** Rate Limiting, Auto-Lock, Vault Integrity Check (HMAC-SHA256)
- **Secure Memory Handling:** Explizites Wipen von Keys und Passwörtern im Speicher
- **Constant-Time Vergleiche:** Schutz gegen Timing-Angriffe
- **Restriktive Dateiberechtigungen:** Vault-Datei mit `chmod 600` (Owner-Only)
- **Passwort-Generator:** Kryptographisch sicherer Zufallsgenerator

## Installation & Build

### Voraussetzungen

- Java 21 (oder neuer): [Adoptium](https://adoptium.net/) oder [Oracle JDK](https://www.oracle.com/java/)
- Apache Maven 3.9+: [maven.apache.org](https://maven.apache.org/)

### Build

```bash
# Repository klonen
git clone https://github.com/<username>/secure-password-manager.git
cd secure-password-manager

# Build mit Maven
mvn clean package

# Fat-JAR wird erstellt unter:
# target/secure-password-manager-1.0.0-SNAPSHOT.jar
```

### Ausführen

```bash
# Direkt via Maven
java -jar target/secure-password-manager-1.0.0-SNAPSHOT.jar --help

# Optional: Alias setzen
alias pwm='java -jar /path/to/secure-password-manager-1.0.0-SNAPSHOT.jar'
```

## Verwendung

```bash
# Neuen Vault initialisieren
pwm init

# Vault entsperren
pwm unlock

# Eintrag mit automatisch generiertem Passwort hinzufügen
pwm add -n "GitHub" -u "user@example.com" --generate

# Eintrag mit eigenem Passwort hinzufügen
pwm add -n "AWS Console" -u "admin@company.com"

# Passwort abrufen
pwm get -n "GitHub" --show-password

# Alle Einträge auflisten
pwm list

# Einträge durchsuchen
pwm list -q "git"

# Sicheres Passwort generieren (ohne zu speichern)
pwm generate -l 32

# 2FA einrichten
pwm totp-setup

# Vault sperren (Key-Material wird aus dem Speicher gelöscht)
pwm lock
```

## Architektur & Sicherheit

### Kryptografische Architektur

```
Master Password
      │
      ▼
┌─────────────────────┐
│  Argon2id KDF       │  ← Salt (16 Bytes, zufällig)
│  Memory: 64 MiB     │  ← Iterations: 3
│  Parallelism: 4     │  ← Output: 256-bit Key
└─────────┬───────────┘
          │
          ▼
┌─────────────────────┐
│  AES-256-GCM        │  ← Nonce (12 Bytes, zufällig)
│  + Associated Data   │  ← Header JSON als AAD
│  + Auth Tag (128-bit)│
└─────────┬───────────┘
          │
          ▼
   Encrypted Vault File
```

### Sicherheitstabelle

| Komponente | Verfahren / Ansatz | Standard |
|---|---|---|
| Key Derivation | Argon2id (64 MiB, 3 Iter., 4 Threads) | RFC 9106 |
| Verschlüsselung | AES-256-GCM (128-bit Tag) | NIST SP 800-38D |
| Salt | 16 Bytes, `SecureRandom` | NIST SP 800-132 |
| Nonce | 12 Bytes, `SecureRandom`, pro Encryption neu | NIST SP 800-38D |
| 2FA | TOTP (SHA-1, 6 Digits, 30s) | RFC 6238 |
| Integrity | HMAC-SHA256 über Header + Ciphertext | NIST FIPS 198-1 |
| Vergleiche | Constant-Time (XOR-basiert) | CERT / CWE-208 |
| Dateiberechtigungen | `chmod 600` (POSIX) | CIS Benchmark |

### Defense-in-Depth Schichten

1. **Starke KDF:** Argon2id macht Brute-Force extrem kostspielig (Speicher + CPU)
2. **AEAD:** AES-256-GCM garantiert Vertraulichkeit UND Integrität
3. **2FA:** TOTP als zweiter Faktor bei Vault-Unlock
4. **Rate Limiting:** Max. 5 Versuche / 60s, exponentielles Backoff, Lockout nach 10 Fehlversuchen
5. **Auto-Lock:** Automatisches Sperren nach 5 Minuten Inaktivität
6. **Replay-Schutz:** TOTP-Counter-Tracking verhindert Code-Wiederverwendung
7. **Secure Memory:** Keys und Passwörter werden explizit überschrieben
8. **File Permissions:** Vault-Datei nur für Owner lesbar

## Dateiformat

Die Vault-Datei besteht aus zwei Teilen, getrennt durch einen Zeilenumbruch:

```
[VaultFileHeader als JSON]
[Verschlüsselter Vault als Base64]
```

**Header (unverschlüsselt, aber als AAD authentifiziert):**
```json
{
  "formatVersion": "1.0",
  "kdfAlgorithm": "Argon2id",
  "kdfMemoryKiB": 65536,
  "kdfIterations": 3,
  "kdfParallelism": 4,
  "encryptionAlgorithm": "AES-256-GCM",
  "saltBase64": "...",
  "nonceBase64": "...",
  "integrityHashBase64": "..."
}
```

**Vault (verschlüsselt, enthält nach Entschlüsselung):**
```json
{
  "version": "1.0.0",
  "createdAt": 1710000000,
  "modifiedAt": 1710000000,
  "entries": [
    {
      "id": "uuid",
      "name": "GitHub",
      "username": "user@example.com",
      "password": "...",
      "url": "https://github.com",
      "createdAt": 1710000000,
      "modifiedAt": 1710000000
    }
  ],
  "totpConfig": { ... }
}
```

---

## Projekt-Roadmap

### Meilenstein 2 — Architektur & Kryptografie-Definition

> **Fokus:** Sicherheitskritische Architektur und kryptografische Grundlagen

**Ziele:**
- Spezifikation eines sicheren Dateiformats für den Vault
- Festlegung der Key-Derivation-Function (KDF)
- Auswahl eines geeigneten Authenticated-Encryption-Modus
- Konzeption des 2FA-Designs
- Definition verbindlicher Secure Defaults zur Vermeidung von Fehlkonfigurationen

**Deliverables:**
- Architekturdiagramm
- Kryptographische Begründung der gewählten Verfahren
- Defense-in-Depth-Erklärung
- Detaillierte Datenstruktur-Definition

**Referenzen:** OWASP Secure Design Principles, NIST Crypto Guidelines

---

### Meilenstein 3 — Kern-Implementierung

> **Fokus:** Funktionaler und sicherheitstechnischer Kern des Systems

**Ziele:**
- Vollständige und stabile Implementierung der CLI-Operationen: `init`, `unlock`, `add`, `get`, `list`
- Argon2id zur Schlüsselableitung mit Bouncy Castle 1.83
- Authenticated Encryption (AES-256-GCM) mit korrekt generierten Salt- und Nonce-Werten
- Restriktive Dateiberechtigungen (`chmod 600`)
- Keinerlei sensibles Klartext-Logging

**Hinweis:** Zwei-Faktor-Authentifizierung ist in diesem Meilenstein noch nicht vorgesehen.

**Deliverables:**
- Funktionsfähiges CLI (Picocli 4.7.7)
- Dokumentiertes internes Security Code Review

---

### Meilenstein 4 — 2FA & Defense-in-Depth

> **Fokus:** Erweiterung um 2FA und zusätzliche Schutzschichten

**Ziele:**
- TOTP-Setup inkl. QR-Code-Anzeige (ZXing 3.5.3)
- Rate Limiting gegen Brute-Force-Angriffe (5 Versuche / 60s, exponentielles Backoff)
- Constant-Time Vergleiche für Passwörter und TOTP-Codes
- Auto-Lock bei Inaktivität (300s Standard)
- Vault Integrity Check (HMAC-SHA256)
- Replay-Schutz bei TOTP (Counter-Tracking)
- Secure Memory Handling (explizites Wipen von byte[] und char[])

**Deliverables:**
- Voll funktionsfähiger Passwortmanager
- Dokumentierte 2FA-Architektur

---

### Meilenstein 5 — Security Audit & Härtung

> **Fokus:** Analyse aus Angreiferperspektive und gezielte Härtung

**Ziele:**
- Security-fokussiertes Code Review
- Testfälle für: Bruteforce-Angriffe, manipulierte Vault-Dateien, veränderte Ciphertexte, fehlerhafte TOTP-Eingaben
- Überprüfung von Logging-Verhalten, temporären Dateien und Speicherbehandlung
- Optional: 1–2 kontrollierte Schwachstellen dokumentiert belassen (für realistische Pentest-Szenarien)

**Deliverables:**
- Dokument „Bekannte Limitationen"
- Strukturierte Beschreibung der Angriffsflächen
- Installationsanleitung für Pentester

---

## Projektstruktur

```
secure-password-manager/
├── pom.xml                          # Maven Build-Konfiguration
├── README.md                        # Dieses Dokument
├── src/
│   ├── main/java/com/pwm/
│   │   ├── cli/                     # CLI-Commands (Picocli)
│   │   │   ├── PasswordManagerCli.java    # Haupteinstiegspunkt
│   │   │   ├── InitCommand.java           # pwm init
│   │   │   ├── UnlockCommand.java         # pwm unlock (+ 2FA)
│   │   │   ├── AddCommand.java            # pwm add
│   │   │   ├── GetCommand.java            # pwm get
│   │   │   ├── ListCommand.java           # pwm list
│   │   │   ├── GenerateCommand.java       # pwm generate
│   │   │   ├── TotpSetupCommand.java      # pwm totp-setup
│   │   │   └── LockCommand.java           # pwm lock
│   │   ├── crypto/                  # Kryptografie-Kern
│   │   │   ├── CryptoService.java         # Argon2id + AES-256-GCM
│   │   │   └── TotpService.java           # TOTP (RFC 6238)
│   │   ├── model/                   # Datenmodelle
│   │   │   ├── Vault.java                 # Vault-Container
│   │   │   ├── VaultEntry.java            # Einzelner Credential-Eintrag
│   │   │   ├── VaultFileHeader.java       # Datei-Header (KDF-Params etc.)
│   │   │   └── TotpConfig.java            # TOTP-Konfiguration
│   │   └── service/                 # Business-Logik
│   │       ├── VaultService.java          # Vault CRUD + Encryption I/O
│   │       ├── RateLimiter.java           # Brute-Force-Schutz
│   │       └── AutoLockService.java       # Inaktivitäts-Sperre
│   └── test/java/com/pwm/          # Tests (JUnit 5)
└── docs/                            # Dokumentation
    ├── architecture.md              # Architekturdiagramm
    ├── crypto-rationale.md          # Kryptographische Begründung
    ├── pentest-setup.md             # Installationsanleitung für Pentester
    └── known-limitations.md         # Bekannte Limitationen
```

## Bekannte Limitationen

> Dieses Kapitel wird im Rahmen von Meilenstein 5 ergänzt und dokumentiert bewusst belassene Einschränkungen sowie bekannte Angriffsflächen.

- JVM-basiert: Memory-Wiping ist best-effort (GC kann Kopien im Speicher hinterlassen)
- Kein Clipboard-Management (Copy-to-Clipboard mit Auto-Clear)
- Kein Cloud-Sync oder Multi-Device-Support
- Single-User-Design (kein Sharing von Vaults)

## Lizenz

Dieses Projekt steht unter der [MIT-Lizenz](LICENSE).
