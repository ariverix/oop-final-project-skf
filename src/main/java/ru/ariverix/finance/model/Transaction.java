package ru.ariverix.finance.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record Transaction(Kind kind, String category, BigDecimal amount,
                          LocalDate date, String description) {
    public enum Kind {
        INCOME, EXPENSE
    }

    public Transaction {
        if (kind == null || category == null || category.isBlank()
                || amount == null || amount.signum() <= 0 || date == null) {
            throw new IllegalArgumentException("Некорректная операция");
        }
        description = description == null ? "" : description;
    }
}
