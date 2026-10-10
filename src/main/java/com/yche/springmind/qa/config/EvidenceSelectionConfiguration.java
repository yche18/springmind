package com.yche.springmind.qa.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yche.springmind.qa.rag.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(EvidenceSelectionProperties.class)
public class EvidenceSelectionConfiguration {
    @Bean
    EvidenceReranker evidenceReranker(EvidenceSelectionProperties properties, ObjectMapper mapper) {
        return new HttpEvidenceReranker(properties, mapper);
    }

    @Bean
    EvidenceSelector evidenceSelector(EvidenceSelectionProperties properties, EvidenceReranker reranker) {
        return new EvidenceSelector(properties, reranker);
    }
}
