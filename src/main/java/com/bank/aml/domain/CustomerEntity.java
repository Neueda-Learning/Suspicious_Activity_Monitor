package com.bank.aml.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "customer")
@Getter
@Setter
public class CustomerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_ref", nullable = false, unique = true, length = 64)
    private String customerRef;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 32)
    private String segment;

    @Column(name = "legal_form", length = 32)
    private String legalForm;

    @Column(length = 128)
    private String industry;

    @Column(name = "incorporation_country", nullable = false, length = 2)
    private String incorporationCountry;

    @Column(name = "registration_date")
    private LocalDate registrationDate;

    @Column(nullable = false, length = 16)
    private String crr;

    @Column(name = "monitoring_status", nullable = false, length = 32)
    private String monitoringStatus;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
