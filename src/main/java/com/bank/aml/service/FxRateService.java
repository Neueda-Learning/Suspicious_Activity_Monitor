package com.bank.aml.service;

import com.bank.aml.domain.FxRateEntity;
import com.bank.aml.domain.FxRateEntity.FxRateId;
import com.bank.aml.repo.FxRateRepository;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

@Service
@RequiredArgsConstructor
@Slf4j
public class FxRateService {
    private static final List<String> CURRENCIES = List.of("USD", "EUR", "CNY", "CHF", "JPY", "HKD", "SGD", "AED");

    private final FxRateRepository fxRateRepository;
    private final RestClient.Builder restClientBuilder;

    private static final Map<String, BigDecimal> FALLBACK = new LinkedHashMap<>();
    static {
        FALLBACK.put("GBP", BigDecimal.ONE);
        FALLBACK.put("USD", new BigDecimal("0.79000000"));
        FALLBACK.put("EUR", new BigDecimal("0.86000000"));
        FALLBACK.put("CNY", new BigDecimal("0.11000000"));
        FALLBACK.put("CHF", new BigDecimal("0.90000000"));
        FALLBACK.put("JPY", new BigDecimal("0.00520000"));
        FALLBACK.put("HKD", new BigDecimal("0.10100000"));
        FALLBACK.put("SGD", new BigDecimal("0.59000000"));
        FALLBACK.put("AED", new BigDecimal("0.21500000"));
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        refreshRecentRates();
    }

    @Scheduled(cron = "0 15 6 * * *")
    public void scheduledRefresh() {
        refreshRecentRates();
    }

    @Transactional
    public void refreshRecentRates() {
        LocalDate today = LocalDate.now();
        boolean anyNetwork = false;
        for (String ccy : CURRENCIES) {
            for (int i = 0; i < 14; i++) {
                LocalDate d = today.minusDays(i);
                if (fxRateRepository.findByIdCurrencyAndIdRateDate(ccy, d).isPresent()) continue;
                Optional<BigDecimal> remote = fetchFrankfurter(ccy, d);
                if (remote.isPresent()) {
                    save(ccy, d, remote.get());
                    anyNetwork = true;
                }
            }
        }
        // ensure baseline coverage for seed window (~90 days) via fallback
        for (String ccy : FALLBACK.keySet()) {
            for (int i = 0; i < 90; i++) {
                LocalDate d = today.minusDays(i);
                if (fxRateRepository.findByIdCurrencyAndIdRateDate(ccy, d).isEmpty()) {
                    save(ccy, d, FALLBACK.get(ccy));
                }
            }
        }
        log.info("FX cache ready (networkSuccess={})", anyNetwork);
    }

    private Optional<BigDecimal> fetchFrankfurter(String currency, LocalDate date) {
        if ("GBP".equalsIgnoreCase(currency)) return Optional.of(BigDecimal.ONE);
        try {
            SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
            rf.setConnectTimeout(Duration.ofSeconds(3));
            rf.setReadTimeout(Duration.ofSeconds(8));
            RestClient client = restClientBuilder.requestFactory(rf).build();
            JsonNode body = client.get()
                    .uri("https://api.frankfurter.app/{date}?from={from}&to=GBP", date, currency)
                    .retrieve()
                    .body(JsonNode.class);
            if (body != null && body.path("rates").has("GBP")) {
                return Optional.of(body.path("rates").path("GBP").decimalValue());
            }
        } catch (Exception ex) {
            log.debug("Frankfurter miss {} {}: {}", currency, date, ex.getMessage());
        }
        return Optional.empty();
    }

    private void save(String currency, LocalDate date, BigDecimal rate) {
        FxRateEntity e = new FxRateEntity();
        FxRateId id = new FxRateId();
        id.setCurrency(currency.toUpperCase());
        id.setRateDate(date);
        e.setId(id);
        e.setRateToGbp(rate);
        e.setFetchedAt(Instant.now());
        fxRateRepository.save(e);
    }

    public BigDecimal rateToGbp(String currency, LocalDate date) {
        if (currency == null || "GBP".equalsIgnoreCase(currency)) return BigDecimal.ONE;
        return fxRateRepository.findByIdCurrencyAndIdRateDate(currency.toUpperCase(), date)
                .map(FxRateEntity::getRateToGbp)
                .or(() -> Optional.ofNullable(FALLBACK.get(currency.toUpperCase())))
                .orElse(BigDecimal.ONE);
    }

    public BigDecimal toGbp(String currency, LocalDate date, BigDecimal amount) {
        return amount.multiply(rateToGbp(currency, date)).setScale(2, RoundingMode.HALF_UP);
    }
}
