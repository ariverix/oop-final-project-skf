package ru.ariverix.finance.core;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import ru.ariverix.finance.model.Report;
import ru.ariverix.finance.model.Transaction;
import ru.ariverix.finance.model.Wallet;

public final class FinanceService {
    private static final BigDecimal EIGHTY_PERCENT = new BigDecimal("0.80");
    private final UserRepository users;
    private final WalletRepository store;
    private final Clock clock;
    private final List<String> notices = new ArrayList<>();
    private Wallet current;

    public FinanceService(UserRepository users, WalletRepository store, Clock clock) {
        this.users = users;
        this.store = store;
        this.clock = clock;
    }

    public void register(String login, String password) throws IOException {
        users.register(login, password);
        store.save(Wallet.empty(login));
    }

    public void login(String login, String password) throws IOException {
        if (!users.authenticate(login, password)) {
            throw new SecurityException("Неверный логин или пароль");
        }
        logout();
        Wallet loaded = store.load(login);
        current = loaded == null ? Wallet.empty(login) : loaded;
        notices.clear();
    }

    public void logout() throws IOException {
        if (current != null) {
            store.save(current);
            current = null;
        }
        notices.clear();
    }

    public String user() {
        return wallet().owner();
    }

    public Wallet wallet() {
        if (current == null) {
            throw new IllegalStateException("Сначала выполните login");
        }
        return current;
    }

    public List<String> takeNotices() {
        List<String> result = List.copyOf(notices);
        notices.clear();
        return result;
    }

    public Set<String> categories() {
        return wallet().categories();
    }

    public void addCategory(String category) {
        String name = checkCategoryName(category);
        if (wallet().categories().contains(name)) {
            throw new IllegalArgumentException("Такая категория уже существует");
        }
        current = wallet().withCategory(name);
    }

    public void renameCategory(String existing, String replacement) {
        if ("Переводы".equals(existing)) {
            throw new IllegalArgumentException("Системную категорию «Переводы» изменить нельзя");
        }
        String name = checkCategoryName(replacement);
        checkExisting(existing);
        if (wallet().categories().contains(name)) {
            throw new IllegalArgumentException("Такая категория уже существует");
        }
        current = wallet().renameCategory(existing, name);
    }

    public void removeCategory(String category) {
        checkExisting(category);
        if ("Переводы".equals(category) || wallet().budgets().containsKey(category)
                || wallet().transactions().stream().anyMatch(tx -> tx.category().equals(category))) {
            throw new IllegalArgumentException("Категория используется. Уберите бюджет и операции");
        }
        current = wallet().withoutCategory(category);
    }

    public void setBudget(String category, BigDecimal value) {
        checkExisting(category);
        checkBudget(value);
        current = wallet().withBudget(category, value);
    }

    public Transaction income(String category, BigDecimal amount, String note) {
        return add(Transaction.Kind.INCOME, category, amount, note);
    }

    public Transaction expense(String category, BigDecimal amount, String note) {
        return add(Transaction.Kind.EXPENSE, category, amount, note);
    }

    private Transaction add(Transaction.Kind kind, String category, BigDecimal amount, String note) {
        checkExisting(category);
        checkAmount(amount);
        BigDecimal beforeSpent = spent(category);
        BigDecimal beforeBalance = balance();
        Transaction tx = new Transaction(kind, category, amount, LocalDate.now(clock), note);
        current = wallet().add(tx);
        if (kind == Transaction.Kind.EXPENSE) {
            warnForExpense(category, beforeSpent, beforeBalance);
        }
        return tx;
    }

    private void warnForExpense(String category, BigDecimal beforeSpent, BigDecimal beforeBalance) {
        BigDecimal spentNow = spent(category);
        BigDecimal budget = wallet().budgets().get(category);
        if (budget != null) {
            if (spentNow.compareTo(budget) > 0) {
                notices.add("Превышен бюджет «" + category + "» на "
                        + spentNow.subtract(budget).toPlainString());
            } else if (budget.signum() > 0
                    && beforeSpent.compareTo(budget.multiply(EIGHTY_PERCENT)) < 0
                    && spentNow.compareTo(budget.multiply(EIGHTY_PERCENT)) >= 0) {
                notices.add("Израсходовано 80% бюджета «" + category + "»");
            }
        }
        if (beforeBalance.signum() >= 0 && balance().signum() < 0) {
            notices.add("Расходы превысили доходы. Баланс отрицательный.");
        }
        if (balance().signum() == 0 && beforeBalance.signum() > 0) {
            notices.add("Баланс кошелька равен нулю.");
        }
    }

    public BigDecimal balance() {
        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expenses = BigDecimal.ZERO;
        for (Transaction tx : wallet().transactions()) {
            if (tx.kind() == Transaction.Kind.INCOME) {
                income = income.add(tx.amount());
            } else {
                expenses = expenses.add(tx.amount());
            }
        }
        return income.subtract(expenses);
    }

