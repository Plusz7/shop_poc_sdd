package com.project.custom.payment.infrastructure.stripe;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.diagnostics.FailureAnalysis;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;

class StripePropertiesValidationTest {

    private static final String LIVE_KEY = "sk_" + "live_51AbCdEfGhIjKlMnOpQrStUv"; // fake key, split so secret scanners do not flag it
    private static final String BAD_WEBHOOK_SECRET = "wh_4bCdEfGhIjKlMnOpQrStUv";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(StripeConfig.class);

    @Test
    void startsWithTestKeys() {
        runner.withPropertyValues(
                        "shop.stripe.secret-key=sk_test_dummy",
                        "shop.stripe.webhook-secret=whsec_dummy",
                        "shop.stripe.connect-timeout=5s",
                        "shop.stripe.read-timeout=10s")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    StripeProperties properties = context.getBean(StripeProperties.class);
                    assertThat(properties.apiBase()).hasToString("https://api.stripe.com");
                    assertThat(properties.toString()).doesNotContain("dummy");
                });
    }

    @Test
    void missingSecretKeyBlocksStartup() {
        runner.withPropertyValues(
                        "shop.stripe.webhook-secret=whsec_dummy",
                        "shop.stripe.connect-timeout=5s",
                        "shop.stripe.read-timeout=10s")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(rootCauseMessage(context.getStartupFailure()))
                            .isEqualTo("Missing required configuration: shop.stripe.secret-key");
                });
    }

    @Test
    void liveKeyBlocksStartupWithoutRevealingTheValue() {
        runner.withPropertyValues(
                        "shop.stripe.secret-key=" + LIVE_KEY,
                        "shop.stripe.webhook-secret=whsec_dummy",
                        "shop.stripe.connect-timeout=5s",
                        "shop.stripe.read-timeout=10s")
                .run(context -> {
                    assertThat(context).hasFailed();
                    Throwable failure = context.getStartupFailure();
                    assertThat(rootCauseMessage(failure))
                            .isEqualTo("shop.stripe.secret-key must be a test key (sk_test_…)");
                    assertThat(stackTrace(failure)).doesNotContain(LIVE_KEY);
                    assertThat(analysisText(failure)).doesNotContain(LIVE_KEY);
                });
    }

    @Test
    void webhookSecretWithoutPrefixBlocksStartupWithoutRevealingTheValue() {
        runner.withPropertyValues(
                        "shop.stripe.secret-key=sk_test_dummy",
                        "shop.stripe.webhook-secret=" + BAD_WEBHOOK_SECRET,
                        "shop.stripe.connect-timeout=5s",
                        "shop.stripe.read-timeout=10s")
                .run(context -> {
                    assertThat(context).hasFailed();
                    Throwable failure = context.getStartupFailure();
                    assertThat(rootCauseMessage(failure)).contains("shop.stripe.webhook-secret");
                    assertThat(stackTrace(failure)).doesNotContain(BAD_WEBHOOK_SECRET);
                    assertThat(analysisText(failure)).doesNotContain(BAD_WEBHOOK_SECRET);
                });
    }

    @Test
    void nonPositiveTimeoutBlocksStartup() {
        runner.withPropertyValues(
                        "shop.stripe.secret-key=sk_test_dummy",
                        "shop.stripe.webhook-secret=whsec_dummy",
                        "shop.stripe.connect-timeout=0s",
                        "shop.stripe.read-timeout=10s")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(rootCauseMessage(context.getStartupFailure()))
                            .contains("shop.stripe.connect-timeout");
                });
    }

    @Test
    void realApplicationYamlWithoutEnvironmentVariableReportsMissingKey() {
        runner.withInitializer(new ConfigDataApplicationContextInitializer())
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(rootCauseMessage(context.getStartupFailure()))
                            .isEqualTo("Missing required configuration: shop.stripe.secret-key");
                });
    }

    @Test
    void failureAnalyzerReportsOnlyPropertyNameAndCause() {
        runner.withPropertyValues(
                        "shop.stripe.secret-key=" + LIVE_KEY,
                        "shop.stripe.webhook-secret=whsec_dummy",
                        "shop.stripe.connect-timeout=5s",
                        "shop.stripe.read-timeout=10s")
                .run(context -> {
                    FailureAnalysis analysis = new StripePropertiesFailureAnalyzer()
                            .analyze(context.getStartupFailure());
                    assertThat(analysis).isNotNull();
                    assertThat(analysis.getDescription())
                            .contains("shop.stripe")
                            .contains("must be a test key")
                            .doesNotContain(LIVE_KEY);
                    assertThat(analysis.getAction()).doesNotContain(LIVE_KEY);
                });
    }

    private static String rootCauseMessage(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage();
    }

    private static String stackTrace(Throwable failure) {
        StringWriter writer = new StringWriter();
        failure.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }

    private static String analysisText(Throwable failure) {
        FailureAnalysis analysis = new StripePropertiesFailureAnalyzer().analyze(failure);
        return analysis == null ? "" : analysis.getDescription() + analysis.getAction();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(StripeProperties.class)
    static class StripeConfig {
    }
}
