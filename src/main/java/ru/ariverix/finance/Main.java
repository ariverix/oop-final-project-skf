package ru.ariverix.finance;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Scanner;
import ru.ariverix.finance.cli.FinanceConsole;
import ru.ariverix.finance.core.FinanceService;
import ru.ariverix.finance.infra.JsonUserRepository;
import ru.ariverix.finance.infra.JsonWalletRepository;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        try {
            Path directory = Path.of("data");
            FinanceService service = new FinanceService(
                    new JsonUserRepository(directory.resolve("users.json")),
                    new JsonWalletRepository(directory.resolve("wallets")),
                    Clock.systemDefaultZone());
            try (Scanner input = new Scanner(System.in)) {
                new FinanceConsole(service, input, System.out).run();
            }
        } catch (IOException | RuntimeException e) {
            System.err.println("Не удалось запустить приложение: " + e.getMessage());
            System.exit(1);
        }
    }
}
