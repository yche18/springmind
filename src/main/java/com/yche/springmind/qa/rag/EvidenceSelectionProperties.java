package com.yche.springmind.qa.rag;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import java.net.URI;
import java.time.Duration;

@ConfigurationProperties("qa.evidence")
public record EvidenceSelectionProperties(
        @DefaultValue("4096") int maxTokens,
        @DefaultValue("30") int candidateLimit,
        @DefaultValue("false") boolean rerankEnabled,
        @DefaultValue("") String rerankUrl,
        @DefaultValue("") String rerankApiKey,
        @DefaultValue("") String rerankModel,
        @DefaultValue("2s") Duration rerankTimeout) {
    public EvidenceSelectionProperties {
        if (maxTokens < 128 || maxTokens > 32768 || candidateLimit < 5 || candidateLimit > 50) {
            throw new IllegalArgumentException("Evidence budget must be 128..32768 tokens and candidate limit 5..50");
        }
        if (rerankTimeout == null || rerankTimeout.toMillis() < 1 || rerankTimeout.toSeconds() > 30) {
            throw new IllegalArgumentException("Rerank timeout must be positive and at most 30 seconds");
        }
        if (rerankEnabled) {
            URI uri = URI.create(rerankUrl);
            if (uri.getHost() == null || !("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                    || uri.getUserInfo() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException("Configure an HTTP(S) rerank endpoint without embedded credentials");
            }
        }
    }

    public static EvidenceSelectionProperties defaults() {
        return new EvidenceSelectionProperties(4096, 30, false, "", "", "", Duration.ofSeconds(2));
    }
}