    private BigDecimal spent(String category) {
        return wallet().transactions().stream()
                .filter(tx -> tx.kind() == Transaction.Kind.EXPENSE && tx.category().equals(category))
                .map(Transaction::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public Report report(Set<String> selection, LocalDate from, LocalDate to) {
        Wallet wallet = wallet();
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("Начальная дата больше конечной");
        }
        Set<String> categories = selection == null || selection.isEmpty()
                ? wallet.categories() : selection;
        for (String category : categories) {
            checkExisting(category);
        }
        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expense = BigDecimal.ZERO;
        Map<String, BigDecimal> incomeBy = new LinkedHashMap<>();
        Map<String, BigDecimal> expenseBy = new LinkedHashMap<>();
        for (Transaction tx : wallet.transactions()) {
            if (!categories.contains(tx.category())
                    || (from != null && tx.date().isBefore(from))
                    || (to != null && tx.date().isAfter(to))) {
                continue;
            }
            if (tx.kind() == Transaction.Kind.INCOME) {
                income = income.add(tx.amount());
                incomeBy.merge(tx.category(), tx.amount(), BigDecimal::add);
            } else {
                expense = expense.add(tx.amount());
                expenseBy.merge(tx.category(), tx.amount(), BigDecimal::add);
            }
        }
        Map<String, BigDecimal> remaining = new LinkedHashMap<>();
        for (var entry : wallet.budgets().entrySet()) {
            if (!categories.contains(entry.getKey())) {
                continue;
            }
            BigDecimal used = wallet.transactions().stream()
                    .filter(tx -> tx.kind() == Transaction.Kind.EXPENSE
                            && tx.category().equals(entry.getKey()))
                    .map(Transaction::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
            remaining.put(entry.getKey(), entry.getValue().subtract(used));
        }
        return new Report(income, expense, incomeBy, expenseBy, remaining);
    }

    public void transfer(String recipient, BigDecimal amount) throws IOException {
        if (recipient == null || recipient.isBlank() || !users.exists(recipient)) {
            throw new IllegalArgumentException("Получатель не найден");
        }
        if (recipient.equals(user())) {
            throw new IllegalArgumentException("Нельзя перевести деньги самому себе");
        }
        checkAmount(amount);
        if (balance().compareTo(amount) < 0) {
            throw new IllegalArgumentException("Недостаточно средств");
        }
        Wallet target = store.load(recipient);
        if (target == null) {
            throw new IOException("Не найден кошелёк получателя");
        }
        LocalDate today = LocalDate.now(clock);
        Wallet nextTarget = target.add(new Transaction(Transaction.Kind.INCOME,
                "Переводы", amount, today, "От " + user()));
        Wallet nextCurrent = wallet().add(new Transaction(Transaction.Kind.EXPENSE,
                "Переводы", amount, today, "Для " + recipient));

        // In a console application, persist transfers before reporting success.
        store.save(nextTarget);
        store.save(nextCurrent);
        current = nextCurrent;
        notices.add("Перевод пользователю " + recipient + " выполнен");
    }

    public void exportTo(Path file) throws IOException {
        store.exportTo(wallet(), file);
    }

    public void importFrom(Path file) throws IOException {
        Wallet imported = store.importFrom(file);
        if (!user().equals(imported.owner())) {
            throw new SecurityException("Импортировать можно только свой кошелёк");
        }
        if (!imported.categories().contains("Переводы")) {
            throw new IllegalArgumentException("В файле отсутствует обязательная категория");
        }
        for (Transaction tx : imported.transactions()) {
            if (!imported.categories().contains(tx.category())) {
                throw new IllegalArgumentException("В файле есть операция с неизвестной категорией");
            }
            checkAmount(tx.amount());
        }
        for (var entry : imported.budgets().entrySet()) {
            if (!imported.categories().contains(entry.getKey())) {
                throw new IllegalArgumentException("В файле есть бюджет с неизвестной категорией");
            }
            checkBudget(entry.getValue());
        }
        store.save(imported);
        current = imported;
    }

    public List<Transaction> history() {
        return wallet().transactions();
    }

    private void checkExisting(String category) {
        if (category == null || !wallet().categories().contains(category)) {
            throw new IllegalArgumentException("Категория не найдена: " + category);
        }
    }

    private static String checkCategoryName(String category) {
        if (category == null || category.isBlank() || category.length() > 40
                || category.contains("\n") || category.contains("\r")) {
            throw new IllegalArgumentException("Имя категории: от 1 до 40 символов");
        }
        return category.trim();
    }

    private static void checkAmount(BigDecimal value) {
        if (value == null || value.signum() <= 0 || value.scale() > 2
                || value.compareTo(new BigDecimal("1000000000000")) >= 0) {
            throw new IllegalArgumentException("Некорректная сумма");
        }
    }

    private static void checkBudget(BigDecimal value) {
        if (value == null || value.signum() < 0 || value.scale() > 2
                || value.compareTo(new BigDecimal("1000000000000")) >= 0) {
            throw new IllegalArgumentException("Некорректный бюджет");
        }
    }
}
