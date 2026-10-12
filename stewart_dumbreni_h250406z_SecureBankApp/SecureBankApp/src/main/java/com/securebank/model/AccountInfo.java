package com.securebank.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AccountInfo(String number, Account.Type type, BigDecimal balance, LocalDateTime createdAt) {
    public static AccountInfo from(Account a) {
        return new AccountInfo(a.getNumber(), a.getType(), a.getBalance(), a.getCreatedAt());
    }
}
