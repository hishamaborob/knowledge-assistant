package com.kbassistant.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI knowledgeAssistantOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Knowledge Assistant API")
                        .description("RAG application — upload documents, ask questions, get cited answers")
                        .version("0.1.0")
                        .license(new License().name("MIT")));
    }
}
