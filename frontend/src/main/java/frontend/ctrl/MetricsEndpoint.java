package frontend.ctrl;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.distribution.CountAtBucket;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.core.instrument.distribution.HistogramSupport;


import java.util.concurrent.TimeUnit;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// Used AI for the help with Histogram formulation

@RestController
public class MetricsEndpoint {

  private final MeterRegistry registry;

  public MetricsEndpoint(MeterRegistry registry) {
    this.registry = registry;
  }

  @GetMapping(value = "/metrics", produces = "text/plain; version=0.0.4; charset=utf-8")
  public String metrics() {
    StringBuilder sb = new StringBuilder();

    renderGauge(sb, "sms_checker_active_sessions", "Current number of active user sessions (approx)");
    sb.append("\n");

    renderCounters(sb, "sms_checker_actions_total", "Total user actions in the SMS checker");
    sb.append("\n");

    renderTimers(sb, "sms_checker_classify_latency_seconds", "Time spent classifying a message");
   

    return sb.toString();
  }

  private void renderGauge(StringBuilder sb, String name, String help) {
    Gauge g = registry.find(name).gauge();
    double v = (g == null) ? 0.0 : g.value();
    sb.append("# HELP ").append(name).append(" ").append(help).append("\n");
    sb.append("# TYPE ").append(name).append(" gauge\n");
    sb.append(name).append(" ").append(v).append("\n");
  }

  private void renderCounters(StringBuilder sb, String name, String help) {
    sb.append("# HELP ").append(name).append(" ").append(help).append("\n");
    sb.append("# TYPE ").append(name).append(" counter\n");
    for (Meter m : registry.find(name).meters()) {
      Counter c = (Counter) m;
      sb.append(name).append(formatTags(m)).append(" ").append(c.count()).append("\n");
    }
  }

  private void renderTimers(StringBuilder sb, String name, String help) {
    sb.append("# HELP ").append(name).append(" ").append(help).append("\n");
    sb.append("# TYPE ").append(name).append(" histogram\n");

    for (Meter m : registry.find(name).meters()) {
      Timer t = (Timer) m;

      if (!(t instanceof HistogramSupport hs)) {
     
        sb.append(name).append("_count").append(formatTags(m)).append(" ").append(t.count()).append("\n");
        sb.append(name).append("_sum").append(formatTags(m)).append(" ").append(t.totalTime(TimeUnit.SECONDS)).append("\n");
        continue;
      }

      HistogramSnapshot snap = hs.takeSnapshot();

   
      long cumulative = 0;
      for (CountAtBucket b : snap.histogramCounts()) {
        cumulative += b.count();
        sb.append(name).append("_bucket")
          .append(formatTagsHisto(m, "le", Double.toString(b.bucket())))
          .append(" ")
          .append(cumulative)
          .append("\n");
      }

      // +Inf bucket == total count
      sb.append(name).append("_bucket")
        .append(formatTagsHisto(m, "le", "+Inf"))
        .append(" ")
        .append(t.count())
        .append("\n");

   
      sb.append(name).append("_sum")
        .append(formatTags(m))
        .append(" ")
        .append(t.totalTime(TimeUnit.SECONDS))
        .append("\n");

      sb.append(name).append("_count")
        .append(formatTags(m))
        .append(" ")
        .append(t.count())
        .append("\n");
    }
  }


  private static String formatTags(Meter m) {
    if (m.getId().getTags().isEmpty()) return "";
    StringBuilder sb = new StringBuilder("{");
    for (int i = 0; i < m.getId().getTags().size(); i++) {
      var t = m.getId().getTags().get(i);
      if (i > 0) sb.append(",");
      sb.append(t.getKey()).append("=\"").append(escape(t.getValue())).append("\"");
    }
    sb.append("}");
    return sb.toString();
  }

  private static String formatTagsHisto(Meter m, String key, String value) {
    var tags = m.getId().getTags();
    StringBuilder sb = new StringBuilder("{");
    boolean first = true;

    for (var t : tags) {
      if (!first) sb.append(",");
      first = false;
      sb.append(t.getKey()).append("=\"").append(escape(t.getValue())).append("\"");
    }

    if (!first) sb.append(",");
    sb.append(key).append("=\"").append(escape(value)).append("\"");
    sb.append("}");
    return sb.toString();
  }


  private static String escape(String v) {
    return v.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}

