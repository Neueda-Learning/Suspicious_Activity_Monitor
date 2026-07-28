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
@Table(name = "sanctions_hit")
@Getter
@Setter
public class SanctionsHitEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trigger_type", nullable = false, length = 64)
    private String triggerType;

    @Column(name = "payment_txn_id")
    private Long paymentTxnId;

    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "screened_name", nullable = false, length = 512)
    private String screenedName;

    @Column(name = "sanctions_entry_id", nullable = false)
    private Long sanctionsEntryId;

    @Column(name = "name_similarity", nullable = false, precision = 4, scale = 3)
    private BigDecimal nameSimilarity;

    @Column(nullable = false, length = 32)
    private String status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "match_details_snapshot", nullable = false, columnDefinition = "jsonb")
    private JsonNode matchDetailsSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "sanctions_entry_snapshot", nullable = false, columnDefinition = "jsonb")
    private JsonNode sanctionsEntrySnapshot;

    @Column(name = "resolution_rationale", columnDefinition = "text")
    private String resolutionRationale;

    @Column(name = "resolved_by", length = 128)
    private String resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;
}
