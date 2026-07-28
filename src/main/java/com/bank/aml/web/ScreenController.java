package com.bank.aml.web;

import com.bank.aml.service.SanctionsScreeningService;
import com.bank.aml.web.dto.ScreenRequest;
import com.bank.aml.web.dto.ScreenResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ScreenController {
    private final SanctionsScreeningService sanctionsScreeningService;

    @PostMapping("/api/screen")
    public ScreenResponse screen(@Valid @RequestBody ScreenRequest body) {
        return sanctionsScreeningService.screenOpen(body);
    }
}
