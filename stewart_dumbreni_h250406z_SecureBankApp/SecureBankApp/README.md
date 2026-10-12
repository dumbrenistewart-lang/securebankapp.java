# Secure Banking Application

## Description
A Java (Maven) desktop banking application with a Swing GUI that demonstrates object-oriented design
and secure coding practices. Users register, log in, open accounts, deposit and withdraw money, and
export an account statement as a PDF. All data is stored in plain text files - no database is used.

## Student Details
- **Name:** [Your Full Name]
- **Registration Number:** [Your Reg Number]

## Features
- **User authentication** - registration and login (passwords stored only as salted PBKDF2 hashes)
- **Account management** - open Savings or Current accounts (up to 5 per user) and view balances
- **Transactions** - deposit and withdraw funds, with a full per-account transaction history
- **Data persistence** - users, accounts and transactions saved to `data/*.txt` and loaded at start-up
- **PDF statement export** - save any account's statement (details, transactions, totals, multi-page) as a PDF
- **Graphical interface** - Swing GUI with login window and account dashboard
- **Security extras** - login lockout, auto-logout after inactivity, audit log, input validation

## Technologies
Java 17+, Maven, Swing. **No third-party libraries** - the PDF writer is part of the code base.

## How to Build and Run
Requirements: JDK 17 or newer and Maven 3.8+.

```bash
# from the project root (the folder containing pom.xml)
mvn clean package
java -jar target/SecureBankApp-1.0.0.jar
```

or run directly without building a jar:

```bash
mvn compile exec:java
```

Run it from the project root so the application finds the `data/` folder. A different data folder can
be given as an argument: `java -jar target/SecureBankApp-1.0.0.jar /path/to/data`.

### Using the application
1. Click **Register**, choose a username and a strong password, then **Login**.
2. Click **Open new account**, choose the type and an optional initial deposit.
3. Select an account, type an amount and press **Deposit** or **Withdraw**.
4. Select an account and press **Export statement (PDF)** to save its statement.

## Project Structure
```
SecureBankApp/
├── pom.xml
├── data/                      users.txt, accounts.txt, transactions.txt (audit.log is created at run time)
└── src/main/java/com/securebank/
    ├── Main.java              start-up: loads data, wires services, opens the GUI
    ├── model/                 User, Account, AccountInfo, Transaction
    ├── security/              PasswordHasher (PBKDF2)
    ├── storage/               Repository (file persistence), AuditLog
    ├── service/               AuthService, BankService, InputValidator, BankException
    ├── pdf/                   PdfWriter (minimal PDF generator), StatementExporter
    └── gui/                   LoginFrame, DashboardFrame
```

## OOP Design
- **Encapsulation** - `Account` keeps its balance private and only changes it through `deposit()` / `withdraw()`,
  which enforce the rules. The GUI only ever receives read-only `AccountInfo` snapshots.
- **Separation of concerns** - GUI -> services -> repository; the GUI never touches files or balances directly.
- **Immutability** - `User`, `Transaction` and `AccountInfo` are records.

## Secure Coding Practices
| Area | What was done |
|------|---------------|
| Password storage | PBKDF2-HMAC-SHA256, 600,000 iterations, 16-byte random salt per user; constant-time comparison |
| Password handling | `char[]` / `JPasswordField`; arrays wiped after use; never logged or displayed |
| Password policy | 8-64 chars with upper-case, lower-case, digit and symbol |
| Brute-force defence | 3 failed logins lock the username for 60 s; generic error message; dummy hash for unknown users (no user enumeration / timing leak) |
| Input validation | Allow-list regex for usernames and amounts; all money in `BigDecimal` (never `double`) |
| File-injection safety | Fields are validated, enums or Base64, so the `\|` delimiter / newlines can never be injected into data files |
| Access control | Every banking call re-checks that the account belongs to the logged-in user |
| Business rules | No overdrafts, max 1,000,000.00 per transaction, balance ceiling, max 5 accounts |
| Data integrity | Atomic file writes (temp file + move); in-memory state is reloaded if a save fails; corrupt data files stop start-up |
| File permissions | Data files are created owner-only (`rw-------`) on POSIX systems |
| Error handling | Users see safe messages; internal details are never shown |
| Auditing | `data/audit.log` records logins, lockouts, transactions and denied access (no secrets) |
| Session safety | Automatic logout after 5 minutes of inactivity |

## Data Files
All files are pipe (`|`) delimited; lines starting with `#` are comments.

| File | Columns |
|------|---------|
| `users.txt` | `username \| salt(Base64) \| iterations \| passwordHash(Base64)` |
| `accounts.txt` | `accountNumber \| owner \| type \| balance \| createdAt` |
| `transactions.txt` | `txId \| accountNumber \| type \| amount \| balanceAfter \| timestamp` |

## Notes
- Do not edit the data files by hand; any malformed line makes the application refuse to start rather than guess.
