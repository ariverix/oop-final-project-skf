package ru.ariverix.finance.core;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Money {
    private Money() {
    }

    public static BigDecimal positive(String text) {
        if (text == null) {
            throw new IllegalArgumentException("Укажите сумму");
        }
        try {
            BigDecimal value = new BigDecimal(text.replace(',', '.'));
            if (value.signum() <= 0 || value.compareTo(new BigDecimal("1000000000000")) >= 0
                    || value.scale() > 2) {
                throw new IllegalArgumentException("Сумма должна быть больше нуля, до 2 знаков после запятой");
            }
            return value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Некорректная сумма: " + text, e);
        }
    }

    public static BigDecimal nonNegative(String text) {
        if (text == null) {
            throw new IllegalArgumentException("Укажите лимит");
        }
        try {
            BigDecimal value = new BigDecimal(text.replace(',', '.'));
            if (value.signum() < 0 || value.compareTo(new BigDecimal("1000000000000")) >= 0
                    || value.scale() > 2) {
                throw new IllegalArgumentException("Лимит должен быть неотрицательным, до 2 знаков");
            }
            return value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Некорректная сумма: " + text, e);
        }
    }
}
