package com.bank.aml.web.dto;

import jakarta.validation.constraints.NotBlank;

public record ResolveHitRequest(@NotBlank String outcome, @NotBlank String rationale) {}
