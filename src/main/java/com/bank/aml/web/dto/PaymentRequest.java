package com.bank.aml.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record PaymentRequest(
        @NotBlank String customerRef,
        @NotBlank String beneficiaryName,
        @NotBlank String beneficiaryCountry,
        @NotNull BigDecimal amount,
        @NotBlank String currency,
        String beneficiaryDob,
        String beneficiaryNationality) {}
