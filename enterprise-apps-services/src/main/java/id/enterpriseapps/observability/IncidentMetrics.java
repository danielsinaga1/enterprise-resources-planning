package id.enterpriseapps.observability;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Business metrics for the Incident Report domain.
 *
 * Exposes three of the four Micrometer instrument types so the Grafana
 * "business metrics" row has real data:
 * <ul>
 *   <li><b>Counter</b> {@code incident.report.created} — monotonic count of
 *       reports created, split by classification.</li>
 *   <li><b>Counter</b> {@code incident.report.workflow.transition} — workflow
 *       state changes, split by the target status.</li>
 *   <li><b>Timer</b> {@code incident.report.create} — latency + throughput of
 *       the create operation (histogram enabled for percentiles).</li>
 *   <li><b>Gauge</b> {@code incident.report.create.inflight} — create calls
 *       currently executing.</li>
 * </ul>
 *
 * Tags are deliberately low-cardinality (classification / status are bounded
 * lookup values) to keep the Prometheus series count under control.
 */
@Component
public class IncidentMetrics {

    public static final String CREATED = "incident.report.created";
    public static final String TRANSITION = "incident.report.workflow.transition";
    public static final String CREATE_TIMER = "incident.report.create";
    public static final String INFLIGHT = "incident.report.create.inflight";

    private final MeterRegistry registry;
    private final AtomicInteger inFlight = new AtomicInteger(0);

    public IncidentMetrics(MeterRegistry registry) {
        this.registry = registry;
        Gauge.builder(INFLIGHT, inFlight, AtomicInteger::get)
                .description("Incident report create operations currently in progress")
                .register(registry);
    }

    /** Count one successfully created report, tagged by its classification. */
    public void incrementCreated(String classification) {
        Counter.builder(CREATED)
                .description("Total incident reports successfully created")
                .baseUnit("reports")
                .tag("classification", safe(classification))
                .register(registry)
                .increment();
    }

    /** Count one workflow state change, tagged by the new status. */
    public void recordTransition(String toStatus) {
        Counter.builder(TRANSITION)
                .description("Incident report workflow state transitions")
                .tag("to_status", safe(toStatus))
                .register(registry)
                .increment();
    }

    /**
     * Times {@code work} as the create operation and keeps the in-flight gauge
     * accurate even when the work throws. Returns the supplier's value.
     */
    public <T> T timeCreate(Supplier<T> work) {
        inFlight.incrementAndGet();
        Timer.Sample sample = Timer.start(registry);
        try {
            return work.get();
        } finally {
            sample.stop(Timer.builder(CREATE_TIMER)
                    .description("Latency of incident report creation")
                    .publishPercentileHistogram()
                    .register(registry));
            inFlight.decrementAndGet();
        }
    }

    private static String safe(String v) {
        return (v == null || v.isBlank()) ? "unknown" : v;
    }
}
