package com.bank.aml.web;

import com.bank.aml.domain.CaseEntity;
import com.bank.aml.service.CaseService;
import com.bank.aml.web.dto.CaseDetailDto;
import com.bank.aml.web.dto.CaseListResponse;
import com.bank.aml.web.dto.DispositionRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cases")
@RequiredArgsConstructor
public class CaseController {
    private final CaseService caseService;

    @GetMapping
    public CaseListResponse list(
            @RequestParam(required = false, defaultValue = "OPEN") String status,
            @RequestParam(required = false, defaultValue = "priority") String sort) {
        return caseService.list(status, sort);
    }

    @GetMapping("/{id}")
    public CaseDetailDto get(@PathVariable Long id) {
        return caseService.get(id);
    }

    @PostMapping("/{id}/disposition")
    public CaseEntity dispose(@PathVariable Long id, @Valid @RequestBody DispositionRequest body) {
        return caseService.dispose(id, body);
    }
}
