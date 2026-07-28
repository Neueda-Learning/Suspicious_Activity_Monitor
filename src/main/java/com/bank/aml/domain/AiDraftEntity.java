package com.bank.aml.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "ai_draft")
@Getter
@Setter
public class AiDraftEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "case_id", nullable = false)
    private Long caseId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_snapshot_json", nullable = false, columnDefinition = "jsonb")
    private JsonNode inputSnapshotJson;

    @Column(name = "raw_output", nullable = false, columnDefinition = "text")
    private String rawOutput;

    @Column(name = "analyst_final_text", columnDefinition = "text")
    private String analystFinalText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "model_metadata_json", nullable = false, columnDefinition = "jsonb")
    private JsonNode modelMetadataJson;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    @Column(name = "edited_by", length = 128)
    private String editedBy;

    @Column(name = "edited_at")
    private Instant editedAt;

    @Column(nullable = false, length = 32)
    private String status;
}
