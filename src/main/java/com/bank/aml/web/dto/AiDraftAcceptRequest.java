package com.bank.aml.web.dto;

import jakarta.validation.constraints.NotBlank;

public record AiDraftAcceptRequest(@NotBlank String analystFinalText) {}
