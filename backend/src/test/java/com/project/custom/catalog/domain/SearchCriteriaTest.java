package com.project.custom.catalog.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchCriteriaTest {

    @Test
    void defaultsToFirstPageOf24SortedByName() {
        SearchCriteria criteria = SearchCriteria.builder().build();

        assertThat(criteria.page()).isZero();
        assertThat(criteria.size()).isEqualTo(24);
        assertThat(criteria.sort()).isEqualTo(ProductSort.NAME);
        assertThat(criteria.query()).isEmpty();
        assertThat(criteria.likePattern()).isEmpty();
        assertThat(criteria.categorySlug()).isEmpty();
        assertThat(criteria.minPriceMinor()).isEmpty();
        assertThat(criteria.maxPriceMinor()).isEmpty();
    }

    @Test
    void trimsTheQuery() {
        SearchCriteria criteria = SearchCriteria.builder().query("  lodz  ").build();

        assertThat(criteria.query()).contains("lodz");
        assertThat(criteria.likePattern()).contains("%lodz%");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t"})
    void blankQueryMeansNoQuery(String query) {
        assertThat(SearchCriteria.builder().query(query).build().query()).isEmpty();
    }

    @Test
    void truncatesTheQueryTo100Characters() {
        String longQuery = "a".repeat(150);

        SearchCriteria criteria = SearchCriteria.builder().query(longQuery).build();

        assertThat(criteria.query()).hasValueSatisfying(query -> assertThat(query).hasSize(100));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "100%     | %100\\%%",
            "a_b      | %a\\_b%",
            "[abc]    | %\\[abc]%",
            "back\\sl | %back\\\\sl%",
            "%_[zzz   | %\\%\\_\\[zzz%"
    })
    void escapesLikeWildcardsWithBackslash(String query, String expectedPattern) {
        assertThat(SearchCriteria.builder().query(query).build().likePattern()).contains(expectedPattern);
    }

    @Test
    void swapsPricesWhenMinIsGreaterThanMax() {
        SearchCriteria criteria = SearchCriteria.builder().minPriceMinor(20000L).maxPriceMinor(5000L).build();

        assertThat(criteria.minPriceMinor()).contains(5000L);
        assertThat(criteria.maxPriceMinor()).contains(20000L);
    }

    @Test
    void keepsOrderedPriceRange() {
        SearchCriteria criteria = SearchCriteria.builder().minPriceMinor(5000L).maxPriceMinor(20000L).build();

        assertThat(criteria.minPriceMinor()).contains(5000L);
        assertThat(criteria.maxPriceMinor()).contains(20000L);
    }

    @Test
    void rejectsNegativePrices() {
        assertThatThrownBy(() -> SearchCriteria.builder().minPriceMinor(-1L).build())
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SearchCriteria.builder().maxPriceMinor(-1L).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativePage() {
        assertThatThrownBy(() -> SearchCriteria.builder().page(-1).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 49, 100})
    void rejectsSizeOutsideOneTo48(int size) {
        assertThatThrownBy(() -> SearchCriteria.builder().size(size).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 24, 48})
    void acceptsSizeWithinOneTo48(int size) {
        assertThat(SearchCriteria.builder().size(size).build().size()).isEqualTo(size);
    }

    @Test
    void sortingComesFromTheAllowList() {
        assertThat(ProductSort.values())
                .containsExactlyInAnyOrder(ProductSort.PRICE_ASC, ProductSort.PRICE_DESC, ProductSort.NAME);
        assertThat(SearchCriteria.builder().sort(ProductSort.PRICE_DESC).build().sort())
                .isEqualTo(ProductSort.PRICE_DESC);
    }

    @Test
    void blankCategoryMeansAllCategories() {
        assertThat(SearchCriteria.builder().categorySlug(" ").build().categorySlug()).isEmpty();
        assertThat(SearchCriteria.builder().categorySlug("kitchen").build().categorySlug()).contains("kitchen");
    }

    @Test
    void offsetIsPageTimesSize() {
        assertThat(SearchCriteria.builder().page(2).size(24).build().offset()).isEqualTo(48);
    }
}
