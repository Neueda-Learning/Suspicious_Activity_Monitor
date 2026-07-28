package com.bank.aml.service;

import com.bank.aml.domain.CountryInfoEntity;
import com.bank.aml.repo.CountryInfoRepository;
import com.bank.aml.util.Jsons;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

@Service
@RequiredArgsConstructor
@Slf4j
public class CountryInfoService {
    private final CountryInfoRepository countryInfoRepository;
    private final RestClient.Builder restClientBuilder;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void warmCache() {
        if (countryInfoRepository.count() > 0) {
            log.info("country_info already seeded ({})", countryInfoRepository.count());
            return;
        }
        try {
            SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
            rf.setConnectTimeout(Duration.ofSeconds(5));
            rf.setReadTimeout(Duration.ofSeconds(20));
            RestClient client = restClientBuilder.requestFactory(rf).build();
            JsonNode arr = client.get()
                    .uri("https://restcountries.com/v3.1/all?fields=cca2,name,region,capital")
                    .retrieve()
                    .body(JsonNode.class);
            if (arr != null && arr.isArray()) {
                for (JsonNode n : arr) {
                    saveNode(n);
                }
                log.info("Loaded {} countries from REST Countries", countryInfoRepository.count());
                return;
            }
        } catch (Exception ex) {
            log.warn("REST Countries unavailable, using fallback: {}", ex.getMessage());
        }
        seedFallback();
    }

    private void saveNode(JsonNode n) {
        String iso2 = n.path("cca2").asText(null);
        if (iso2 == null || iso2.isBlank()) return;
        CountryInfoEntity e = new CountryInfoEntity();
        e.setIso2(iso2.toUpperCase());
        e.setName(n.path("name").path("common").asText(iso2));
        e.setRegion(n.path("region").asText(null));
        JsonNode capital = n.path("capital");
        e.setCapital(capital.isArray() && !capital.isEmpty() ? capital.get(0).asText() : null);
        e.setRawJson(n);
        countryInfoRepository.save(e);
    }

    private void seedFallback() {
        Map<String, String[]> data = new LinkedHashMap<>();
        data.put("GB", new String[]{"United Kingdom", "Europe", "London"});
        data.put("US", new String[]{"United States", "Americas", "Washington, D.C."});
        data.put("DE", new String[]{"Germany", "Europe", "Berlin"});
        data.put("FR", new String[]{"France", "Europe", "Paris"});
        data.put("CN", new String[]{"China", "Asia", "Beijing"});
        data.put("HK", new String[]{"Hong Kong", "Asia", "Hong Kong"});
        data.put("AE", new String[]{"United Arab Emirates", "Asia", "Abu Dhabi"});
        data.put("NG", new String[]{"Nigeria", "Africa", "Abuja"});
        data.put("IR", new String[]{"Iran", "Asia", "Tehran"});
        data.put("PK", new String[]{"Pakistan", "Asia", "Islamabad"});
        data.put("TR", new String[]{"Turkey", "Asia", "Ankara"});
        data.put("RU", new String[]{"Russia", "Europe", "Moscow"});
        data.put("SG", new String[]{"Singapore", "Asia", "Singapore"});
        data.put("NL", new String[]{"Netherlands", "Europe", "Amsterdam"});
        data.put("CH", new String[]{"Switzerland", "Europe", "Bern"});
        data.put("JP", new String[]{"Japan", "Asia", "Tokyo"});
        data.put("IN", new String[]{"India", "Asia", "New Delhi"});
        data.put("BR", new String[]{"Brazil", "Americas", "Brasília"});
        data.put("ZA", new String[]{"South Africa", "Africa", "Pretoria"});
        data.put("AU", new String[]{"Australia", "Oceania", "Canberra"});
        data.forEach((iso, v) -> {
            CountryInfoEntity e = new CountryInfoEntity();
            e.setIso2(iso);
            e.setName(v[0]);
            e.setRegion(v[1]);
            e.setCapital(v[2]);
            e.setRawJson(Jsons.obj().put("cca2", iso).put("fallback", true));
            countryInfoRepository.save(e);
        });
        log.info("Seeded {} fallback countries", data.size());
    }

    public String displayName(String iso2) {
        if (iso2 == null) return null;
        return countryInfoRepository.findById(iso2.toUpperCase()).map(CountryInfoEntity::getName).orElse(iso2);
    }
}
