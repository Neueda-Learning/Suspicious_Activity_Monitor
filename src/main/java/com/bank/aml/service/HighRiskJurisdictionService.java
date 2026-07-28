package com.bank.aml.service;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import com.bank.aml.util.Jsons;

@Service
public class HighRiskJurisdictionService {
    @Getter
    private Set<String> codes = Set.of();

    @PostConstruct
    void load() throws Exception {
        try (InputStream in = new ClassPathResource("high-risk-jurisdictions.json").getInputStream()) {
            JsonNode root = Jsons.MAPPER.readTree(in);
            Set<String> set = new HashSet<>();
            for (JsonNode n : root.path("codes")) {
                set.add(n.asText().toUpperCase());
            }
            codes = Collections.unmodifiableSet(set);
        }
    }

    public boolean isHighRisk(String iso2) {
        return iso2 != null && codes.contains(iso2.toUpperCase());
    }
}
