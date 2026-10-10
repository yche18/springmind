package com.yche.springmind.qa.evaluation;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class OfflineRelabelTest {
    @Test
    void rejectsChangedQuestionOrSourceTextBeforeRescoring() {
        var original = new RerankEvaluation.Dataset("RRF_SNAPSHOT", Map.of("q__a", "Header Original"), List.of(
                new RerankEvaluation.Example("q", "test", "Question", List.of("q__a"), Map.of("q__a", 3))));
        for (var fixture : List.of(
                new RerankEvaluation.Dataset("SYNTHETIC", Map.of("a", "Changed"), List.of(new RerankEvaluation.Example("q", "test", "Question", List.of("a"), Map.of("a", 2)))),
                new RerankEvaluation.Dataset("SYNTHETIC", Map.of("a", "Original"), List.of(new RerankEvaluation.Example("q", "test", "Changed", List.of("a"), Map.of("a", 2)))))) {
            assertThatThrownBy(() -> OfflineRelabel.applyLabels(original, fixture)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void rejectsMismatchedInputOrderAndForeignOutput() throws Exception {
        var mapper = RerankEvaluationRun.datasetMapper();
        var root = Path.of("evaluation/rerank/results/northstar-2026-10-10");
        var snapshot = mapper.readValue(root.resolve("snapshot-v2.json").toFile(), RerankEvaluation.Dataset.class);
        var report = mapper.readTree(root.resolve("qwen-5s.json").toFile());
        for (String field : List.of("inputOrder", "outputOrder")) {
            var invalid = report.deepCopy();
            ((com.fasterxml.jackson.databind.node.ArrayNode) invalid.path("rows").get(0).path(field)).set(0, mapper.valueToTree("foreign"));
            assertThatThrownBy(() -> OfflineRelabel.rescore(snapshot, invalid)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void allRequiredPartialEvidenceHasGradeTwoButCompleteAndBackgroundEvidenceKeepTheirGrades() throws Exception {
        var fixture = RerankEvaluationRun.datasetMapper().readValue(Path.of("evaluation/rerank/sample.json").toFile(), RerankEvaluation.Dataset.class);
        for (var example : fixture.cases()) {
            if (example.category().startsWith("multi-evidence")) assertThat(example.relevance().values()).containsExactly(2, 2);
            if (example.id().equals("q16")) assertThat(example.relevance()).containsEntry("external-ai", 3).containsEntry("group-access", 1);
        }
    }
}
