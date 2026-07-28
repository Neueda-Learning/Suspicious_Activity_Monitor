package com.bank.aml.web;

import com.bank.aml.domain.SanctionsHitEntity;
import com.bank.aml.service.DemoResetService;
import com.bank.aml.service.SanctionsScreeningService;
import com.bank.aml.web.dto.PaymentRequest;
import com.bank.aml.web.dto.PaymentResponse;
import com.bank.aml.web.dto.ResolveHitRequest;
import com.bank.aml.web.dto.SyncResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class SanctionsController {
    private final SanctionsScreeningService sanctionsScreeningService;
    private final DemoResetService demoResetService;

    @PostMapping("/api/payments")
    public PaymentResponse payment(@Valid @RequestBody PaymentRequest body) {
        return sanctionsScreeningService.screenPayment(body);
    }

    @GetMapping("/api/sanctions-hits")
    public List<SanctionsHitEntity> hits(@RequestParam(required = false, defaultValue = "POTENTIAL_MATCH") String status) {
        return sanctionsScreeningService.listHits(status);
    }

    @PostMapping("/api/sanctions-hits/{id}/resolve")
    public SanctionsHitEntity resolve(@PathVariable Long id, @Valid @RequestBody ResolveHitRequest body) {
        return sanctionsScreeningService.resolve(id, body);
    }

    @PostMapping("/api/sanctions/sync")
    public SyncResponse sync() {
        return sanctionsScreeningService.syncListV2();
    }

    /** Demo-only. Restores the opening state so the list-update beat survives a rehearsal. */
    @PostMapping("/api/demo/reset")
    public Map<String, Object> reset() {
        return demoResetService.reset();
    }
}
