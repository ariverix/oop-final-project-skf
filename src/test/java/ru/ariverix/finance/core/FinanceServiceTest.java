package ru.ariverix.finance.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.ariverix.finance.model.Transaction;
import ru.ariverix.finance.model.Wallet;

class FinanceServiceTest {
    private FinanceService service;

    @BeforeEach
    void init() throws IOException {
        service = new FinanceService(new Users(), new Wallets(),
                Clock.fixed(java.time.Instant.parse("2026-11-16T10:00:00Z"), ZoneOffset.UTC));
        service.register("alice", "secret123");
        service.register("bob", "secret123");
        service.login("alice", "secret123");
    }

    private static BigDecimal amount(String amount) {
        return Money.positive(amount);
    }

    @Test
    void loginAndWalletOwner() {
        assertEquals("alice", service.user());
    }

    @Test
    void invalidPasswordRejected() {
        assertThrows(SecurityException.class, () -> service.login("bob", "badpass"));
    }

    @Test
    void separateWallets() throws Exception {
        service.income("Зарплата", amount("500"), "");
        service.login("bob", "secret123");
        assertEquals(0, service.balance().compareTo(BigDecimal.ZERO));
        service.login("alice", "secret123");
        assertEquals(0, service.balance().compareTo(amount("500")));
    }

    @Test
    void incomeAddsToBalance() {
        service.income("Зарплата", amount("20000"), "");
        assertEquals(0, service.balance().compareTo(amount("20000")));
    }

    @Test
    void expenseSubtractsFromBalance() {
        service.income("Зарплата", amount("1000"), "");
        service.expense("Еда", amount("250"), "");
        assertEquals(0, service.balance().compareTo(amount("750")));
    }

    @Test
    void rejectsUnknownCategory() {
        assertThrows(IllegalArgumentException.class,
                () -> service.expense("Несуществующая", amount("50"), ""));
    }

    @Test
    void rejectsNegativeAmount() {
        assertThrows(IllegalArgumentException.class,
                () -> service.expense("Еда", new BigDecimal("-1"), ""));
    }

    @Test
    void canCreateCategory() {
        service.addCategory("Книги");
        assertTrue(service.categories().contains("Книги"));
    }

    @Test
    void rejectsDuplicateCategory() {
        assertThrows(IllegalArgumentException.class, () -> service.addCategory("Еда"));
    }

    @Test
    void setsCategoryBudget() {
        service.setBudget("Еда", amount("4000"));
        assertEquals(0, service.report(Set.of(), null, null)
                .remainingBudgets().get("Еда").compareTo(amount("4000")));
    }

    @Test
    void calculatesRemainingCategoryBudget() {
        service.setBudget("Еда", amount("4000"));
        service.expense("Еда", amount("300"), "");
        service.expense("Еда", amount("500"), "");
        assertEquals(0, service.report(Set.of(), null, null)
                .remainingBudgets().get("Еда").compareTo(amount("3200")));
    }

    @Test
    void warnsAboutOverBudget() {
        service.setBudget("Еда", amount("100"));
        service.expense("Еда", amount("120"), "");
        assertTrue(service.takeNotices().stream().anyMatch(n -> n.contains("Превышен бюджет")));
    }

    @Test
    void warnsWhenEightyPercentUsed() {
        service.setBudget("Еда", amount("100"));
        service.expense("Еда", amount("80"), "");
        assertTrue(service.takeNotices().stream().anyMatch(n -> n.contains("80%")));
    }

    @Test
    void warnsAboutOverspendingIncome() {
        service.income("Зарплата", amount("100"), "");
        service.expense("Еда", amount("200"), "");
        assertTrue(service.takeNotices().stream().anyMatch(n -> n.contains("Расходы превысили")));
    }

    @Test
    void canRenameUsedCategory() {
        service.expense("Еда", amount("15"), "");
        service.renameCategory("Еда", "Продукты");
        assertEquals("Продукты", service.history().get(0).category());
    }

