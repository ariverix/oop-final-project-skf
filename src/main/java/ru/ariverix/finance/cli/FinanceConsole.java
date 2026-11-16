package ru.ariverix.finance.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import ru.ariverix.finance.core.FinanceService;
import ru.ariverix.finance.core.Money;
import ru.ariverix.finance.model.Report;
import ru.ariverix.finance.model.Transaction;

public final class FinanceConsole {
    private final FinanceService service;
    private final Scanner input;
    private final PrintStream out;

    public FinanceConsole(FinanceService service, Scanner input, PrintStream out) {
        this.service = service;
        this.input = input;
        this.out = out;
    }

    public void run() {
        out.println("Личные финансы. Введите help для списка команд.");
        try {
            while (true) {
                out.print("> ");
                if (!input.hasNextLine()) {
                    return;
                }
                String line = input.nextLine().trim();
                if (line.isEmpty()) {
                    continue;
                }
                try {
                    List<String> args = split(line);
                    if (args.isEmpty()) {
                        continue;
                    }
                    if (args.get(0).equalsIgnoreCase("exit")
                            || args.get(0).equalsIgnoreCase("quit")) {
                        out.println("Работа завершена.");
                        return;
                    }
                    handle(args);
                    for (String notice : service.takeNotices()) {
                        out.println("! " + notice);
                    }
                } catch (IOException | RuntimeException e) {
                    out.println("Ошибка: " + e.getMessage());
                }
            }
        } finally {
            try {
                service.logout();
            } catch (IOException e) {
                out.println("Не удалось сохранить кошелёк: " + e.getMessage());
            }
        }
    }

    private void handle(List<String> a) throws IOException {
        String command = a.get(0).toLowerCase(java.util.Locale.ROOT);
        switch (command) {
            case "help" -> help();
            case "register" -> {
                require(a, 3, "register <логин> <пароль>");
                service.register(a.get(1), a.get(2));
                service.login(a.get(1), a.get(2));
                out.println("Создан пользователь: " + service.user());
            }
            case "login" -> {
                require(a, 3, "login <логин> <пароль>");
                service.login(a.get(1), a.get(2));
                out.println("Вход выполнен: " + service.user());
            }
            case "logout" -> {
                service.logout();
                out.println("Данные сохранены.");
            }
            case "whoami" -> out.println("Текущий пользователь: " + service.user());
            case "categories" -> out.println("Категории: " + String.join(", ", service.categories()));
            case "category" -> category(a);
            case "budget" -> {
                require(a, 3, "budget <категория> <сумма>");
                service.setBudget(a.get(1), Money.nonNegative(a.get(2)));
                out.println("Бюджет обновлён.");
            }
            case "income", "expense" -> {
                require(a, 3, command + " <категория> <сумма> [описание]");
                String note = a.size() > 3 ? String.join(" ", a.subList(3, a.size())) : "";
                Transaction tx = command.equals("income")
                        ? service.income(a.get(1), Money.positive(a.get(2)), note)
                        : service.expense(a.get(1), Money.positive(a.get(2)), note);
                out.println("Добавлено: " + tx.amount() + " (" + tx.category() + ")");
                out.println("Баланс: " + service.balance());
            }
            case "balance" -> out.println("Баланс: " + service.balance());
            case "summary" -> showReport(a);
            case "history" -> {
                if (service.history().isEmpty()) {
                    out.println("Операций пока нет.");
                }
                for (Transaction tx : service.history()) {
                    out.printf("%s | %-7s | %-24s | %12s | %s%n",
                            tx.date(), tx.kind(), tx.category(), tx.amount(), tx.description());
                }
            }
            case "transfer" -> {
                require(a, 3, "transfer <логин> <сумма>");
                service.transfer(a.get(1), Money.positive(a.get(2)));
                out.println("Баланс после перевода: " + service.balance());
            }
            case "export" -> {
                require(a, 2, "export <файл.json>");
                service.exportTo(Path.of(a.get(1)));
                out.println("Файл сохранён: " + a.get(1));
            }
            case "import" -> {
                require(a, 2, "import <файл.json>");
                service.importFrom(Path.of(a.get(1)));
                out.println("Данные загружены: " + a.get(1));
            }
            default -> out.println("Неизвестная команда. Введите help.");
        }
    }

