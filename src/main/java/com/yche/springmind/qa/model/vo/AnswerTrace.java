package com.yche.springmind.qa.model.vo;

import org.springframework.ai.document.Document;
import java.util.List;
import java.util.Map;

/** Request-local diagnostics; no global trace lookup or raw prompt storage. */
public record AnswerTrace(String traceId, String evidenceLevel, RetrievalTrace retrieval,
                          long retrievalMs, long generationMs, long totalMs, List<Evidence> evidence) {
    public static List<Evidence> evidence(List<Document> documents) {
        return documents.stream().map(document -> {
            Map<String, Object> metadata = document.getMetadata();
            String content = document.getText() == null ? "" : document.getText();
            return new Evidence(document.getId(), number(metadata, "documentId"),
                    String.valueOf(metadata.getOrDefault("fileName", "")),
                    number(metadata, "startChunkIndex"), number(metadata, "endChunkIndex"),
                    String.valueOf(metadata.getOrDefault("retrievalSource", "UNKNOWN")),
                    metadata.get("score") instanceof Number score ? score.doubleValue() : 0,
                    content.substring(0, Math.min(600, content.length())), content.length() > 600,
                    number(metadata, "rrfRank"), number(metadata, "selectionRank"),
                    metadata.get("rerankScore") instanceof Number score ? score.doubleValue() : null);
        }).toList();
    }

    private static Long number(Map<String, Object> metadata, String key) {
        return metadata.get(key) instanceof Number number ? number.longValue() : null;
    }

    public record Evidence(String evidenceId, Long documentId, String fileName, Long startChunkIndex,
                           Long endChunkIndex, String source, double score, String excerpt, boolean truncated,
                           Long rrfRank, Long selectionRank, Double rerankScore) { }
}
