package ru.ariverix.finance.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Scanner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.ariverix.finance.core.FinanceService;
import ru.ariverix.finance.infra.JsonUserRepository;
import ru.ariverix.finance.infra.JsonWalletRepository;

class FinanceConsoleTest {
    @TempDir
    Path directory;

    @Test
    void keepsQuotedCategoryNames() {
        assertEquals(List.of("expense", "Коммунальные услуги", "3000"),
                FinanceConsole.split("expense \"Коммунальные услуги\" 3000"));
    }

    @Test
    void acceptsCommandsAndSavesOnExit() throws Exception {
        FinanceService service = new FinanceService(
                new JsonUserRepository(directory.resolve("users.json")),
                new JsonWalletRepository(directory.resolve("wallets")),
                Clock.systemUTC());
        String commands = "register alice secret123\nincome Зарплата 20000\n"
                + "budget Еда 1000\nexpense Еда 200\nsummary\nlogout\nlogin alice secret123\n"
                + "balance\nexit\n";
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (Scanner input = new Scanner(commands);
                PrintStream printer = new PrintStream(output, true, StandardCharsets.UTF_8)) {
            new FinanceConsole(service, input, printer).run();
        }
        String text = output.toString(StandardCharsets.UTF_8);
        assertTrue(text.contains("Доходы: 20000"));
        assertTrue(text.contains("Баланс: 19800"));
        assertTrue(text.contains("Работа завершена."));
    }
}
