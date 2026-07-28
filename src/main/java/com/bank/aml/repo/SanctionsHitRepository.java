package com.bank.aml.repo;

import com.bank.aml.domain.SanctionsHitEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SanctionsHitRepository extends JpaRepository<SanctionsHitEntity, Long> {

    List<SanctionsHitEntity> findByStatus(String status);

    List<SanctionsHitEntity> findByCustomerId(Long customerId);

    List<SanctionsHitEntity> findByPaymentTxnId(Long paymentTxnId);
}
