package com.securebank.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class Account {

    public enum Type { SAVINGS, CURRENT }

    public static final BigDecimal MAX_BALANCE = new BigDecimal("999999999.99");

    private final String number;
    private final String owner;
    private final Type type;
    private final LocalDateTime createdAt;
    private BigDecimal balance;

    public Account(String number, String owner, Type type, BigDecimal balance, LocalDateTime createdAt) {
        this.number = number;
        this.owner = owner;
        this.type = type;
        this.balance = balance.setScale(2);
        this.createdAt = createdAt;
    }

    public String getNumber()           { return number; }
    public String getOwner()            { return owner; }
    public Type getType()               { return type; }
    public BigDecimal getBalance()      { return balance; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void deposit(BigDecimal amount) {
        requirePositive(amount);
        BigDecimal updated = balance.add(amount);
        if (updated.compareTo(MAX_BALANCE) > 0) {
            throw new IllegalArgumentException("Deposit would exceed the maximum allowed balance.");
        }
        balance = updated;
    }

    public void withdraw(BigDecimal amount) {
        requirePositive(amount);
        if (amount.compareTo(balance) > 0) {
            throw new IllegalStateException("Insufficient funds.");
        }
        balance = balance.subtract(amount);
    }

    private static void requirePositive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero.");
        }
    }
}
