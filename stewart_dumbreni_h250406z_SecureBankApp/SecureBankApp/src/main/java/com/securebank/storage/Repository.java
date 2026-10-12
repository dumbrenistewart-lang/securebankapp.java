package com.securebank.storage;

import com.securebank.model.Account;
import com.securebank.model.Transaction;
import com.securebank.model.User;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;

public final class Repository {

    private static final String USERS_HEADER = "# username|salt(Base64)|pbkdf2Iterations|passwordHash(Base64)";
    private static final String ACCOUNTS_HEADER = "# accountNumber|owner|type|balance|createdAt";
    private static final String TX_HEADER = "# txId|accountNumber|type|amount|balanceAfter|timestamp";

    private final Path usersFile, accountsFile, txFile;
    private final Path dir;

    private final Map<String, User> users = new LinkedHashMap<>();
    private final Map<String, Account> accounts = new LinkedHashMap<>();
    private final List<Transaction> transactions = new ArrayList<>();

    public Repository(Path dir) {
        this.dir = dir;
        this.usersFile = dir.resolve("users.txt");
        this.accountsFile = dir.resolve("accounts.txt");
        this.txFile = dir.resolve("transactions.txt");
    }

    public synchronized void load() throws IOException {
        Files.createDirectories(dir);
        ensureFile(usersFile, USERS_HEADER);
        ensureFile(accountsFile, ACCOUNTS_HEADER);
        ensureFile(txFile, TX_HEADER);
        users.clear();
        accounts.clear();
        transactions.clear();

        readRows(usersFile, 4, f -> {
            User u = new User(f[0], Base64.getDecoder().decode(f[1]),
                    Integer.parseInt(f[2]), Base64.getDecoder().decode(f[3]));
            users.put(u.username(), u);
        });
        readRows(accountsFile, 5, f -> {
            Account a = new Account(f[0], f[1], Account.Type.valueOf(f[2]),
                    new BigDecimal(f[3]), LocalDateTime.parse(f[4]));
            accounts.put(a.getNumber(), a);
        });
        readRows(txFile, 6, f -> transactions.add(new Transaction(f[0], f[1],
                Transaction.Type.valueOf(f[2]), new BigDecimal(f[3]), new BigDecimal(f[4]),
                LocalDateTime.parse(f[5]))));
    }

    private interface RowHandler { void accept(String[] fields); }

    private static void readRows(Path file, int columns, RowHandler handler) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] fields = line.split("\\|", -1);
            try {
                if (fields.length != columns) throw new IllegalArgumentException("wrong column count");
                handler.accept(fields);
            } catch (IllegalArgumentException | DateTimeParseException e) {

                throw new IOException("Corrupt data in " + file.getFileName() + " at line " + (i + 1));
            }
        }
    }

    private static void ensureFile(Path file, String header) throws IOException {
        if (Files.notExists(file)) {
            Files.writeString(file, header + System.lineSeparator(), StandardCharsets.UTF_8);
            restrict(file);
        }
    }

    public synchronized void save() throws IOException {
        List<String> u = new ArrayList<>();
        for (User x : users.values()) {
            u.add(String.join("|", x.username(), Base64.getEncoder().encodeToString(x.salt()),
                    String.valueOf(x.iterations()), Base64.getEncoder().encodeToString(x.passwordHash())));
        }
        List<String> a = new ArrayList<>();
        for (Account x : accounts.values()) {
            a.add(String.join("|", x.getNumber(), x.getOwner(), x.getType().name(),
                    x.getBalance().toPlainString(), x.getCreatedAt().toString()));
        }
        List<String> t = new ArrayList<>();
        for (Transaction x : transactions) {
            t.add(String.join("|", x.id(), x.accountNumber(), x.type().name(),
                    x.amount().toPlainString(), x.balanceAfter().toPlainString(), x.timestamp().toString()));
        }
        writeAtomically(usersFile, USERS_HEADER, u);
        writeAtomically(accountsFile, ACCOUNTS_HEADER, a);
        writeAtomically(txFile, TX_HEADER, t);
    }

    private static void writeAtomically(Path target, String header, List<String> rows) throws IOException {
        List<String> all = new ArrayList<>();
        all.add(header);
        all.addAll(rows);
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.write(tmp, all, StandardCharsets.UTF_8);
        restrict(tmp);
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void restrict(Path file) {
        try {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException | IOException ignored) {

        }
    }

    public synchronized Optional<User> findUser(String username) { return Optional.ofNullable(users.get(username)); }

    public synchronized void addUser(User u) { users.put(u.username(), u); }

    public synchronized Optional<Account> findAccount(String number) { return Optional.ofNullable(accounts.get(number)); }

    public synchronized void addAccount(Account a) { accounts.put(a.getNumber(), a); }

    public synchronized List<Account> accountsOwnedBy(String owner) {
        List<Account> out = new ArrayList<>();
        for (Account a : accounts.values()) if (a.getOwner().equals(owner)) out.add(a);
        return out;
    }

    public synchronized void addTransaction(Transaction t) { transactions.add(t); }

    public synchronized List<Transaction> transactionsFor(String accountNumber) {
        List<Transaction> out = new ArrayList<>();
        for (Transaction t : transactions) if (t.accountNumber().equals(accountNumber)) out.add(t);
        return out;
    }

    public synchronized String nextTransactionId() {
        return String.format("TX%06d", transactions.size() + 1);
    }
}
