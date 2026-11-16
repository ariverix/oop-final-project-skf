package ru.ariverix.finance.infra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.ariverix.finance.model.Transaction;
import ru.ariverix.finance.model.Wallet;

class JsonRepositoryTest {
    @TempDir
    Path directory;

    @Test
    void storesHashedPasswords() throws Exception {
        Path file = directory.resolve("users.json");
        JsonUserRepository users = new JsonUserRepository(file);
        users.register("alice", "secret123");
        String contents = Files.readString(file);
        assertFalse(contents.contains("secret123"));
        assertTrue(users.authenticate("alice", "secret123"));
        assertFalse(users.authenticate("alice", "bad-password"));
    }

    @Test
    void remembersUsersAfterRestart() throws Exception {
        Path file = directory.resolve("users.json");
        JsonUserRepository before = new JsonUserRepository(file);
        before.register("alice", "secret123");
        JsonUserRepository after = new JsonUserRepository(file);
        assertTrue(after.authenticate("alice", "secret123"));
    }

    @Test
    void preventsDuplicateLogins() throws Exception {
        JsonUserRepository users = new JsonUserRepository(directory.resolve("users.json"));
        users.register("alice", "secret123");
        assertThrows(IllegalArgumentException.class,
                () -> users.register("alice", "secret123"));
    }

    @Test
    void persistsWalletAndHistory() throws Exception {
        JsonWalletRepository wallets = new JsonWalletRepository(directory.resolve("wallets"));
        Wallet wallet = Wallet.empty("alice").add(new Transaction(Transaction.Kind.INCOME,
                "Зарплата", new BigDecimal("4000.00"), LocalDate.of(2026, 11, 16), ""));
        wallets.save(wallet);
        Wallet restored = new JsonWalletRepository(directory.resolve("wallets")).load("alice");
        assertEquals("alice", restored.owner());
        assertEquals(1, restored.transactions().size());
        assertEquals(new BigDecimal("4000.00"), restored.transactions().get(0).amount());
    }

    @Test
    void exportsAndImportsWallet() throws Exception {
        JsonWalletRepository wallets = new JsonWalletRepository(directory.resolve("wallets"));
        Wallet wallet = Wallet.empty("alice").withBudget("Еда", new BigDecimal("500.00"));
        Path export = directory.resolve("snapshot.json");
        wallets.exportTo(wallet, export);
        Wallet restored = wallets.importFrom(export);
        assertEquals("alice", restored.owner());
        assertEquals(new BigDecimal("500.00"), restored.budgets().get("Еда"));
    }

    @Test
    void rejectsCorruptedJson() throws Exception {
        JsonWalletRepository wallets = new JsonWalletRepository(directory.resolve("wallets"));
        Path export = directory.resolve("bad.json");
        Files.writeString(export, "{invalid}");
        assertThrows(IOException.class, () -> wallets.importFrom(export));
    }
}
