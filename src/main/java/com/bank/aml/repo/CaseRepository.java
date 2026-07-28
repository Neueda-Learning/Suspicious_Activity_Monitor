package com.bank.aml.repo;

import com.bank.aml.domain.CaseEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CaseRepository extends JpaRepository<CaseEntity, Long> {

    Optional<CaseEntity> findByCaseRef(String caseRef);

    List<CaseEntity> findByStatus(String status);

    List<CaseEntity> findByCustomerId(Long customerId);

    List<CaseEntity> findByAssignedTo(String assignedTo);
}
