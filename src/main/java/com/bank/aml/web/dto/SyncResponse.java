package com.bank.aml.web.dto;

public record SyncResponse(int added, int updated, int customersRescreened, int potentialMatchesCreated) {}
