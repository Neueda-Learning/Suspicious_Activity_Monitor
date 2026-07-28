package com.bank.aml.repo;

import com.bank.aml.domain.AlertEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertRepository extends JpaRepository<AlertEntity, Long> {

    List<AlertEntity> findByCustomerId(Long customerId);

    List<AlertEntity> findByCaseId(Long caseId);

    List<AlertEntity> findByRuleCode(String ruleCode);
}
