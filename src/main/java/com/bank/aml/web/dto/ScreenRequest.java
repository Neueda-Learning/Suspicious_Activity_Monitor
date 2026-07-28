package com.bank.aml.web.dto;

import jakarta.validation.constraints.NotBlank;

public record ScreenRequest(@NotBlank String name, String country, String dateOfBirth) {}
