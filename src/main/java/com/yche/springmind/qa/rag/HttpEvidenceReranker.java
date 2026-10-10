package com.yche.springmind.qa.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Supports index-based /rerank responses; provider-returned document text is never trusted. */
public class HttpEvidenceReranker implements EvidenceReranker, AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(HttpEvidenceReranker.class);
    private final EvidenceSelectionProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient client;

    public HttpEvidenceReranker(EvidenceSelectionProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
        this.client = HttpClient.newBuilder().connectTimeout(properties.rerankTimeout())
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    @Override
    public Result rerank(String question, List<Document> documents) {
        if (!properties.rerankEnabled()) return new Result(documents, "DISABLED");
        if (documents.size() < 2) return new Result(documents, "SKIPPED");
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("query", question);
            body.put("documents", documents.stream().map(Document::getText).map(this::preview).toList());
            body.put("top_n", documents.size());
            if (!properties.rerankModel().isBlank()) body.put("model", properties.rerankModel());
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(properties.rerankUrl()))
                    .timeout(properties.rerankTimeout()).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
            if (!properties.rerankApiKey().isBlank()) {
                request.header("Authorization", "Bearer " + properties.rerankApiKey());
            }
            HttpResponse<String> response = send(request.build());
            if (response.statusCode() != 200 || response.body().length() > 1_000_000) {
                throw new IllegalStateException("Invalid rerank response");
            }
            JsonNode results = mapper.readTree(response.body()).path("results");
            if (!results.isArray() || results.size() != documents.size()) {
                throw new IllegalStateException("Reranker must score every candidate");
            }
            Map<Integer, Double> scores = new HashMap<>();
            for (JsonNode item : results) {
                JsonNode indexNode = item.path("index");
                JsonNode scoreNode = item.path("relevance_score");
                if (!indexNode.isIntegralNumber() || !indexNode.canConvertToInt() || !scoreNode.isNumber()) {
                    throw new IllegalStateException("Invalid rerank score");
                }
                int index = indexNode.intValue();
                double score = scoreNode.doubleValue();
                if (index < 0 || index >= documents.size() || !Double.isFinite(score) || scores.put(index, score) != null) {
                    throw new IllegalStateException("Invalid or duplicate rerank index");
                }
            }
            List<Document> ranked = scores.keySet().stream()
                    .sorted(Comparator.<Integer>comparingDouble(scores::get).reversed().thenComparingInt(Integer::intValue))
                    .map(index -> {
                        Document original = documents.get(index);
                        Map<String, Object> metadata = new LinkedHashMap<>(original.getMetadata());
                        metadata.put("rerankScore", scores.get(index));
                        return Document.builder().id(original.getId()).text(original.getText()).metadata(metadata).build();
                    }).toList();
            return new Result(ranked, "APPLIED");
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            return new Result(documents, "FALLBACK");
        } catch (Exception failure) {
            // Do not log endpoint credentials, request documents, or provider response bodies.
            log.warn("Reranking unavailable; preserving RRF order. errorType={}", failure.getClass().getSimpleName());
            return new Result(documents, "FALLBACK");
        }
    }

    private String preview(String text) {
        int end = text.offsetByCodePoints(0, Math.min(4096, text.codePointCount(0, text.length())));
        return text.substring(0, end);
    }

    private HttpResponse<String> send(HttpRequest request) throws Exception {
        var pending = client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        try {
            // Bound the complete body download as well as connection/response-header latency.
            return pending.get(properties.rerankTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException | InterruptedException failure) {
            pending.cancel(true);
            throw failure;
        }
    }

    @Override
    public void close() { client.close(); }
}
