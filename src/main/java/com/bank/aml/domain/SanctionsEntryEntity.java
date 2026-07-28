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
@Table(name = "sanctions_entry")
@Getter
@Setter
public class SanctionsEntryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_unique_id", nullable = false, length = 128)
    private String sourceUniqueId;

    @Column(nullable = false, length = 512)
    private String name;

    @Column(name = "entity_type", nullable = false, length = 32)
    private String entityType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "aliases_json", nullable = false, columnDefinition = "jsonb")
    private JsonNode aliasesJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "identifiers_json", nullable = false, columnDefinition = "jsonb")
    private JsonNode identifiersJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "measures_json", nullable = false, columnDefinition = "jsonb")
    private JsonNode measuresJson;

    @Column(name = "source_updated_at")
    private Instant sourceUpdatedAt;

    @Column(name = "import_batch_id", length = 64)
    private String importBatchId;

    @Column(nullable = false)
    private Boolean active;
}
