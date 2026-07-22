package com.huawei.clouds.openrewrite.kafka;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.properties.PropertiesIsoVisitor;
import org.openrewrite.properties.tree.Properties;

import java.util.Set;

/** Marks Kafka 4.1 configuration changes that require operational intent. */
public final class FindKafkaClientPropertiesRisks extends Recipe {
    private static final Set<String> OAUTH_URL_KEYS = Set.of(
            "sasl.oauthbearer.token.endpoint.url", "sasl.oauthbearer.jwks.endpoint.url"
    );
    private static final Set<String> REMOVED_METRICS = Set.of(
            "bufferpool-wait-time-total", "io-waittime-total", "iotime-total"
    );

    @Override
    public String getDisplayName() {
        return "Find behavior-sensitive Kafka client 4.1 properties";
    }

    @Override
    public String getDescription() {
        return "Mark OAuth URL allow-listing, idempotent producer in-flight limits, and removed metric names for review.";
    }

    @Override
    public PropertiesIsoVisitor<ExecutionContext> getVisitor() {
        return new PropertiesIsoVisitor<ExecutionContext>() {
            @Override
            public Properties.Entry visitEntry(Properties.Entry entry, ExecutionContext ctx) {
                Properties.Entry e = super.visitEntry(entry, ctx);
                String key = e.getKey();
                String value = e.getValue().getText().trim();
                if (OAUTH_URL_KEYS.contains(key)) {
                    return SearchResult.found(e, "Kafka 4.1 requires this endpoint to be allowed by the org.apache.kafka.sasl.oauthbearer.allowed.urls system property");
                }
                if ("max.in.flight.requests.per.connection".equals(key) && exceedsFive(value) && idempotenceEnabledOrDefault(e)) {
                    return SearchResult.found(e, "With idempotence enabled (including its default), Kafka 4.1 rejects max.in.flight.requests.per.connection greater than 5");
                }
                if (REMOVED_METRICS.stream().anyMatch(value::contains)) {
                    return SearchResult.found(e, "Kafka 4.1 removed a legacy metric name; use its -ns- replacement and recalculate unit-sensitive thresholds");
                }
                return e;
            }

            private boolean idempotenceEnabledOrDefault(Properties.Entry entry) {
                Properties.File file = getCursor().firstEnclosing(Properties.File.class);
                if (file == null) {
                    return true;
                }
                return file.getContent().stream().filter(Properties.Entry.class::isInstance)
                        .map(Properties.Entry.class::cast)
                        .filter(candidate -> "enable.idempotence".equals(candidate.getKey()))
                        .map(candidate -> candidate.getValue().getText().trim())
                        .noneMatch("false"::equalsIgnoreCase);
            }
        };
    }

    private static boolean exceedsFive(String value) {
        try {
            return Integer.parseInt(value) > 5;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }
}
