package com.yche.springmind.qa.rag;

import com.yche.springmind.qa.model.vo.EvidenceSelectionTrace;
import org.springframework.ai.document.Document;
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;
import org.springframework.ai.tokenizer.TokenCountEstimator;
import java.util.*;
import java.util.stream.Collectors;

/** Selects complete source chunks around each primary hit under a shared context budget. */
public class EvidenceSelector {
    private final EvidenceSelectionProperties properties;
    private final EvidenceReranker reranker;
    private final TokenCountEstimator tokenizer;

    public EvidenceSelector(EvidenceSelectionProperties properties, EvidenceReranker reranker) {
        this.properties = properties;
        this.reranker = reranker;
        this.tokenizer = new JTokkitTokenCountEstimator();
    }

    public static EvidenceSelector defaults() {
        return new EvidenceSelector(EvidenceSelectionProperties.defaults(),
                (question, docs) -> new EvidenceReranker.Result(docs, "DISABLED"));
    }

    public int candidateLimit() { return properties.candidateLimit(); }

    public Result select(String question, List<Document> candidates, int topK) {
        List<Document> inputs = new ArrayList<>();
        for (int index = 0; index < candidates.size(); index++) {
            Document document = candidates.get(index);
            Map<String, Object> metadata = new LinkedHashMap<>(document.getMetadata());
            metadata.put("rrfRank", index + 1);
            inputs.add(Document.builder().id(document.getId()).text(document.getText()).metadata(metadata).build());
        }
        int inputTokens = tokens(inputs);
        long rerankStart = System.nanoTime();
        EvidenceReranker.Result ranked = reranker.rerank(question, inputs);
        long rerankMs = (System.nanoTime() - rerankStart) / 1_000_000;
        long selectionStart = System.nanoTime();
        List<Document> selected = new ArrayList<>();
        Set<String> usedChunks = new HashSet<>();
        Set<String> usedText = new HashSet<>();
        for (int rank = 0; rank < ranked.documents().size() && selected.size() < topK; rank++) {
            Document document = ranked.documents().get(rank);
            List<EvidenceWindowChunk> window = window(document);
            if (window.isEmpty()) continue;
            int primaryIndex = ((Number) document.getMetadata().getOrDefault("primaryChunkIndex", window.getFirst().index())).intValue();
            int primary = -1;
            for (int index = 0; index < window.size(); index++) {
                if (window.get(index).index() == primaryIndex) primary = index;
            }
            if (primary < 0) continue;
            String evidenceId = "E" + (selected.size() + 1);
            if (used(document, window.get(primary), usedChunks, usedText)) continue;
            List<EvidenceWindowChunk> chosen = new ArrayList<>(List.of(window.get(primary)));
            if (!fits(selected, format(document, evidenceId, chosen))) continue;
            boolean leftOpen = true;
            boolean rightOpen = true;
            for (int distance = 1; distance < window.size(); distance++) {
                for (int direction : new int[]{-1, 1}) {
                    if (direction == -1 && !leftOpen || direction == 1 && !rightOpen) continue;
                    int index = primary + direction * distance;
                    if (index < 0 || index >= window.size()) continue;
                    EvidenceWindowChunk chunk = window.get(index);
                    List<EvidenceWindowChunk> expanded = new ArrayList<>(chosen);
                    expanded.add(chunk);
                    expanded.sort(Comparator.comparingInt(EvidenceWindowChunk::index));
                    boolean consecutive = direction == -1 ? chunk.index() + 1 == chosen.getFirst().index()
                            : chunk.index() - 1 == chosen.getLast().index();
                    if (consecutive && !used(document, chunk, usedChunks, usedText)
                            && chosen.stream().noneMatch(existing -> normalize(existing.text()).equals(normalize(chunk.text())))
                            && fits(selected, format(document, evidenceId, expanded))) {
                        chosen = expanded;
                    } else if (direction == -1) leftOpen = false;
                    else rightOpen = false;
                }
            }
            Map<String, Object> metadata = new LinkedHashMap<>(document.getMetadata());
            metadata.remove("windowChunks");
            metadata.put("evidenceId", evidenceId);
            metadata.put("selectionRank", rank + 1);
            metadata.put("startChunkIndex", chosen.getFirst().index());
            metadata.put("endChunkIndex", chosen.getLast().index());
            boolean vector = chosen.stream().anyMatch(chunk -> "VECTOR".equals(chunk.retrievalSource()) || "BOTH".equals(chunk.retrievalSource()));
            boolean keyword = chosen.stream().anyMatch(chunk -> "KEYWORD".equals(chunk.retrievalSource()) || "BOTH".equals(chunk.retrievalSource()));
            if (vector || keyword) metadata.put("retrievalSource", vector && keyword ? "BOTH" : vector ? "VECTOR" : "KEYWORD");
            String text = format(document, evidenceId, chosen);
            metadata.put("evidenceTokens", tokenizer.estimate(text));
            selected.add(Document.builder().id(evidenceId).text(text).metadata(metadata).build());
            for (EvidenceWindowChunk chunk : chosen) {
                usedChunks.add(key(document, chunk));
                usedText.add(normalize(chunk.text()));
            }
        }
        return new Result(List.copyOf(selected), new EvidenceSelectionTrace(ranked.status(), inputs.size(),
                selected.size(), inputTokens, tokens(selected), properties.maxTokens()), rerankMs,
                (System.nanoTime() - selectionStart) / 1_000_000);
    }

