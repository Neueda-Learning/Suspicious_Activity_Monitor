package com.bank.aml.web.dto;

public record PaymentResponse(String status, Long sanctionsHitId, Long paymentTxnId, String message) {}
