package com.bank.aml.web.dto;

import jakarta.validation.constraints.NotBlank;

public record DispositionRequest(
        @NotBlank String decision,
        @NotBlank String rationale) {}
