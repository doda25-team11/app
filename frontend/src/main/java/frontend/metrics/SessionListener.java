package frontend.metrics;

import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import org.springframework.stereotype.Component;

@Component
public class SessionLifecycleListener implements HttpSessionListener {

    private final UsabilityMetrics metrics;

    public SessionLifecycleListener(UsabilityMetrics metrics) {
        this.metrics = metrics;
    }

    @Override
    public void sessionCreated(HttpSessionEvent se) {
        metrics.sessionStart();
    }

    @Override
    public void sessionDestroyed(HttpSessionEvent se) {
        metrics.sessionEnd();
    }
}
