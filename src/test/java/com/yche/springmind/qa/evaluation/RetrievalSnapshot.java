package com.yche.springmind.qa.evaluation;

import org.springframework.ai.document.Document;
import java.util.*;

/** Maps the small synthetic corpus to actual pre-rerank windows without changing their text or order. */
final class RetrievalSnapshot {
    private RetrievalSnapshot() {}

    static RerankEvaluation.Dataset assemble(RerankEvaluation.Dataset fixture,
            Map<String, List<Document>> captured, Map<Long, String> sourceByDocumentId, long groupId) {
        RerankEvaluation.validate(fixture);
        if (!new HashSet<>(sourceByDocumentId.values()).equals(fixture.documents().keySet())
                || sourceByDocumentId.size() != fixture.documents().size()) {
            throw new IllegalArgumentException("Expected a one-to-one uploaded fixture corpus");
        }
        Map<String, String> texts = new LinkedHashMap<>();
        List<RerankEvaluation.Example> cases = new ArrayList<>();
        for (var example : fixture.cases()) {
            List<Document> inputs = captured.get(example.id());
            if (inputs == null) throw new IllegalArgumentException("Missing captured question");
            fixture.documents().forEach((id, text) -> texts.put(key(example.id(), id), text));
            List<String> order = new ArrayList<>();
            for (Document input : inputs) {
                Map<String, Object> metadata = input.getMetadata();
                if (!(metadata.get("groupId") instanceof Number group) || group.longValue() != groupId
                        || !(metadata.get("documentId") instanceof Number document)) {
                    throw new IllegalArgumentException("Candidate outside the evaluation group");
                }
                String source = sourceByDocumentId.get(document.longValue());
                if (source == null) throw new IllegalArgumentException("Candidate outside the uploaded fixture corpus");
                String id = key(example.id(), source);
                if (order.contains(id)) throw new IllegalArgumentException("Multiple windows require manual label mapping");
                order.add(id);
                texts.put(id, input.getText());
            }
            Map<String, Integer> labels = new LinkedHashMap<>();
            example.relevance().forEach((source, grade) -> labels.put(key(example.id(), source), grade));
            cases.add(new RerankEvaluation.Example(example.id(), example.category(), example.query(), order, labels));
        }
        var snapshot = new RerankEvaluation.Dataset("RRF_SNAPSHOT", texts, cases);
        RerankEvaluation.validate(snapshot);
        return snapshot;
    }

    static String key(String questionId, String sourceId) { return questionId + "__" + sourceId; }
}
