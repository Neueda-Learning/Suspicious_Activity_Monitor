package com.bank.aml.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "country_info")
@Getter
@Setter
public class CountryInfoEntity {

    @Id
    @Column(length = 2)
    private String iso2;

    @Column(nullable = false)
    private String name;

    @Column(length = 128)
    private String region;

    @Column(length = 128)
    private String capital;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_json", nullable = false, columnDefinition = "jsonb")
    private JsonNode rawJson;
}
