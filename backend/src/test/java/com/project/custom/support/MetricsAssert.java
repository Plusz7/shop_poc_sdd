package com.project.custom.support;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Reads metrics of the application {@link MeterRegistry} by name and labels (research R-34): the value of a
 * counter or gauge, the number of recordings of a timer. A metric that does not exist reads as {@code 0}.
 * {@link #delta(ThrowingRunnable)} measures what an action changed, so tests do not depend on each other.
 */
public final class MetricsAssert {

    private static final String SUM_SUFFIX = "#sum";

    private final MeterRegistry registry;

    public MetricsAssert(MeterRegistry registry) {
        this.registry = registry;
    }

    /**
     * @param tags label name and value pairs, e.g. {@code "status", "PAID"}
     */
    public double value(String name, String... tags) {
        return snapshot().getOrDefault(key(name, tags), 0.0);
    }

    /** Sum of the recorded durations of a timer, in seconds. */
    public double totalSeconds(String name, String... tags) {
        return snapshot().getOrDefault(key(name, tags) + SUM_SUFFIX, 0.0);
    }

    /** Runs the action and returns the changes of all metrics it caused. */
    public Delta delta(ThrowingRunnable action) throws Exception {
        Map<String, Double> before = snapshot();
        action.run();
        return new Delta(before, snapshot());
    }

    private Map<String, Double> snapshot() {
        Map<String, Double> values = new HashMap<>();
        for (Meter meter : registry.getMeters()) {
            String key = key(meter.getId());
            switch (meter) {
                case Counter counter -> values.put(key, counter.count());
                case FunctionCounter counter -> values.put(key, counter.count());
                case Timer timer -> {
                    values.put(key, (double) timer.count());
                    values.put(key + SUM_SUFFIX, timer.totalTime(TimeUnit.SECONDS));
                }
                case Gauge gauge -> values.put(key, gauge.value());
                default -> {
                }
            }
        }
        return values;
    }

    private static String key(Meter.Id id) {
        return id.getName() + id.getTags().stream()
                .filter(tag -> !"application".equals(tag.getKey()))
                .sorted()
                .map(tag -> tag.getKey() + "=" + tag.getValue())
                .collect(Collectors.joining(",", "{", "}"));
    }

    private static String key(String name, String... tags) {
        if (tags.length % 2 != 0) {
            throw new IllegalArgumentException("Tags must be name and value pairs");
        }
        Tag[] pairs = new Tag[tags.length / 2];
        for (int i = 0; i < tags.length; i += 2) {
            pairs[i / 2] = Tag.of(tags[i], tags[i + 1]);
        }
        return key(new Meter.Id(name, Tags.of(pairs), null, null, Meter.Type.OTHER));
    }

    /** Changes of metrics between two moments; a metric missing in either reads as {@code 0}. */
    public static final class Delta {

        private final Map<String, Double> before;
        private final Map<String, Double> after;

        private Delta(Map<String, Double> before, Map<String, Double> after) {
            this.before = before;
            this.after = after;
        }

        public double of(String name, String... tags) {
            String key = key(name, tags);
            return after.getOrDefault(key, 0.0) - before.getOrDefault(key, 0.0);
        }

        /** Change of the sum of the recorded durations of a timer, in seconds. */
        public double totalSecondsOf(String name, String... tags) {
            String key = key(name, tags) + SUM_SUFFIX;
            return after.getOrDefault(key, 0.0) - before.getOrDefault(key, 0.0);
        }
    }

    @FunctionalInterface
    public interface ThrowingRunnable {

        void run() throws Exception;
    }
}
