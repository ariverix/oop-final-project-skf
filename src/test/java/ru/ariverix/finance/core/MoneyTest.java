package ru.ariverix.finance.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {
    @Test
    void acceptsCommaDecimalSeparator() {
        assertEquals(new BigDecimal("12.50"), Money.positive("12,50"));
    }

    @Test
    void rejectsZeroIncome() {
        assertThrows(IllegalArgumentException.class, () -> Money.positive("0"));
    }

    @Test
    void rejectsExcessivePrecision() {
        assertThrows(IllegalArgumentException.class, () -> Money.positive("10.123"));
    }

    @Test
    void rejectsNonNumericAmount() {
        assertThrows(IllegalArgumentException.class, () -> Money.positive("twelve"));
    }

    @Test
    void allowsZeroBudget() {
        assertEquals(new BigDecimal("0.00"), Money.nonNegative("0"));
    }
}
