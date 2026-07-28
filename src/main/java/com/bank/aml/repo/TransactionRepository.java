package com.bank.aml.repo;

import com.bank.aml.domain.TransactionEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionRepository extends JpaRepository<TransactionEntity, Long> {

    Optional<TransactionEntity> findByTxnRef(String txnRef);

    List<TransactionEntity> findByCustomerIdAndExecutedAtBetween(
            Long customerId, Instant start, Instant end);

    List<TransactionEntity> findByCustomerId(Long customerId);
}
