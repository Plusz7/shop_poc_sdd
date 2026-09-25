package com.project.custom.order.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderValueObjectsTest {

    @Test
    void customerDetailsAcceptValidEmailAndName() {
        CustomerDetails customer = new CustomerDetails("jan.kowalski@example.com", "Jan Kowalski");

        assertThat(customer.email()).isEqualTo("jan.kowalski@example.com");
        assertThat(customer.fullName()).isEqualTo("Jan Kowalski");
    }

    @Test
    void customerDetailsTrimValues() {
        CustomerDetails customer = new CustomerDetails("  jan@example.com ", "  Jan Kowalski ");

        assertThat(customer.email()).isEqualTo("jan@example.com");
        assertThat(customer.fullName()).isEqualTo("Jan Kowalski");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "jan", "jan@", "@example.com", "jan@example", "jan kowalski@example.com"})
    void customerDetailsRejectInvalidEmail(String email) {
        assertThatThrownBy(() -> new CustomerDetails(email, "Jan Kowalski"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void customerDetailsAcceptEmailOfExactly254Characters() {
        String email = "a".repeat(64) + "@" + "b".repeat(185) + ".com";
        assertThat(email).hasSize(254);

        assertThatCode(() -> new CustomerDetails(email, "Jan Kowalski")).doesNotThrowAnyException();
        assertThatThrownBy(() -> new CustomerDetails("a" + email, "Jan Kowalski"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void customerDetailsRequireFullNameOf2To100Characters() {
        assertThatCode(() -> new CustomerDetails("jan@example.com", "Jo")).doesNotThrowAnyException();
        assertThatCode(() -> new CustomerDetails("jan@example.com", "J".repeat(100))).doesNotThrowAnyException();
        assertThatThrownBy(() -> new CustomerDetails("jan@example.com", "J"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CustomerDetails("jan@example.com", "J".repeat(101)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CustomerDetails("jan@example.com", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shippingAddressInPolandIsValid() {
        ShippingAddress address = ShippingAddress.inPoland("ul. Marszałkowska 1", "00-001", "Warszawa");

        assertThat(address.streetAndNumber()).isEqualTo("ul. Marszałkowska 1");
        assertThat(address.postalCode()).isEqualTo("00-001");
        assertThat(address.city()).isEqualTo("Warszawa");
        assertThat(address.country()).isEqualTo("PL");
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345", "1-2345", "123-45", "ab-cde", "00-0011", ""})
    void shippingAddressRejectsInvalidPostalCode(String postalCode) {
        assertThatThrownBy(() -> ShippingAddress.inPoland("ul. Długa 5", postalCode, "Gdańsk"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shippingAddressLimitsStreetAndCityLength() {
        assertThatCode(() -> ShippingAddress.inPoland("A 1", "00-001", "Ło")).doesNotThrowAnyException();
        assertThatCode(() -> ShippingAddress.inPoland("A".repeat(120), "00-001", "C".repeat(60)))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> ShippingAddress.inPoland("A1", "00-001", "Łódź"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ShippingAddress.inPoland("A".repeat(121), "00-001", "Łódź"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ShippingAddress.inPoland("ul. Długa 5", "00-001", "Ł"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ShippingAddress.inPoland("ul. Długa 5", "00-001", "C".repeat(61)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shippingAddressAcceptsOnlyPoland() {
        assertThatThrownBy(() -> new ShippingAddress("ul. Długa 5", "00-001", "Gdańsk", "DE"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ORD-7K2Q9M4XTB", "ORD-0000000000", "ORD-ZZZZZZZZZZ"})
    void orderNumberAcceptsCrockfordBase32(String value) {
        assertThat(new OrderNumber(value).value()).isEqualTo(value);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "ORD-7K2Q9M4XT", "ORD-7K2Q9M4XTBB", "ord-7K2Q9M4XTB", "ORD-7K2Q9M4XTI",
            "ORD-7K2Q9M4XTL", "ORD-7K2Q9M4XTO", "ORD-7K2Q9M4XTU", "XYZ-7K2Q9M4XTB"})
    void orderNumberRejectsOtherFormats(String value) {
        assertThatThrownBy(() -> new OrderNumber(value)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void generatorProducesValidAndDistinctNumbers() {
        OrderNumberGenerator generator = new OrderNumberGenerator(new SecureRandom());
        Set<String> numbers = new HashSet<>();

        for (int i = 0; i < 1_000; i++) {
            OrderNumber number = generator.next();
            assertThat(number.value()).matches(OrderNumber.PATTERN);
            numbers.add(number.value());
        }

        assertThat(numbers).hasSize(1_000);
    }
}
