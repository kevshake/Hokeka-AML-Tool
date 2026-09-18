package com.posgateway.aml.repository;

import com.posgateway.aml.entity.billing.CurrencyRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CurrencyRateRepository extends JpaRepository<CurrencyRate, String> {

    @Query("SELECT cr FROM CurrencyRate cr WHERE cr.isActive = true ORDER BY cr.currencyCode")
    List<CurrencyRate> findAllActive();
}
