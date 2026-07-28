package com.bank.aml.repo;

import com.bank.aml.domain.CountryInfoEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CountryInfoRepository extends JpaRepository<CountryInfoEntity, String> {
}
