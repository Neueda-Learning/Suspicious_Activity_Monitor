package com.bank.aml.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI amlOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("AML Alert Triage API")
                        .description("UK AML alert triage, case management, and sanctions screening")
                        .version("v1"));
    }
}
