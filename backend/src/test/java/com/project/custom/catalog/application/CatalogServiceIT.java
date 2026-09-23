package com.project.custom.catalog.application;

import com.project.custom.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * US1 (FR-001–FR-005): categories, product search and the product page against a real SQL Server with
 * the Polish CI_AI collation. Deterministic assertions run on products of the {@code test-fixtures}
 * category; seed-wide assertions only check what the seed guarantees.
 */
class CatalogServiceIT extends IntegrationTest {

    private static final String FIXTURES = TEST_FIXTURE_CATEGORY;

    @Autowired
    private MockMvc mockMvc;

    private long lodzMug;
    private long setA;
    private long setB;
    private long setC;
    private long hidden;

    @BeforeEach
    void createFixtureProducts() {
        long category = jdbcTemplate.queryForObject(
                "INSERT INTO category (name, slug, display_order) OUTPUT INSERTED.id VALUES (?, ?, ?)",
                Long.class, "Test fixtures", FIXTURES, 999);
        lodzMug = product(category, "Łódź kubek testowy", 5_000, 10, true);
        setA = product(category, "Zestaw testowy A", 15_000, 2, true);
        setB = product(category, "Zestaw testowy B", 15_000, 5, true);
        setC = product(category, "Zestaw testowy C", 25_000, 0, true);
        hidden = product(category, "Ukryty produkt testowy", 9_000, 5, false);
        jdbcTemplate.update("INSERT INTO product_image (product_id, display_order, url, alt) VALUES (?, 1, ?, ?)",
                setA, "/images/test-second.svg", "Second image");
    }

