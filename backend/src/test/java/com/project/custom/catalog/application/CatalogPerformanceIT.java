package com.project.custom.catalog.application;

import com.project.custom.ShopApplication;
import com.project.custom.support.IntegrationTest;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Performance on a catalog of at least 500 products (SC-003 and research R-34, SC-012): every read of the
 * purchase path answers within 300 ms after warm-up, without N+1 queries (Hibernate statement counter), and
 * metrics cost under 5% or 5 ms of the p95 latency compared with the same application started with
 * {@code management.metrics.enable.all=false}. Both applications run in this JVM against the same database
 * and are measured in alternating batches, so that JVM and machine noise hit both equally.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CatalogPerformanceIT extends IntegrationTest {

    private static final Duration BUDGET = Duration.ofMillis(300);
    private static final double OVERHEAD_RATIO = 0.05;
    private static final double OVERHEAD_NOISE_MS = 5.0;
    private static final int WARM_UP_REQUESTS = 60;
    private static final int BATCHES = 6;
    private static final int REQUESTS_PER_BATCH = 50;
    private static final int CART_LINES = 10;
    /** A page query, a count query and the images of the page; anything above means per-row queries. */
    private static final int MAX_STATEMENTS_PER_READ = 8;

    private final HttpClient http = HttpClient.newHttpClient();
    private final String guest = UUID.randomUUID().toString();

    @LocalServerPort
    private int metricsOnPort;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private JdbcConnectionDetails database;

    @Autowired
    private Environment environment;

    private ConfigurableApplicationContext metricsOffApp;
    private int metricsOffPort;
    private Map<String, String> scenarios;

    @BeforeEach
    void prepareCatalogAndCart() throws Exception {
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM product WHERE active = 1", Integer.class))
                .as("seeded products").isGreaterThanOrEqualTo(500);
        long category = createFixtureCategory();
        for (int i = 1; i <= CART_LINES; i++) {
            long product = createFixtureProduct(category, "Perf product " + i, 1_000L * i, 100, true);
            addToCart(metricsOnPort, guest, product, i);
        }
        scenarios = new LinkedHashMap<>();
        scenarios.put("list", "/api/products?size=24");
        scenarios.put("search with a phrase", "/api/products?q=kubek");
        scenarios.put("filter and sort", "/api/products?category=kitchen&minPrice=2000&maxPrice=80000&sort=price_desc");
        scenarios.put("cart with 10 lines", "/api/cart");
    }

    @AfterEach
    void stopMetricsOffApplication() {
        if (metricsOffApp != null) {
            metricsOffApp.close();
        }
    }

    @Test
    void readsStayWithinTheBudget() throws Exception {
        for (Map.Entry<String, String> scenario : scenarios.entrySet()) {
            warmUp(metricsOnPort, scenario.getValue());
            List<Double> samples = new ArrayList<>();
            for (int i = 0; i < BATCHES * REQUESTS_PER_BATCH; i++) {
                samples.add(timeMillis(metricsOnPort, scenario.getValue()));
            }
            assertThat(percentile(samples, 95)).as("p95 of '%s' in ms".formatted(scenario.getKey()))
                    .isLessThanOrEqualTo((double) BUDGET.toMillis());
        }
    }

    @Test
    void readsDoNotRunPerRowQueries() throws Exception {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        try {
            Map<String, Long> statementsPerScenario = new LinkedHashMap<>();
            for (Map.Entry<String, String> scenario : scenarios.entrySet()) {
                statistics.clear();
                get(metricsOnPort, guest, scenario.getValue());
                statementsPerScenario.put(scenario.getKey(), statistics.getPrepareStatementCount());
            }
            statementsPerScenario.forEach((name, statements) ->
                    assertThat(statements).as("SQL statements of '%s'".formatted(name))
                            .isBetween(1L, (long) MAX_STATEMENTS_PER_READ));

            statistics.clear();
            get(metricsOnPort, guest, "/api/products?size=48");
            assertThat(statistics.getPrepareStatementCount())
                    .as("statements of a 48-product page equal those of a 24-product page")
                    .isEqualTo(statementsPerScenario.get("list"));

            String oneLineGuest = UUID.randomUUID().toString();
            long product = jdbcTemplate.queryForObject(
                    "SELECT TOP 1 id FROM product WHERE active = 1 AND stock > 50", Long.class);
            addToCart(metricsOnPort, oneLineGuest, product, 1);
            statistics.clear();
            get(metricsOnPort, oneLineGuest, "/api/cart");
            assertThat(statistics.getPrepareStatementCount())
                    .as("statements of a 1-line cart equal those of a 10-line cart")
                    .isEqualTo(statementsPerScenario.get("cart with 10 lines"));
        } finally {
            statistics.setStatisticsEnabled(false);
        }
    }

    @Test
    void metricsAddLessThanFivePercentOrFiveMillisecondsToTheP95Latency() throws Exception {
        startMetricsOffApplication();
        for (Map.Entry<String, String> scenario : scenarios.entrySet()) {
            String path = scenario.getValue();
            warmUp(metricsOnPort, path);
            warmUp(metricsOffPort, path);
            List<Double> withMetrics = new ArrayList<>();
            List<Double> withoutMetrics = new ArrayList<>();
            for (int batch = 0; batch < BATCHES; batch++) {
                for (int i = 0; i < REQUESTS_PER_BATCH; i++) {
                    withMetrics.add(timeMillis(metricsOnPort, path));
                }
                for (int i = 0; i < REQUESTS_PER_BATCH; i++) {
                    withoutMetrics.add(timeMillis(metricsOffPort, path));
                }
            }
            double on = percentile(withMetrics, 95);
            double off = percentile(withoutMetrics, 95);
            double allowed = Math.max(off * OVERHEAD_RATIO, OVERHEAD_NOISE_MS);
            assertThat(on - off).as("p95 with metrics %.2f ms, without %.2f ms, in '%s'"
                    .formatted(on, off, scenario.getKey())).isLessThan(allowed);
        }
    }

    private void startMetricsOffApplication() {
        metricsOffApp = new SpringApplicationBuilder(ShopApplication.class)
                .profiles("test")
                .run(
                        "--server.port=0",
                        "--management.server.port=0",
                        "--management.metrics.enable.all=false",
                        "--spring.datasource.url=" + database.getJdbcUrl(),
                        "--spring.datasource.username=" + database.getUsername(),
                        "--spring.datasource.password=" + database.getPassword(),
                        "--shop.stripe.api-base=" + environment.getProperty("shop.stripe.api-base"));
        metricsOffPort = Integer.parseInt(metricsOffApp.getEnvironment().getProperty("local.server.port"));
    }

    private void addToCart(int port, String guestId, long productId, int quantity) throws Exception {
        String json = "{\"productId\": %d, \"quantity\": %d}".formatted(productId, quantity);
        HttpResponse<String> response = send(HttpRequest.newBuilder(uri(port, "/api/cart/lines"))
                .header("Cookie", "shop_guest=" + guestId).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json)).build());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
    }

    private void warmUp(int port, String path) throws Exception {
        for (int i = 0; i < WARM_UP_REQUESTS; i++) {
            get(port, guest, path);
        }
    }

    private double timeMillis(int port, String path) throws Exception {
        long start = System.nanoTime();
        get(port, guest, path);
        return (System.nanoTime() - start) / 1_000_000.0;
    }

    private void get(int port, String guestId, String path) throws Exception {
        HttpResponse<String> response = send(HttpRequest.newBuilder(uri(port, path))
                .header("Cookie", "shop_guest=" + guestId).GET().build());
        assertThat(response.statusCode()).as(path).isEqualTo(200);
    }

    private HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static URI uri(int port, String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private static double percentile(List<Double> samples, int percent) {
        List<Double> sorted = samples.stream().sorted().toList();
        int index = (int) Math.ceil(percent / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(index, 0));
    }
}
