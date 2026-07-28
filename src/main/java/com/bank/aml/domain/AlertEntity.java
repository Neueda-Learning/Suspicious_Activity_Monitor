package com.bank.aml.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "alert")
@Getter
@Setter
public class AlertEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "case_id")
    private Long caseId;

    @Column(name = "rule_code", nullable = false, length = 16)
    private String ruleCode;

    @Column(name = "rule_name", nullable = false, length = 128)
    private String ruleName;

    @Column(nullable = false, precision = 4, scale = 3)
    private BigDecimal strength;

    @Column(nullable = false)
    private Integer points;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rule_params_snapshot", nullable = false, columnDefinition = "jsonb")
    private JsonNode ruleParamsSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence_snapshot", nullable = false, columnDefinition = "jsonb")
    private JsonNode evidenceSnapshot;

    @Column(name = "window_start")
    private Instant windowStart;

    @Column(name = "window_end")
    private Instant windowEnd;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
