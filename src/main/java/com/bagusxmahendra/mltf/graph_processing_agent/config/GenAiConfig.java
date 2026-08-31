package com.bagusxmahendra.mltf.graph_processing_agent.config;

import com.google.genai.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration providing Google Gen AI Client bean.
 */
@Configuration
public class GenAiConfig {

    private static final Logger log = LoggerFactory.getLogger(GenAiConfig.class);

    @Value("${google.genai.api-key:}")
    private String apiKey;

    @Bean
    @ConditionalOnMissingBean(Client.class)
    public Client googleGenAiClient() {
        if (apiKey != null && !apiKey.isBlank()) {
            log.info("Initializing Google Gen AI Client with configured API key.");
            return Client.builder().apiKey(apiKey).build();
        } else {
            log.info("Initializing Google Gen AI Client with default environment builder.");
            try {
                return Client.builder().build();
            } catch (Exception e) {
                log.warn("Default credentials not found for Google Gen AI Client: {}. Fallback builder used.", e.getMessage());
                return Client.builder().apiKey("AIzaSyMockKeyForInitialization").build();
            }
        }
    }
}
