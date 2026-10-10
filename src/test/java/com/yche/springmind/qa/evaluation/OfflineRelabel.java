package com.yche.springmind.qa.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yche.springmind.qa.rag.EvidenceReranker;
import org.springframework.ai.document.Document;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Reuses recorded orderings only; no HTTP client or model provider is constructed. */
final class OfflineRelabel {
    private OfflineRelabel() {}

    static RerankEvaluation.Dataset applyLabels(RerankEvaluation.Dataset original, RerankEvaluation.Dataset fixture) {
        RerankEvaluation.validate(original);
        RerankEvaluation.validate(fixture);
        if (original.cases().size() != fixture.cases().size()) throw new IllegalArgumentException("Question count changed");
        List<RerankEvaluation.Example> revised = new ArrayList<>();
        for (int index = 0; index < original.cases().size(); index++) {
            var old = original.cases().get(index);
            var labels = fixture.cases().get(index);
            if (!old.id().equals(labels.id()) || !old.query().equals(labels.query()) || !old.category().equals(labels.category())) {
                throw new IllegalArgumentException("Relabeling must not change question identity, text or category");
            }
            for (var source : fixture.documents().entrySet()) {
                String captured = original.documents().get(RetrievalSnapshot.key(old.id(), source.getKey()));
                if (captured == null || !captured.contains(source.getValue())) {
                    throw new IllegalArgumentException("Source text changed; cannot reuse recorded ranks");
                }
            }
            Map<String, Integer> grades = new LinkedHashMap<>();
            labels.relevance().forEach((source, grade) -> grades.put(RetrievalSnapshot.key(old.id(), source), grade));
            revised.add(new RerankEvaluation.Example(old.id(), old.category(), old.query(), old.candidates(), grades));
        }
        var result = new RerankEvaluation.Dataset(original.provenance(), original.documents(), revised);
        RerankEvaluation.validate(result);
        return result;
    }

    static ObjectNode rescore(RerankEvaluation.Dataset dataset, JsonNode originalReport) {
        if (originalReport.path("rows").size() != dataset.cases().size()) throw new IllegalArgumentException("Report row count changed");
        var mapper = RerankEvaluationRun.datasetMapper();
        AtomicInteger index = new AtomicInteger();
        EvidenceReranker recorded = (query, candidates) -> {
            int position = index.getAndIncrement();
            JsonNode row = originalReport.path("rows").get(position);
            if (!dataset.cases().get(position).id().equals(row.path("id").asText())
                    || !mapper.valueToTree(candidates.stream().map(Document::getId).toList()).equals(row.path("inputOrder"))) {
                throw new IllegalArgumentException("Recorded ranking does not match frozen inputs");
            }
            Map<String, Document> byId = candidates.stream().collect(Collectors.toMap(Document::getId, Function.identity()));
            List<Document> output = new ArrayList<>();
            row.path("outputOrder").forEach(id -> {
                Document document = byId.get(id.asText());
                if (document == null) throw new IllegalArgumentException("Recorded ranking contains foreign evidence");
                output.add(document);
            });
            return new EvidenceReranker.Result(output, row.path("status").asText());
        };
        var recomputed = RerankEvaluation.run(dataset, originalReport.path("k").asInt(), recorded, true);
        ObjectNode result = originalReport.deepCopy();
        JsonNode summary = mapper.valueToTree(recomputed.summary());
        for (String field : List.of("judgedAnswerableCases", "unanswerableCases", "candidateRecall", "baseline", "reranked", "delta")) {
            ((ObjectNode) result.path("summary")).set(field, summary.path(field));
        }
        for (int position = 0; position < recomputed.rows().size(); position++) {
            JsonNode row = mapper.valueToTree(recomputed.rows().get(position));
            for (String field : List.of("candidateRecall", "baseline", "reranked")) {
                ((ObjectNode) result.path("rows").get(position)).set(field, row.path(field));
            }
        }
        // Keep original timestamps, statuses, latency, cost and ordering; only judgments/metrics change.
        result.put("mode", "OFFLINE_RELABEL");
        result.put("originalMode", originalReport.path("mode").asText());
        result.put("newProviderRequests", 0);
        result.put("originalPlannedProviderRequests", originalReport.path("plannedProviderRequests").asInt());
        result.put("plannedProviderRequests", 0);
        result.put("annotationRevision", "complete-answer-v2");
        result.put("annotationStatus", "RUBRIC_ALIGNED_OTHER_JUDGMENTS_PENDING_REVIEW");
        return result;
    }
}
