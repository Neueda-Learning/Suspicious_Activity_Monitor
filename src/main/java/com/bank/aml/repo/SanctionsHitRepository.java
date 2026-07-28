package com.bank.aml.repo;

import com.bank.aml.domain.SanctionsHitEntity;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SanctionsHitRepository extends JpaRepository<SanctionsHitEntity, Long> {

    /**
     * Claims an unresolved hit in a single statement and reports whether this caller won.
     * A read-then-write guard would let two concurrent reviewers both see POTENTIAL_MATCH and
     * apply contradictory outcomes.
     *
     * @return 1 if this call transitioned the hit, 0 if someone else already resolved it
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE SanctionsHitEntity h
               SET h.status = :outcome,
                   h.resolutionRationale = :rationale,
                   h.resolvedBy = :actor,
                   h.resolvedAt = :now
             WHERE h.id = :id
               AND h.status = 'POTENTIAL_MATCH'
            """)
    int resolveIfUnresolved(@Param("id") Long id,
                            @Param("outcome") String outcome,
                            @Param("rationale") String rationale,
                            @Param("actor") String actor,
                            @Param("now") Instant now);

    List<SanctionsHitEntity> findByStatus(String status);

    List<SanctionsHitEntity> findByCustomerId(Long customerId);

    List<SanctionsHitEntity> findByPaymentTxnId(Long paymentTxnId);
}
