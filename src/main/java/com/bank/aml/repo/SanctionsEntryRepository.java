package com.bank.aml.repo;

import com.bank.aml.domain.SanctionsEntryEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SanctionsEntryRepository extends JpaRepository<SanctionsEntryEntity, Long> {

    Optional<SanctionsEntryEntity> findBySourceUniqueId(String sourceUniqueId);

    List<SanctionsEntryEntity> findByActiveTrue();

    List<SanctionsEntryEntity> findByImportBatchId(String importBatchId);
}
