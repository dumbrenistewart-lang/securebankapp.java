package com.securebank.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record Transaction(String id, String accountNumber, Type type, BigDecimal amount,
                          BigDecimal balanceAfter, LocalDateTime timestamp) {

    public enum Type { OPENING, DEPOSIT, WITHDRAWAL }
}
