package com.bank.aml.repo;

import com.bank.aml.domain.AiDraftEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiDraftRepository extends JpaRepository<AiDraftEntity, Long> {

    List<AiDraftEntity> findByCaseId(Long caseId);

    Optional<AiDraftEntity> findFirstByCaseIdOrderByGeneratedAtDesc(Long caseId);
}
