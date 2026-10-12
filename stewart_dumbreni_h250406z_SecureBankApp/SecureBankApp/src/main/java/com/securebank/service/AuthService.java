package com.securebank.service;

import com.securebank.model.User;
import com.securebank.security.PasswordHasher;
import com.securebank.storage.AuditLog;
import com.securebank.storage.Repository;

import java.io.IOException;
import java.util.*;

public final class AuthService {

    private static final int MAX_FAILED = 3;
    private static final long LOCK_MILLIS = 60_000;
    private static final byte[] DUMMY_SALT = new byte[16];

    private static final class Attempts { int fails; long lockedUntil; }

    private final Repository repo;
    private final AuditLog audit;
    private final Map<String, Attempts> attempts = new HashMap<>();

    public AuthService(Repository repo, AuditLog audit) {
        this.repo = repo;
        this.audit = audit;
    }

    public synchronized void register(String rawUsername, char[] password, char[] confirm) throws BankException {
        try {
            String username = InputValidator.normalizeUsername(rawUsername);
            if (!Arrays.equals(password, confirm)) throw new BankException("Passwords do not match.");
            InputValidator.checkPassword(password);
            if (repo.findUser(username).isPresent()) throw new BankException("That username is already taken.");

            byte[] salt = PasswordHasher.newSalt();
            int iterations = PasswordHasher.DEFAULT_ITERATIONS;
            repo.addUser(new User(username, salt, iterations, PasswordHasher.hash(password, salt, iterations)));
            try {
                repo.save();
            } catch (IOException e) {
                reloadQuietly();
                throw new BankException("Could not save your registration. Please try again.");
            }
            audit.log("REGISTER", username, "account created");
        } finally {
            Arrays.fill(password, '\0');
            Arrays.fill(confirm, '\0');
        }
    }

    public synchronized String login(String rawUsername, char[] password) throws BankException {
        try {
            if (!InputValidator.looksLikeUsername(rawUsername)) {
                audit.log("LOGIN_FAILED", "invalid-format", "-");
                throw new BankException("Invalid username or password.");
            }
            String username = rawUsername.trim().toLowerCase(Locale.ROOT);
            Attempts a = attempts.computeIfAbsent(username, k -> new Attempts());
            long now = System.currentTimeMillis();
            if (a.lockedUntil > now) {
                audit.log("LOGIN_BLOCKED", username, "locked out");
                throw new BankException("Too many failed attempts. Try again in "
                        + ((a.lockedUntil - now) / 1000 + 1) + " seconds.");
            }

            Optional<User> user = repo.findUser(username);
            boolean ok;
            if (user.isPresent()) {
                User u = user.get();
                ok = PasswordHasher.verify(password, u.salt(), u.iterations(), u.passwordHash());
            } else {
                PasswordHasher.hash(password, DUMMY_SALT, PasswordHasher.DEFAULT_ITERATIONS);
                ok = false;
            }

            if (ok) {
                attempts.remove(username);
                audit.log("LOGIN_OK", username, "-");
                return username;
            }
            if (++a.fails >= MAX_FAILED) {
                a.fails = 0;
                a.lockedUntil = now + LOCK_MILLIS;
                audit.log("LOCKOUT", username, "locked for 60s");
            } else {
                audit.log("LOGIN_FAILED", username, "bad credentials");
            }
            throw new BankException("Invalid username or password.");
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private void reloadQuietly() {
        try { repo.load(); } catch (IOException ignored) { }
    }
}
