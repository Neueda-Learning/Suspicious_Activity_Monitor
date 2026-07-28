package com.bank.aml.repo;

import com.bank.aml.domain.AuditEventEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventRepository extends JpaRepository<AuditEventEntity, Long> {

    List<AuditEventEntity> findByEntityTypeAndEntityIdOrderByOccurredAtDesc(
            String entityType, Long entityId);

    List<AuditEventEntity> findByAction(String action);
}
