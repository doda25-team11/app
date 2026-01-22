package frontend.metrics;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

@Component
public class UsabilityMetrics implements MeterBinder {

    private Counter actionsTotal;
    private Timer classifyLatency;
    private final AtomicInteger activeSessions = new AtomicInteger(0);

    @Override
    public void bindTo(MeterRegistry registry) {
        // Gauge: active sessions
        Gauge.builder("sms_checker_active_sessions", activeSessions, AtomicInteger::get)
                .description("Current number of active user sessions (approx)")
                .tag("channel", "ui")
                .register(registry);

        // Counter: actions
        // // Histogram/Timer: classification latency
        // classifyLatency = Timer.builder("sms_checker_classify_latency_seconds")
        //         .description("Time spent classifying a message")
        //         .publishPercentileHistogram() 
        //         .tag("channel", "ui")         
        //         .tag("model_version", "unknown")
        //         .register(registry);

        // A “base” counter just to ensure metric exists 
        actionsTotal = Counter.builder("sms_checker_actions_total")
                .description("Total user actions in the SMS checker")
                .tag("action", "init")
                .tag("result", "ok")
                .tag("channel", "ui")
                .register(registry);
    }

    // Counter with labels: action/result/channel
    public void incAction(MeterRegistry registry, String action, String result, String channel) {
        registry.counter("sms_checker_actions_total",
                "action", action,
                "result", result,
                "channel", channel
        ).increment();
    }

    // Histogram/Timer with labels: channel/model_version
    public <T> T timeClassify(MeterRegistry registry, String channel, String modelVersion, java.util.concurrent.Callable<T> fn)
            throws Exception {
        Timer timer = Timer.builder("sms_checker_classify_latency_seconds")
                .description("Time spent classifying a message")
                .publishPercentileHistogram()
                .tag("channel", channel)
                .tag("model_version", modelVersion)
                .register(registry);

        return timer.recordCallable(fn);
    }

    // Gauge helpers
    public void sessionStart() { activeSessions.incrementAndGet(); }
    public void sessionEnd()   { activeSessions.updateAndGet(v -> Math.max(0, v - 1)); }
}