    @Test
    void canEditBudget() {
        service.setBudget("Еда", amount("200"));
        service.setBudget("Еда", amount("300"));
        assertEquals(0, service.wallet().budgets().get("Еда").compareTo(amount("300")));
    }

    @Test
    void canRemoveUnusedCategory() {
        service.addCategory("Книги");
        service.removeCategory("Книги");
        assertFalse(service.categories().contains("Книги"));
    }

    @Test
    void cannotRemoveUsedCategory() {
        service.expense("Еда", amount("10"), "");
        assertThrows(IllegalArgumentException.class, () -> service.removeCategory("Еда"));
    }

    @Test
    void reportsSeveralCategories() {
        service.expense("Еда", amount("100"), "");
        service.expense("Такси", amount("200"), "");
        service.expense("Развлечения", amount("300"), "");
        assertEquals(0, service.report(Set.of("Еда", "Такси"), null, null)
                .expense().compareTo(amount("300")));
    }

    @Test
    void reportsDateRange() {
        service.income("Зарплата", amount("100"), "");
        assertEquals(0, service.report(Set.of(), LocalDate.of(2026, 11, 15),
                LocalDate.of(2026, 11, 15)).income().compareTo(BigDecimal.ZERO));
        assertEquals(0, service.report(Set.of(), LocalDate.of(2026, 11, 16),
                LocalDate.of(2026, 11, 16)).income().compareTo(amount("100")));
    }

    @Test
    void rejectsMissingCategoryInFilter() {
        assertThrows(IllegalArgumentException.class,
                () -> service.report(Set.of("неизвестна"), null, null));
    }

    @Test
    void rejectsInvertedDates() {
        assertThrows(IllegalArgumentException.class, () -> service.report(Set.of(),
                LocalDate.of(2026, 12, 1), LocalDate.of(2026, 11, 1)));
    }

    @Test
    void transfersBetweenUsers() throws Exception {
        service.income("Зарплата", amount("1000"), "");
        service.transfer("bob", amount("300"));
        assertEquals(0, service.balance().compareTo(amount("700")));
        service.login("bob", "secret123");
        assertEquals(0, service.balance().compareTo(amount("300")));
        assertEquals(Transaction.Kind.INCOME, service.history().get(0).kind());
    }

    @Test
    void cannotTransferAboveBalance() {
        assertThrows(IllegalArgumentException.class,
                () -> service.transfer("bob", amount("100")));
    }

    @Test
    void cannotTransferToUnknownUser() {
        assertThrows(IllegalArgumentException.class,
                () -> service.transfer("nobody", amount("10")));
    }

    @Test
    void canProduceAssignmentExample() {
        service.income("Зарплата", amount("20000"), "");
        service.income("Зарплата", amount("40000"), "");
        service.income("Бонус", amount("3000"), "");
        service.expense("Еда", amount("300"), "");
        service.expense("Еда", amount("500"), "");
        service.expense("Развлечения", amount("3000"), "");
        service.expense("Коммунальные услуги", amount("3000"), "");
        service.expense("Такси", amount("1500"), "");
        var report = service.report(Set.of(), null, null);
        assertEquals(0, report.income().compareTo(amount("63000")));
        assertEquals(0, report.expense().compareTo(amount("8300")));
    }

    static final class Users implements UserRepository {
        private final Map<String, String> passwords = new HashMap<>();

        public void register(String login, String password) {
            if (passwords.putIfAbsent(login, password) != null) {
                throw new IllegalArgumentException("Логин занят");
            }
        }

        public boolean authenticate(String login, String password) {
            return password.equals(passwords.get(login));
        }

        public boolean exists(String login) {
            return passwords.containsKey(login);
        }
    }

    static final class Wallets implements WalletRepository {
        private final Map<String, Wallet> wallets = new HashMap<>();

        public Wallet load(String owner) {
            return wallets.get(owner);
        }

        public void save(Wallet wallet) {
            wallets.put(wallet.owner(), wallet);
        }

        public void exportTo(Wallet wallet, Path path) {
            wallets.put("exported", wallet);
        }

        public Wallet importFrom(Path path) {
            return wallets.get("exported");
        }
    }
}
