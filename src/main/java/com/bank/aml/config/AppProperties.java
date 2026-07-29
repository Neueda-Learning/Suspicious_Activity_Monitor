package com.bank.aml.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "aml")
public class AppProperties {

    private String actor = "sarah.chen";
    private Ollama ollama = new Ollama();
    private Sanctions sanctions = new Sanctions();

    @Getter
    @Setter
    public static class Ollama {
        private String baseUrl = "http://localhost:11434";
        private String model = "qwen3:4b";
        private long timeoutMs = 60000;
    }

    @Getter
    @Setter
    public static class Sanctions {
        private String listV1 = "classpath:sanctions/list-v1.json";
        private String listV2 = "classpath:sanctions/list-v2.json";
    }
}
