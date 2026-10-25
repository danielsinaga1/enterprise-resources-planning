package id.neravoxa.config;

import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.aop.ObservedAspect;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Central wiring for the observability stack.
 *
 * <ul>
 *   <li>{@link TimedAspect}    — makes {@code @Timed} on methods produce Micrometer timers.</li>
 *   <li>{@link ObservedAspect} — makes {@code @Observed} produce both a metric and a span
 *       (one annotation, two signals) via the OpenTelemetry bridge.</li>
 *   <li>A {@link MeterRegistryCustomizer} that stamps common tags (deployment env + instance)
 *       onto every meter so Grafana panels can split by environment/pod.</li>
 * </ul>
 *
 * All beans are {@code @ConditionalOnMissingBean} so they coexist with — and defer to —
 * anything Spring Boot auto-configures, avoiding duplicate-bean failures.
 */
@Configuration
public class ObservabilityConfig {

    @Value("${spring.application.name:neravoxa}")
    private String applicationName;

    @Value("${DEPLOY_ENV:dev}")
    private String deployEnvironment;

    @Value("${HOSTNAME:local}")
    private String instanceId;

    /**
     * Enables {@code @Timed} method-level timers (e.g. on heavy service methods).
     */
    @Bean
    @ConditionalOnMissingBean
    public TimedAspect timedAspect(MeterRegistry registry) {
        return new TimedAspect(registry);
    }

    /**
     * Enables {@code @Observed} so a single annotation emits a timer AND a trace span.
     */
    @Bean
    @ConditionalOnMissingBean
    public ObservedAspect observedAspect(ObservationRegistry observationRegistry) {
        return new ObservedAspect(observationRegistry);
    }

    /**
     * Common low-cardinality tags applied to every meter. {@code application} is also set in
     * application.yaml; we add deployment env + instance here. Keep this list SMALL — each tag
     * value multiplies the metric series count in Prometheus.
     */
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> commonTagsCustomizer() {
        return registry -> registry.config()
                .commonTags(
                        "application", applicationName,
                        "env", deployEnvironment,
                        "instance", instanceId)
                // Cap accidental high-cardinality URI tags (e.g. unmatched paths)
                // so a scan/404 storm can't blow up Prometheus memory.
                .meterFilter(MeterFilter.maximumAllowableTags(
                        "http.server.requests", "uri", 200, MeterFilter.deny()));
    }
}
