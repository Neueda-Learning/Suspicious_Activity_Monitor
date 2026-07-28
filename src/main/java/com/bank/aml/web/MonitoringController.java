package com.bank.aml.web;

import com.bank.aml.service.MonitoringRunService;
import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class MonitoringController {

    private final MonitoringRunService monitoringRunService;

    /** Runs the sweep that is otherwise scheduled overnight, so it can be shown on demand. */
    @PostMapping("/api/monitoring/run")
    public Map<String, Object> run() {
        return monitoringRunService.run(Instant.now());
    }
}
