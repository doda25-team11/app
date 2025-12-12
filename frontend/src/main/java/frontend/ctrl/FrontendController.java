package frontend.ctrl;

import java.net.URI;
import java.net.URISyntaxException;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import frontend.data.Sms;
import jakarta.servlet.http.HttpServletRequest;

@Controller
@RequestMapping(path = "/sms")
public class FrontendController {

    // Monitoring ==========
    private final MeterRegistry meterRegistry;

    // Gauge: current in-flight classification requests
    private final AtomicInteger inFlightRequests;

    public FrontendController(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        this.inFlightRequests = Gauge.builder(
                "sms_checker_in_flight_requests",
                new AtomicInteger(0),
                AtomicInteger::get
        )
        .description("Current number of SMS classification requests being processed")
        .tag("component", "model-service")
        .register(meterRegistry);
    }

    // =====================

    private String modelHost;

    private RestTemplateBuilder rest;

    public FrontendController(RestTemplateBuilder rest, Environment env) {
        this.rest = rest;
        this.modelHost = env.getProperty("MODEL_HOST");
        assertModelHost();
    }

    private void assertModelHost() {
        if (modelHost == null || modelHost.strip().isEmpty()) {
            System.err.println("ERROR: ENV variable MODEL_HOST is null or empty");
            System.exit(1);
        }
        modelHost = modelHost.strip();
        if (modelHost.indexOf("://") == -1) {
            var m = "ERROR: ENV variable MODEL_HOST is missing protocol, like \"http://...\" (was: \"%s\")\n";
            System.err.printf(m, modelHost);
            System.exit(1);
        } else {
            System.out.printf("Working with MODEL_HOST=\"%s\"\n", modelHost);
        }
    }

    @GetMapping("")
    public String redirectToSlash(HttpServletRequest request) {
        // relative REST requests in JS will end up on / and not on /sms
        return "redirect:" + request.getRequestURI() + "/";
    }

    @GetMapping("/")
    public String index(Model m) {
        m.addAttribute("hostname", modelHost);
        return "sms/index";
    }

    @PostMapping({ "", "/" })
    @ResponseBody
    public Sms predict(@RequestBody Sms sms) {
        System.out.printf("Requesting prediction for \"%s\" ...\n", sms.sms);
        sms.result = getPrediction(sms);
        System.out.printf("Prediction: %s\n", sms.result);
        return sms;
    }

    private String getPrediction(Sms sms) {
        // Counter: user initiated classification
        meterRegistry.counter(
                "sms_checker_actions_total",
                "action", "classify",
                "result", "started",
                "channel", "api"
        ).increment();

        // Gauge increment (request started)
        inFlightRequests.incrementAndGet();

        // Histogram / Timer for latency
        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            var url = new URI(modelHost + "/predict");
            var response = rest.build().postForEntity(url, sms, Sms.class);

            // Counter: success
            meterRegistry.counter(
                    "sms_checker_actions_total",
                    "action", "classify",
                    "result", "ok",
                    "channel", "api"
            ).increment();

            return response.getBody().result.trim();

        } catch (Exception e) {

            // Counter: failure
            meterRegistry.counter(
                    "sms_checker_actions_total",
                    "action", "classify",
                    "result", "error",
                    "channel", "api"
            ).increment();

            throw new RuntimeException(e);

        } finally {
            // Stop timer and record histogram
            sample.stop(
                    Timer.builder("sms_checker_classify_latency_seconds")
                            .description("Latency of SMS spam classification")
                            .publishPercentileHistogram()
                            .tag("channel", "api")
                            .tag("model_version", "current")
                            .register(meterRegistry)
            );

            // Gauge decrement (request finished)
            inFlightRequests.decrementAndGet();
        }
    }

}