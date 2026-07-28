package com.bank.aml.service;

import com.bank.aml.config.AppProperties;
import com.bank.aml.domain.AuditEventEntity;
import com.bank.aml.repo.AuditEventRepository;
import com.bank.aml.util.Jsons;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuditService {
    private final AuditEventRepository auditEventRepository;
    private final AppProperties appProperties;

    @Transactional
    public AuditEventEntity record(String action, String entityType, Long entityId, Map<String, ?> details) {
        AuditEventEntity e = new AuditEventEntity();
        e.setActor(appProperties.getActor());
        e.setOccurredAt(Instant.now());
        e.setAction(action);
        e.setEntityType(entityType);
        e.setEntityId(entityId);
        e.setDetailsJson(details == null ? Jsons.emptyObj() : Jsons.toTree(details));
        return auditEventRepository.save(e);
    }

    @Transactional
    public AuditEventEntity record(String action, String entityType, Long entityId, JsonNode details) {
        AuditEventEntity e = new AuditEventEntity();
        e.setActor(appProperties.getActor());
        e.setOccurredAt(Instant.now());
        e.setAction(action);
        e.setEntityType(entityType);
        e.setEntityId(entityId);
        e.setDetailsJson(details == null ? Jsons.emptyObj() : details);
        return auditEventRepository.save(e);
    }

    public List<AuditEventEntity> forEntity(String entityType, Long entityId) {
        return auditEventRepository.findByEntityTypeAndEntityIdOrderByOccurredAtDesc(entityType, entityId);
    }
}
