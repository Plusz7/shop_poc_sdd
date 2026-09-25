package com.project.custom.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.mssqlserver.MSSQLServerContainer;

import java.util.List;

/**
 * Base class of BC integration tests: full Spring context, MockMvc and a real SQL Server started once
 * per test run (Testcontainers, Principle VI). Data written by use cases is removed before every test;
 * the catalog seed (db/seed) stays, and products created by tests are removed through
 * {@link #TEST_FIXTURE_CATEGORY}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(IntegrationTest.ContainersConfig.class)
public abstract class IntegrationTest {

    /** Category slug holding products created by tests; they are deleted before every test. */
    public static final String TEST_FIXTURE_CATEGORY = "test-fixtures";

    /** Tables emptied before every test, children first. Tables not created yet are skipped. */
    private static final List<String> TRANSACTIONAL_TABLES = List.of(
            "outbox_event", "processed_stripe_event", "payment", "order_line", "orders", "cart_line", "cart");

    private static final MSSQLServerContainer SQL_SERVER =
            new MSSQLServerContainer("mcr.microsoft.com/mssql/server:2022-latest").acceptLicense();

    /** The Stripe API seen by the application; reset to "everything succeeds" before every test. */
    protected static final StripeStub STRIPE = new StripeStub();

    static {
        SQL_SERVER.start();
    }

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected PaymentGatewayProbe paymentGatewayProbe;

    @DynamicPropertySource
    static void stripeApiBase(DynamicPropertyRegistry registry) {
        registry.add("shop.stripe.api-base", STRIPE::baseUrl);
    }

    @BeforeEach
    void resetStripe() {
        STRIPE.reset();
        paymentGatewayProbe.reset();
    }

    @BeforeEach
    void cleanDatabase() {
        for (String table : TRANSACTIONAL_TABLES) {
            if (tableExists(table)) {
                jdbcTemplate.update("DELETE FROM " + table);
            }
        }
        if (tableExists("product")) {
            String fixtureProducts = "SELECT p.id FROM product p JOIN category c ON c.id = p.category_id WHERE c.slug = ?";
            jdbcTemplate.update("DELETE FROM product_image WHERE product_id IN (" + fixtureProducts + ")",
                    TEST_FIXTURE_CATEGORY);
            jdbcTemplate.update("DELETE FROM product WHERE id IN (" + fixtureProducts + ")", TEST_FIXTURE_CATEGORY);
            jdbcTemplate.update("DELETE FROM category WHERE slug = ?", TEST_FIXTURE_CATEGORY);
        }
    }

    /** Creates the {@link #TEST_FIXTURE_CATEGORY} category; it and its products are removed before every test. */
    protected long createFixtureCategory() {
        return jdbcTemplate.queryForObject(
                "INSERT INTO category (name, slug, display_order) OUTPUT INSERTED.id VALUES (?, ?, ?)",
                Long.class, "Test fixtures", TEST_FIXTURE_CATEGORY, 999);
    }

    /** Creates a product with the main image {@code /images/test-<id>.svg} whose alt text is the product name. */
    protected long createFixtureProduct(long categoryId, String name, long priceMinor, int stock, boolean active) {
        long id = jdbcTemplate.queryForObject("""
                        INSERT INTO product (name, description, price_minor, category_id, stock, active, version)
                        OUTPUT INSERTED.id VALUES (?, ?, ?, ?, ?, ?, 0)""",
                Long.class, name, "Opis: " + name, priceMinor, categoryId, stock, active);
        jdbcTemplate.update("INSERT INTO product_image (product_id, display_order, url, alt) VALUES (?, 0, ?, ?)",
                id, "/images/test-" + id + ".svg", name);
        return id;
    }

    private boolean tableExists(String table) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = ?", Integer.class, table);
        return count != null && count > 0;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ContainersConfig {

        @Bean
        @ServiceConnection
        MSSQLServerContainer sqlServer() {
            return SQL_SERVER;
        }

        @Bean
        static PaymentGatewayProbe paymentGatewayProbe() {
            return new PaymentGatewayProbe();
        }
    }
}
