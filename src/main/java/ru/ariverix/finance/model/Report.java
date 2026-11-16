package ru.ariverix.finance.model;

import java.math.BigDecimal;
import java.util.Map;

public record Report(BigDecimal income, BigDecimal expense,
                     Map<String, BigDecimal> incomeByCategory,
                     Map<String, BigDecimal> expenseByCategory,
                     Map<String, BigDecimal> remainingBudgets) {
    public BigDecimal balance() {
        return income.subtract(expense);
    }
}
