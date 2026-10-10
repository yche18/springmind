package com.yche.springmind.qa.evaluation;

import com.yche.springmind.qa.rag.EvidenceReranker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.document.Document;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import static org.assertj.core.api.Assertions.*;

/** Verifies recorded arithmetic offline; never calls a model or local infrastructure. */
class RecordedEvaluationTest {
    private static final Path ROOT = Path.of("evaluation/rerank/results/northstar-2026-10-10");

    @ParameterizedTest
    @ValueSource(strings = {"qwen-2s.json", "qwen-5s.json"})
    void savedMetricsRecomputeExactlyFromFrozenLabelsAndRecordedRankings(String name) throws Exception {
        var mapper = RerankEvaluationRun.datasetMapper();
        byte[] bytes = Files.readAllBytes(ROOT.resolve("snapshot.json"));
        var dataset = mapper.readValue(bytes, RerankEvaluation.Dataset.class);
        var report = mapper.readTree(ROOT.resolve(name).toFile());
        assertThat(report.path("datasetSha256").asText()).isEqualTo(
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        var index = new AtomicInteger();
        EvidenceReranker recorded = (query, candidates) -> {
            var row = report.path("rows").get(index.getAndIncrement());
            List<String> inputIds = new ArrayList<>();
            row.path("inputOrder").forEach(id -> inputIds.add(id.asText()));
            assertThat(candidates).extracting(Document::getId).containsExactlyElementsOf(inputIds);
            Map<String, Document> byId = candidates.stream().collect(Collectors.toMap(Document::getId, Function.identity()));
            List<Document> ordered = new ArrayList<>();
            row.path("outputOrder").forEach(id -> ordered.add(byId.get(id.asText())));
            assertThat(ordered).doesNotContainNull();
            return new EvidenceReranker.Result(ordered, row.path("status").asText());
        };
        var recomputed = RerankEvaluation.run(dataset, report.path("k").asInt(), recorded, true);
        assertThat(index).hasValue(30);
        assertThat(mapper.valueToTree(recomputed.summary().baseline()).toString()).isEqualTo(report.path("summary").path("baseline").toString());
        assertThat(mapper.valueToTree(recomputed.summary().reranked()).toString()).isEqualTo(report.path("summary").path("reranked").toString());
        assertThat(mapper.valueToTree(recomputed.summary().delta()).toString()).isEqualTo(report.path("summary").path("delta").toString());
        assertThat(mapper.valueToTree(recomputed.summary().statuses()).toString()).isEqualTo(report.path("summary").path("statuses").toString());
    }

    @Test
    void reviewCoversEverySourceForEveryQuestionAndContainsNoAccountArtifact() throws Exception {
        var mapper = RerankEvaluationRun.datasetMapper();
        var review = mapper.readTree(ROOT.resolve("review-template.json").toFile());
        assertThat(review).hasSize(30);
        String html = Files.readString(ROOT.resolve("review.html"));
        Set<String> ids = new HashSet<>();
        for (var row : review) {
            assertThat(ids.add(row.path("id").asText())).isTrue();
            assertThat(row.path("status").asText()).isEqualTo("PENDING_HUMAN_REVIEW");
            assertThat(row.path("sourceGrades")).hasSize(16);
            assertThat(html).contains("id='" + row.path("id").asText() + "'");
        }
        try (var files = Files.list(ROOT)) {
            assertThat(files.map(path -> path.getFileName().toString()).toList()).containsExactlyInAnyOrder(
                    "README.md", "snapshot.json", "qwen-2s.json", "qwen-5s.json", "review.html", "review-template.json");
        }
    }
}
