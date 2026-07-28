package com.bank.aml.web;

import com.bank.aml.domain.AiDraftEntity;
import com.bank.aml.service.AiDraftService;
import com.bank.aml.web.dto.AiDraftAcceptRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AiController {
    private final AiDraftService aiDraftService;

    @PostMapping("/api/cases/{id}/ai-draft")
    public AiDraftEntity generate(@PathVariable Long id) {
        return aiDraftService.generate(id);
    }

    @PutMapping("/api/ai-drafts/{id}")
    public AiDraftEntity accept(@PathVariable Long id, @Valid @RequestBody AiDraftAcceptRequest body) {
        return aiDraftService.accept(id, body);
    }
}
