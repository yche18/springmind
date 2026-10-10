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
    @ValueSource(strings = {"qwen-2s.json", "qwen-5s.json", "qwen-2s-v2.json", "qwen-5s-v2.json"})
    void savedMetricsRecomputeExactlyFromFrozenLabelsAndRecordedRankings(String name) throws Exception {
        var mapper = RerankEvaluationRun.datasetMapper();
        byte[] bytes = Files.readAllBytes(ROOT.resolve(name.contains("-v2") ? "snapshot-v2.json" : "snapshot.json"));
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
        var revised = mapper.readValue(ROOT.resolve("snapshot-v2.json").toFile(), RerankEvaluation.Dataset.class);
        var examples = revised.cases().stream().collect(Collectors.toMap(RerankEvaluation.Example::id, Function.identity()));
        assertThat(review).hasSize(30);
        String html = Files.readString(ROOT.resolve("review.html"));
        assertThat(html).contains("人工复核已通过", "本轮复核已完成", "review-approval.json")
                .doesNotContain("待确认：", "标签尚未经过人工确认", "□ 保持初稿");
        Set<String> ids = new HashSet<>();
        for (var row : review) {
            assertThat(ids.add(row.path("id").asText())).isTrue();
            assertThat(row.path("status").asText()).isEqualTo("HUMAN_REVIEWED");
            assertThat(row.path("sourceGrades")).hasSize(16);
            row.path("sourceGrades").fields().forEachRemaining(grade -> assertThat(grade.getValue().asInt())
                    .isEqualTo(examples.get(row.path("id").asText()).relevance()
                            .getOrDefault(RetrievalSnapshot.key(row.path("id").asText(), grade.getKey()), 0)));
            assertThat(html).contains("id='" + row.path("id").asText() + "'");
        }
        try (var files = Files.list(ROOT)) {
            assertThat(files.map(path -> path.getFileName().toString()).toList()).containsExactlyInAnyOrder(
                    "README.md", "snapshot.json", "qwen-2s.json", "qwen-5s.json", "review.html", "review-template.json",
                    "snapshot-v2.json", "qwen-2s-v2.json", "qwen-5s-v2.json", "review-approval.json");
        }
    }

    @Test
    void v2ChangesOnlyAuthorizedGradesAndPreservesMeasurementsAndRankings() throws Exception {
        var mapper = RerankEvaluationRun.datasetMapper();
        var original = mapper.readValue(ROOT.resolve("snapshot.json").toFile(), RerankEvaluation.Dataset.class);
        var revised = mapper.readValue(ROOT.resolve("snapshot-v2.json").toFile(), RerankEvaluation.Dataset.class);
        assertThat(revised.documents()).isEqualTo(original.documents());
        Set<String> changed = Set.of("q17", "q18", "q19", "q20", "q21", "q24");
        for (int index = 0; index < original.cases().size(); index++) {
            var before = original.cases().get(index);
            var after = revised.cases().get(index);
            assertThat(after.id()).isEqualTo(before.id());
            assertThat(after.query()).isEqualTo(before.query());
            assertThat(after.candidates()).isEqualTo(before.candidates());
            if (changed.contains(after.id())) assertThat(after.relevance().values()).containsExactly(2, 2);
            else assertThat(after.relevance()).isEqualTo(before.relevance());
        }
        for (String name : List.of("qwen-2s", "qwen-5s")) {
            byte[] bytes = Files.readAllBytes(ROOT.resolve(name + ".json"));
            var old = mapper.readTree(bytes);
            var current = mapper.readTree(ROOT.resolve(name + "-v2.json").toFile());
            assertThat(current.path("mode").asText()).isEqualTo("OFFLINE_RELABEL");
            assertThat(current.path("newProviderRequests").asInt()).isZero();
            assertThat(current.path("annotationStatus").asText()).isEqualTo("HUMAN_REVIEWED");
            assertThat(current.path("humanReview").path("datasetSha256")).isEqualTo(current.path("datasetSha256"));
            assertThat(current.path("sourceReportSha256").asText()).isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
            assertThat(current.path("labelChanges")).hasSize(11);
            for (String field : List.of("statuses", "latencyP50Ms", "latencyP95Ms")) {
                assertThat(current.path("summary").path(field)).isEqualTo(old.path("summary").path(field));
            }
            for (int index = 0; index < original.cases().size(); index++) {
                for (String field : List.of("id", "inputOrder", "outputOrder", "status", "latencyMs")) {
                    assertThat(current.path("rows").get(index).path(field)).isEqualTo(old.path("rows").get(index).path(field));
                }
                for (String side : List.of("baseline", "reranked")) {
                    for (String metric : List.of("hit", "recall", "mrr")) {
                        assertThat(current.path("rows").get(index).path(side).path(metric))
                                .isEqualTo(old.path("rows").get(index).path(side).path(metric));
                    }
                }
            }
        }
    }
}
