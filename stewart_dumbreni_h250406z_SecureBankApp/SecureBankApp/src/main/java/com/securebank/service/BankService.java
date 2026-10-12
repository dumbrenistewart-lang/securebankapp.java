package com.securebank.service;

import com.securebank.model.Account;
import com.securebank.model.AccountInfo;
import com.securebank.model.Transaction;
import com.securebank.storage.AuditLog;
import com.securebank.storage.Repository;

import java.io.IOException;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

public final class BankService {

    public static final BigDecimal MAX_PER_TRANSACTION = new BigDecimal("1000000.00");
    public static final int MAX_ACCOUNTS_PER_USER = 5;

    private final Repository repo;
    private final AuditLog audit;
    private final SecureRandom random = new SecureRandom();

    public BankService(Repository repo, AuditLog audit) {
        this.repo = repo;
        this.audit = audit;
    }

    public synchronized AccountInfo openAccount(String user, Account.Type type, BigDecimal initialDeposit)
            throws BankException {
        if (type == null) throw new BankException("Choose an account type.");
        if (repo.accountsOwnedBy(user).size() >= MAX_ACCOUNTS_PER_USER) {
            throw new BankException("You can hold at most " + MAX_ACCOUNTS_PER_USER + " accounts.");
        }
        checkAmountLimit(initialDeposit);

        Account account = new Account(newAccountNumber(), user, type, BigDecimal.ZERO, now());
        try {
            if (initialDeposit.signum() > 0) account.deposit(initialDeposit);
        } catch (IllegalArgumentException e) {
            throw new BankException(e.getMessage());
        }
        repo.addAccount(account);
        if (initialDeposit.signum() > 0) {
            repo.addTransaction(new Transaction(repo.nextTransactionId(), account.getNumber(),
                    Transaction.Type.OPENING, initialDeposit, account.getBalance(), now()));
        }
        persistOrRollback();
        audit.log("ACCOUNT_OPENED", user, account.getNumber());
        return AccountInfo.from(account);
    }

    public synchronized List<AccountInfo> accountsOf(String user) {
        List<AccountInfo> out = new ArrayList<>();
        for (Account a : repo.accountsOwnedBy(user)) out.add(AccountInfo.from(a));
        return out;
    }

    public synchronized BigDecimal balanceOf(String user, String accountNumber) throws BankException {
        return owned(user, accountNumber).getBalance();
    }

    public synchronized Transaction deposit(String user, String accountNumber, BigDecimal amount)
            throws BankException {
        return move(user, accountNumber, amount, true);
    }

    public synchronized Transaction withdraw(String user, String accountNumber, BigDecimal amount)
            throws BankException {
        return move(user, accountNumber, amount, false);
    }

    public synchronized List<Transaction> historyOf(String user, String accountNumber) throws BankException {
        owned(user, accountNumber);
        return repo.transactionsFor(accountNumber);
    }

    private Transaction move(String user, String accountNumber, BigDecimal amount, boolean deposit)
            throws BankException {
        Account account = owned(user, accountNumber);
        checkAmountLimit(amount);
        if (amount.signum() <= 0) throw new BankException("Amount must be greater than zero.");
        try {
            if (deposit) account.deposit(amount); else account.withdraw(amount);
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new BankException(e.getMessage());
        }
        Transaction tx = new Transaction(repo.nextTransactionId(), accountNumber,
                deposit ? Transaction.Type.DEPOSIT : Transaction.Type.WITHDRAWAL,
                amount, account.getBalance(), now());
        repo.addTransaction(tx);
        persistOrRollback();
        audit.log(deposit ? "DEPOSIT" : "WITHDRAWAL", user, accountNumber + " " + amount.toPlainString());
        return tx;
    }

    private Account owned(String user, String accountNumber) throws BankException {
        return repo.findAccount(accountNumber)
                .filter(a -> a.getOwner().equals(user))
                .orElseThrow(() -> {
                    audit.log("ACCESS_DENIED", user, String.valueOf(accountNumber));
                    return new BankException("Account not found.");
                });
    }

    private static void checkAmountLimit(BigDecimal amount) throws BankException {
        if (amount.compareTo(MAX_PER_TRANSACTION) > 0) {
            throw new BankException("Maximum per transaction is " + MAX_PER_TRANSACTION.toPlainString() + ".");
        }
    }

    private void persistOrRollback() throws BankException {
        try {
            repo.save();
        } catch (IOException e) {
            try { repo.load(); } catch (IOException ignored) { }
            throw new BankException("The change could not be saved and was cancelled.");
        }
    }

    private String newAccountNumber() {
        String n;
        do {
            n = String.valueOf(random.nextLong(1_000_000_000L, 10_000_000_000L));
        } while (repo.findAccount(n).isPresent());
        return n;
    }

    private static LocalDateTime now() { return LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS); }
}
