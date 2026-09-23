package com.project.custom.shared.domain;

import org.junit.jupiter.api.Test;

import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    void createsPlnAmountInMinorUnits() {
        Money money = Money.pln(12_345);

        assertThat(money.minor()).isEqualTo(12_345);
        assertThat(money.currency()).isEqualTo(Currency.getInstance("PLN"));
    }

    @Test
    void zeroIsAllowed() {
        assertThat(Money.pln(0).minor()).isZero();
        assertThat(Money.ZERO).isEqualTo(Money.pln(0));
    }

    @Test
    void rejectsNegativeAmount() {
        assertThatThrownBy(() -> Money.pln(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsCurrencyOtherThanPln() {
        assertThatThrownBy(() -> new Money(100, Currency.getInstance("EUR")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void addsAmounts() {
        assertThat(Money.pln(1_050).plus(Money.pln(2_000))).isEqualTo(Money.pln(3_050));
    }

    @Test
    void multipliesByQuantity() {
        assertThat(Money.pln(1_999).times(3)).isEqualTo(Money.pln(5_997));
        assertThat(Money.pln(1_999).times(0)).isEqualTo(Money.ZERO);
    }

    @Test
    void rejectsNegativeMultiplier() {
        assertThatThrownBy(() -> Money.pln(100).times(-2)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsOverflow() {
        assertThatThrownBy(() -> Money.pln(Long.MAX_VALUE).plus(Money.pln(1)))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    void isEqualByValue() {
        assertThat(Money.pln(500)).isEqualTo(Money.pln(500)).hasSameHashCodeAs(Money.pln(500));
        assertThat(Money.pln(500)).isNotEqualTo(Money.pln(501));
    }
}
