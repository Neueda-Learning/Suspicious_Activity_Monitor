package com.bank.aml.repo;

import com.bank.aml.domain.FxRateEntity;
import com.bank.aml.domain.FxRateEntity.FxRateId;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FxRateRepository extends JpaRepository<FxRateEntity, FxRateId> {

    Optional<FxRateEntity> findByIdCurrencyAndIdRateDate(String currency, LocalDate rateDate);
}
