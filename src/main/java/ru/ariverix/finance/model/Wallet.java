package ru.ariverix.finance.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record Wallet(String owner, List<Transaction> transactions,
                     Map<String, BigDecimal> budgets, Set<String> categories) {

    public Wallet {
        if (owner == null || owner.isBlank() || transactions == null
                || budgets == null || categories == null) {
            throw new IllegalArgumentException("Некорректный кошелёк");
        }
        transactions = List.copyOf(transactions);
        budgets = Map.copyOf(budgets);
        categories = Set.copyOf(categories);
    }

    public static Wallet empty(String owner) {
        Set<String> initial = new LinkedHashSet<>(Set.of(
                "Еда", "Такси", "Развлечения", "Коммунальные услуги",
                "Зарплата", "Бонус", "Переводы"));
        return new Wallet(owner, List.of(), Map.of(), initial);
    }

    public Wallet add(Transaction operation) {
        List<Transaction> updated = new ArrayList<>(transactions);
        updated.add(operation);
        return new Wallet(owner, updated, budgets, categories);
    }

    public Wallet withBudget(String category, BigDecimal limit) {
        Map<String, BigDecimal> updated = new LinkedHashMap<>(budgets);
        updated.put(category, limit);
        return new Wallet(owner, transactions, updated, categories);
    }

    public Wallet withCategory(String category) {
        Set<String> updated = new LinkedHashSet<>(categories);
        updated.add(category);
        return new Wallet(owner, transactions, budgets, updated);
    }

    public Wallet withoutCategory(String category) {
        Set<String> updated = new LinkedHashSet<>(categories);
        updated.remove(category);
        return new Wallet(owner, transactions, budgets, updated);
    }

    public Wallet renameCategory(String previous, String replacement) {
        Set<String> updatedCategories = new LinkedHashSet<>(categories);
        updatedCategories.remove(previous);
        updatedCategories.add(replacement);
        Map<String, BigDecimal> updatedBudgets = new LinkedHashMap<>(budgets);
        if (updatedBudgets.containsKey(previous)) {
            BigDecimal value = updatedBudgets.remove(previous);
            updatedBudgets.put(replacement, value);
        }
        List<Transaction> updatedTransactions = new ArrayList<>();
        for (Transaction tx : transactions) {
            updatedTransactions.add(tx.category().equals(previous)
                    ? new Transaction(tx.kind(), replacement, tx.amount(), tx.date(), tx.description())
                    : tx);
        }
        return new Wallet(owner, updatedTransactions, updatedBudgets, updatedCategories);
    }
}