    private boolean used(Document document, EvidenceWindowChunk chunk, Set<String> chunks, Set<String> texts) {
        return chunks.contains(key(document, chunk)) || texts.contains(normalize(chunk.text()));
    }

    private String key(Document document, EvidenceWindowChunk chunk) {
        return document.getMetadata().getOrDefault("documentId", document.getId()) + ":" + chunk.index();
    }

    private String normalize(String text) { return text.strip().replaceAll("\\s+", " "); }

    private List<EvidenceWindowChunk> window(Document document) {
        if (document.getMetadata().get("windowChunks") instanceof List<?> chunks) {
            return chunks.stream().filter(EvidenceWindowChunk.class::isInstance).map(EvidenceWindowChunk.class::cast)
                    .filter(chunk -> chunk.text() != null && !chunk.text().isBlank())
                    .sorted(Comparator.comparingInt(EvidenceWindowChunk::index)).toList();
        }
        if (document.getText() == null || document.getText().isBlank()) return List.of();
        int index = ((Number) document.getMetadata().getOrDefault("chunkIndex", 0)).intValue();
        Long chunkId = document.getMetadata().get("chunkId") instanceof Number number ? number.longValue() : null;
        return List.of(new EvidenceWindowChunk(chunkId, index, document.getText()));
    }

    private String format(Document document, String evidenceId, List<EvidenceWindowChunk> chunks) {
        StringBuilder text = new StringBuilder();
        for (EvidenceWindowChunk chunk : chunks) {
            String next = chunk.text().strip();
            int overlap = 0;
            for (int length = Math.min(512, Math.min(text.length(), next.length())); length >= 16; length--) {
                if (text.substring(text.length() - length).equals(next.substring(0, length))) {
                    overlap = length;
                    break;
                }
            }
            if (!text.isEmpty() && overlap == 0) text.append('\n');
            text.append(next.substring(overlap));
        }
        return "Evidence ID: " + evidenceId + "\n文件名：" + document.getMetadata().getOrDefault("fileName", "")
                + "\n" + text;
    }

    private boolean fits(List<Document> selected, String next) {
        String context = selected.stream().map(Document::getText).collect(Collectors.joining(System.lineSeparator()));
        return tokenizer.estimate(context.isEmpty() ? next : context + System.lineSeparator() + next) <= properties.maxTokens();
    }

    private int tokens(List<Document> documents) {
        return tokenizer.estimate(documents.stream().map(Document::getText).collect(Collectors.joining(System.lineSeparator())));
    }

    public record Result(List<Document> documents, EvidenceSelectionTrace trace, long rerankMs, long selectionMs) { }
}