    private void category(List<String> a) {
        require(a, 3, "category add|rename|remove <имя> [новое имя]");
        switch (a.get(1)) {
            case "add" -> {
                service.addCategory(a.get(2));
                out.println("Категория добавлена.");
            }
            case "rename" -> {
                require(a, 4, "category rename <старое> <новое>");
                service.renameCategory(a.get(2), a.get(3));
                out.println("Категория переименована.");
            }
            case "remove" -> {
                service.removeCategory(a.get(2));
                out.println("Категория удалена.");
            }
            default -> out.println("Использование: category add|rename|remove ...");
        }
    }

    private void showReport(List<String> a) {
        Set<String> filter = new LinkedHashSet<>();
        if (a.size() > 1 && !a.get(1).equalsIgnoreCase("all")) {
            filter.addAll(Arrays.asList(a.get(1).split(",")));
        }
        LocalDate from = a.size() > 2 ? LocalDate.parse(a.get(2)) : null;
        LocalDate to = a.size() > 3 ? LocalDate.parse(a.get(3)) : null;
        Report report = service.report(filter, from, to);
        out.println("Доходы: " + report.income() + " | Расходы: " + report.expense()
                + " | Разница: " + report.balance());
        out.println("Доходы по категориям:");
        report.incomeByCategory().forEach((name, value) -> out.printf("  %-25s %12s%n", name, value));
        out.println("Расходы по категориям:");
        report.expenseByCategory().forEach((name, value) -> out.printf("  %-25s %12s%n", name, value));
        if (report.income().signum() == 0 && report.expense().signum() == 0) {
            out.println("За выбранный период или по выбранным категориям операций нет.");
        }
        out.println("Бюджеты за всё время:");
        report.remainingBudgets().forEach((name, remaining) -> out.printf(
                "  %-25s лимит: %12s | остаток: %12s%n",
                name, service.wallet().budgets().get(name), remaining));
    }

    private void require(List<String> args, int min, String example) {
        if (args.size() < min) {
            throw new IllegalArgumentException("Использование: " + example);
        }
    }

    public static List<String> split(String line) {
        List<String> parts = new ArrayList<>();
        StringBuilder token = new StringBuilder();
        boolean quoted = false;
        for (char c : line.toCharArray()) {
            if (c == '"') {
                quoted = !quoted;
            } else if (Character.isWhitespace(c) && !quoted) {
                if (token.length() > 0) {
                    parts.add(token.toString());
                    token.setLength(0);
                }
            } else {
                token.append(c);
            }
        }
        if (quoted) {
            throw new IllegalArgumentException("Не закрыты кавычки");
        }
        if (token.length() > 0) {
            parts.add(token.toString());
        }
        return parts;
    }

    private void help() {
        out.println("register <login> <password>            регистрация и вход");
        out.println("login <login> <password>               вход");
        out.println("logout, whoami                         выйти / текущий пользователь");
        out.println("categories, category add <имя>         категории");
        out.println("category rename <старое> <новое>       переименовать категорию");
        out.println("category remove <имя>                  удалить неиспользуемую категорию");
        out.println("budget <категория> <сумма>             установить лимит");
        out.println("income <категория> <сумма> [описание]   добавить доход");
        out.println("expense <категория> <сумма> [описание]  добавить расход");
        out.println("balance, history                       баланс / история");
        out.println("summary [all|кат1,кат2] [с] [по]       статистика, даты YYYY-MM-DD");
        out.println("transfer <логин> <сумма>               перевод");
        out.println("export <файл.json>, import <файл.json>  сохранение/загрузка");
        out.println("help, exit                             справка / выход");
        out.println("Названия с пробелами — в кавычках, например: expense \"Коммунальные услуги\" 3000");
    }
}