    @Test
    void listsCategoriesInDisplayOrder() throws Exception {
        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(10)))
                .andExpect(jsonPath("$[0].slug").isString())
                .andExpect(jsonPath("$[0].name").isString())
                .andExpect(jsonPath("$[-1].slug").value(FIXTURES))
                .andExpect(jsonPath("$[-1].name").value("Test fixtures"));
    }

    @Test
    void defaultListShowsFirst24ActiveProductsOfTheSeed() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products", hasSize(24)))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(24))
                .andExpect(jsonPath("$.totalElements").value(greaterThanOrEqualTo(500)))
                .andExpect(jsonPath("$.totalPages").value(greaterThanOrEqualTo(21)))
                .andExpect(jsonPath("$.products[*].image.url", everyItem(startsWith("/images/"))));
    }

    @Test
    void categoryFilterReturnsOnlyActiveProductsWithTheirMainImage() throws Exception {
        mockMvc.perform(get("/api/products").param("category", FIXTURES))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.products[*].id", contains(
                        (int) lodzMug, (int) setA, (int) setB, (int) setC)))
                .andExpect(jsonPath("$.products[1].image.url").value("/images/test-" + setA + ".svg"))
                .andExpect(jsonPath("$.products[1].image.alt").value("Zestaw testowy A"))
                .andExpect(jsonPath("$.products[1].priceMinor").value(15_000))
                .andExpect(jsonPath("$.products[1].status").value("LOW_STOCK"))
                .andExpect(jsonPath("$.products[1].maxAddable").value(2))
                .andExpect(jsonPath("$.products[3].status").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.products[3].maxAddable").value(0))
                .andExpect(jsonPath("$.products[0].status").value("AVAILABLE"));
    }

    @Test
    void unknownCategoryGivesAnEmptyPage() throws Exception {
        mockMvc.perform(get("/api/products").param("category", "no-such-category"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    @Test
    void searchIgnoresLetterCaseAndPolishDiacritics() throws Exception {
        mockMvc.perform(get("/api/products").param("q", "LODZ").param("size", "48"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products[*].name", hasItem("Łódź kubek testowy")));

        mockMvc.perform(get("/api/products").param("q", "łÓdŹ").param("category", FIXTURES))
                .andExpect(jsonPath("$.products[*].id", contains((int) lodzMug)));
    }

    @Test
    void searchNeverShowsInactiveProducts() throws Exception {
        mockMvc.perform(get("/api/products").param("q", "Ukryty produkt testowy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void likeWildcardsInTheQueryAreTakenLiterally() throws Exception {
        mockMvc.perform(get("/api/products").param("q", "%_[zzz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(get("/api/products").param("q", "%"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void filtersByPriceRangeInGrosze() throws Exception {
        mockMvc.perform(get("/api/products").param("category", FIXTURES)
                        .param("minPrice", "10000").param("maxPrice", "20000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products[*].id", contains((int) setA, (int) setB)));

        mockMvc.perform(get("/api/products").param("category", FIXTURES)
                        .param("minPrice", "20000").param("maxPrice", "10000"))
                .andExpect(jsonPath("$.products[*].id", contains((int) setA, (int) setB)));
    }

    @Test
    void sortsByAllowListWithIdAsStableSecondaryKey() throws Exception {
        mockMvc.perform(get("/api/products").param("category", FIXTURES).param("sort", "price_desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products[*].id", contains((int) setC, (int) setA, (int) setB, (int) lodzMug)));

        mockMvc.perform(get("/api/products").param("category", FIXTURES).param("sort", "price_asc"))
                .andExpect(jsonPath("$.products[*].id", contains((int) lodzMug, (int) setA, (int) setB, (int) setC)));

        mockMvc.perform(get("/api/products").param("category", FIXTURES).param("sort", "name_asc"))
                .andExpect(jsonPath("$.products[*].id", contains((int) lodzMug, (int) setA, (int) setB, (int) setC)));
    }

    @Test
    void paginatesWithPageAndSize() throws Exception {
        mockMvc.perform(get("/api/products").param("category", FIXTURES).param("sort", "price_asc")
                        .param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products[*].id", contains((int) setB, (int) setC)))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void rejectsInvalidListParameters() throws Exception {
        for (String[] parameter : new String[][]{
                {"size", "49"}, {"size", "0"}, {"page", "-1"}, {"sort", "xyz"}, {"minPrice", "-5"},
                {"page", "abc"}, {"category", "Not A Slug"}}) {
            mockMvc.perform(get("/api/products").param(parameter[0], parameter[1]))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.errors[0].field").value(parameter[0]));
        }
    }

    @Test
    void productPageShowsDetailsWithAllImagesAndCategory() throws Exception {
        mockMvc.perform(get("/api/products/{id}", setA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(setA))
                .andExpect(jsonPath("$.name").value("Zestaw testowy A"))
                .andExpect(jsonPath("$.description").value("Opis: Zestaw testowy A"))
                .andExpect(jsonPath("$.priceMinor").value(15_000))
                .andExpect(jsonPath("$.status").value("LOW_STOCK"))
                .andExpect(jsonPath("$.maxAddable").value(2))
                .andExpect(jsonPath("$.images[*].url", contains("/images/test-" + setA + ".svg", "/images/test-second.svg")))
                .andExpect(jsonPath("$.category.slug").value(FIXTURES))
                .andExpect(jsonPath("$.category.name").value("Test fixtures"));
    }

    @Test
    void productPageOfInactiveOrMissingProductIsNotFound() throws Exception {
        for (long id : new long[]{hidden, 987_654_321L}) {
            mockMvc.perform(get("/api/products/{id}", id))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        }
        mockMvc.perform(get("/api/products/{id}", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void catalogResponsesDoNotIssueTheGuestCookie() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    private long product(long categoryId, String name, long priceMinor, int stock, boolean active) {
        long id = jdbcTemplate.queryForObject("""
                        INSERT INTO product (name, description, price_minor, category_id, stock, active, version)
                        OUTPUT INSERTED.id VALUES (?, ?, ?, ?, ?, ?, 0)""",
                Long.class, name, "Opis: " + name, priceMinor, categoryId, stock, active);
        jdbcTemplate.update("INSERT INTO product_image (product_id, display_order, url, alt) VALUES (?, 0, ?, ?)",
                id, "/images/test-" + id + ".svg", name);
        return id;
    }
}
