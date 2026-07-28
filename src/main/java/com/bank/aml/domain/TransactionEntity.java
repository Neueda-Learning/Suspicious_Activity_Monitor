package com.bank.aml.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/**
 * Maps to table {@code txn} (not {@code transaction}) to avoid PostgreSQL reserved-word issues.
 */
@Entity
@Table(name = "txn")
@Getter
@Setter
public class TransactionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "txn_ref", nullable = false, unique = true, length = 64)
    private String txnRef;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "account_ref", nullable = false, length = 64)
    private String accountRef;

    @Column(nullable = false, length = 16)
    private String direction;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "amount_gbp", nullable = false, precision = 18, scale = 2)
    private BigDecimal amountGbp;

    @Column(name = "fx_rate_used", precision = 18, scale = 8)
    private BigDecimal fxRateUsed;

    @Column(name = "fx_rate_date")
    private LocalDate fxRateDate;

    @Column(name = "counterparty_name")
    private String counterpartyName;

    @Column(name = "counterparty_ref", length = 64)
    private String counterpartyRef;

    @Column(name = "counterparty_country", length = 2)
    private String counterpartyCountry;

    /** Null while a payment is held by sanctions screening — a held payment has not been executed. */
    @Column(name = "executed_at")
    private Instant executedAt;

    /** RELEASED | HELD | RELEASED_FALSE_MATCH | RESTRICTED_TARGET_MATCH */
    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
