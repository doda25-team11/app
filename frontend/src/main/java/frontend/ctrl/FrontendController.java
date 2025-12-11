package frontend.ctrl;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;

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
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.DistributionSummary;

@Controller
@RequestMapping(path = "/sms")
public class FrontendController {

    private String modelHost;

    private RestTemplateBuilder rest;
    private MeterRegistry registry;
    private Timer predictionTimer;
    private AtomicInteger modelServiceHealth;
    private DistributionSummary smsLengthSummary;

    public FrontendController(RestTemplateBuilder rest, Environment env, MeterRegistry registry) {
        this.rest = rest;
        this.modelHost = env.getProperty("MODEL_HOST");
        assertModelHost();

        this.registry = registry;
    
        // timer registratio
        this.predictionTimer = Timer.builder("model_service_response_time_seconds")
        .description("Latency of the model prediction service call")
        .tags(List.of(Tag.of("model_version", "latest")))
        .publishPercentiles(0.5, 0.95) 
        .register(this.registry);

        // GAUGE registration
        this.modelServiceHealth = new AtomicInteger(1);
        this.registry.gauge("model_service_health_status",
            List.of(Tag.of("service", "model-service"), Tag.of("model_version", "latest")),
            this.modelServiceHealth,
            AtomicInteger::get);

        // Distribution Summary registration 
        this.smsLengthSummary = DistributionSummary.builder("sms_request_length_bytes")
            .description("Distribution of SMS payload lengths (bytes) submitted by users.")
            .baseUnit("bytes")
            .tags(List.of(Tag.of("application", "frontend")))
            .publishPercentiles(0.5, 0.9, 0.99)
            .serviceLevelObjectives(20.0, 50.0, 100.0)
            .register(this.registry);   
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

        // histogram/summary recording
        this.smsLengthSummary.record(sms.sms.length());

        Timer.Sample sample = Timer.start();
        sms.result = getPrediction(sms);
        sample.stop(predictionTimer);

        // counter increment with dynamic tag (or else it failed for me)
        this.registry.counter(
            "sms_classification_requests_total",
            List.of(
                Tag.of("classifier", "decision-tree"), // Static tag
                Tag.of("status", sms.result.toLowerCase()) // Dynamic tag
            )
        ).increment();

        System.out.printf("Prediction: %s\n", sms.result);
        return sms;
    }

    private String getPrediction(Sms sms) {
        try {
            var url = new URI(modelHost + "/predict");
            var c = rest.build().postForEntity(url, sms, Sms.class);

            modelServiceHealth.set(1); 
            return c.getBody().result.trim();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        } catch (Exception e) {
            modelServiceHealth.set(0); 
            System.err.printf("ERROR: Model service call failed: %s\n", e.getMessage());
            return "error";
        }
    }
}