package com.yche.springmind.qa.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class RetrievalAblationTest {
    private RerankEvaluation.Dataset dataset() throws Exception {
        return RerankEvaluationRun.datasetMapper().readValue(RetrievalAblation.SOURCE.resolve("snapshot-v2.json").toFile(), RerankEvaluation.Dataset.class);
    }

    private Map<String, JsonNode> reports() throws Exception {
        Map<String, JsonNode> reports = new LinkedHashMap<>();
        for (String timeout : List.of("2s", "5s")) reports.put("QWEN_" + timeout.toUpperCase(Locale.ROOT) + "_REUSED",
                RerankEvaluationRun.datasetMapper().readTree(RetrievalAblation.SOURCE.resolve("qwen-" + timeout + "-v2.json").toFile()));
        return reports;
    }

    private RetrievalAblation.Capture capture(List<RetrievalAblation.CapturedRow> rows) throws Exception {
        return new RetrievalAblation.Capture("test", RetrievalAblation.hash(RetrievalAblation.SOURCE.resolve("snapshot-v2.json")),
                "fixture", "model", "digest", 50, 30, 60, 1, 4096, 0, "ORIGINAL_QUERY_NO_PLANNER", "TEST", rows);
    }

    private List<RetrievalAblation.CapturedRow> rows() throws Exception {
        return dataset().cases().stream().map(example -> {
            List<RetrievalAblation.Hit> hits = new ArrayList<>();
            for (int i = 0; i < example.candidates().size(); i++) hits.add(new RetrievalAblation.Hit(i + 1,
                    example.candidates().get(i).substring(example.id().length() + 2), 1.0 / (i + 1)));
            return new RetrievalAblation.CapturedRow(example.id(), hits, List.of(),
                    Map.of("KEYWORD", List.<String>of(), "VECTOR", example.candidates(), "HYBRID", example.candidates()), 1, 1);
        }).toList();
    }

    private Map<String, String> hashes() { return Map.of("QWEN_2S_REUSED", "2s", "QWEN_5S_REUSED", "5s"); }

    @Test
    void emptyRetrievalIsAMissButUnanswerableCasesAreExcluded() throws Exception {
        var report = RetrievalAblation.compare(dataset(), capture(rows()), "capture", reports(), hashes());
        var keyword = report.groups().get("ALL").get("KEYWORD");
        assertThat(keyword.questions()).isEqualTo(30);
        assertThat(keyword.answerable()).isEqualTo(24);
        assertThat(keyword.unanswerable()).isEqualTo(6);
        assertThat(keyword.metrics()).isEqualTo(new RerankEvaluation.Metrics(0, 0, 0, 0));
        assertThat(keyword.multiEvidenceQuestions()).isEqualTo(6);
        assertThat(keyword.multiEvidenceCompleteRate()).isZero();
        assertThat(report.groups().get("unanswerable").get("KEYWORD").metrics()).isNull();
        assertThat(report.groups().get("zh").get("KEYWORD").answerable()).isEqualTo(12);
        assertThat(report.groups().get("en").get("KEYWORD").answerable()).isEqualTo(12);
    }

    @Test
    void preservesOldMetricsAndCountsComplementaryEvidenceNotBackground() throws Exception {
        var originals = reports();
        var report = RetrievalAblation.compare(dataset(), capture(rows()), "capture", originals, hashes());
        for (var entry : originals.entrySet()) assertThat(RerankEvaluationRun.datasetMapper()
                .valueToTree(report.groups().get("ALL").get(entry.getKey()).metrics()).toString())
                .isEqualTo(entry.getValue().path("summary").path("reranked").toString());
        assertThat(report.groups().get("ALL").get("HYBRID").multiEvidenceComplete()).isEqualTo(4);
        assertThat(report.groups().get("ALL").get("QWEN_5S_REUSED").multiEvidenceComplete()).isEqualTo(6);
        assertThat(report.rows().stream().filter(row -> row.id().equals("q16")).findFirst().orElseThrow()
                .allRequiredEvidence().get("HYBRID")).isNull();
    }

    @Test
    void refusesHybridRankDriftInsteadOfSilentlyReusingQwen() throws Exception {
        var rows = new ArrayList<>(rows());
        var first = rows.getFirst();
        var orders = new LinkedHashMap<>(first.orders());
        var changed = new ArrayList<>(orders.get("HYBRID"));
        Collections.swap(changed, 0, 1);
        orders.put("HYBRID", changed);
        rows.set(0, new RetrievalAblation.CapturedRow(first.id(), first.vectorHits(), first.keywordHits(), orders, 1, 1));
        assertThatThrownBy(() -> RetrievalAblation.compare(dataset(), capture(rows), "capture", reports(), hashes()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("rank drift");
    }

    @Test
    void rejectsWrongHashMissingTimeoutRoundAndDuplicateRerankEvidence() throws Exception {
        var reports = reports();
        ((ObjectNode) reports.get("QWEN_5S_REUSED")).put("datasetSha256", "changed");
        assertThatThrownBy(() -> RetrievalAblation.compare(dataset(), capture(rows()), "capture", reports, hashes()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Incompatible");
        reports.remove("QWEN_5S_REUSED");
        assertThatThrownBy(() -> RetrievalAblation.compare(dataset(), capture(rows()), "capture", reports, hashes()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Both original");
        var duplicate = reports();
        var order = (com.fasterxml.jackson.databind.node.ArrayNode) duplicate.get("QWEN_5S_REUSED").path("rows").get(0).path("outputOrder");
        order.set(1, order.get(0));
        assertThatThrownBy(() -> RetrievalAblation.compare(dataset(), capture(rows()), "capture", duplicate, hashes()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("permutation");
    }

    @Test
    void rejectsForeignAndDuplicateSingleChannelCandidates() throws Exception {
        for (var invalid : List.of(List.of("q02__travel-domestic"), List.of("q01__travel-domestic", "q01__travel-domestic"))) {
            var rows = new ArrayList<>(rows());
            var first = rows.getFirst();
            var orders = new LinkedHashMap<>(first.orders());
            orders.put("VECTOR", invalid);
            rows.set(0, new RetrievalAblation.CapturedRow(first.id(), List.of(), List.of(), orders, 1, 1));
            assertThatThrownBy(() -> RetrievalAblation.compare(dataset(), capture(rows), "capture", reports(), hashes()))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Raw channel order");
        }
    }

    @Test
    void verifiesReciprocalRankFusionAndDeterministicChunkTieBreak() {
        var vector = List.of(new RetrievalAblation.Hit(2, "a", 0.9), new RetrievalAblation.Hit(1, "b", 0.8));
        var keyword = List.of(new RetrievalAblation.Hit(1, "b", 5), new RetrievalAblation.Hit(2, "a", 4));
        var row = new RetrievalAblation.CapturedRow("q1", vector, keyword,
                Map.of("VECTOR", List.of("q1__a", "q1__b"), "KEYWORD", List.of("q1__b", "q1__a"),
                        "HYBRID", List.of("q1__b", "q1__a")), 1, 1);
        assertThatCode(() -> RetrievalAblation.verifyRawRanks(row, 30, 60)).doesNotThrowAnyException();
        var changed = new LinkedHashMap<>(row.orders());
        changed.put("HYBRID", List.of("q1__a", "q1__b"));
        assertThatThrownBy(() -> RetrievalAblation.verifyRawRanks(new RetrievalAblation.CapturedRow("q1", vector, keyword, changed, 1, 1), 30, 60))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("RRF");
    }
}
