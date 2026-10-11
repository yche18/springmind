package com.yche.springmind.qa.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Recomputes the saved comparison without starting infrastructure or model clients. */
class RecordedRetrievalAblationTest {
    private static final Path ROOT = Path.of("evaluation/retrieval/results/northstar-2026-10-10");

    private RetrievalAblation.Comparison recompute() throws Exception {
        var mapper = RerankEvaluationRun.datasetMapper();
        var capturePath = ROOT.resolve("capture.json");
        var capture = mapper.readValue(capturePath.toFile(), RetrievalAblation.Capture.class);
        var snapshot = RetrievalAblation.SOURCE.resolve("snapshot-v2.json");
        assertThat(capture.datasetSha256()).isEqualTo(RetrievalAblation.hash(snapshot));
        var dataset = mapper.readValue(snapshot.toFile(), RerankEvaluation.Dataset.class);
        var fixture = mapper.readValue(Path.of("evaluation/rerank/sample.json").toFile(), RerankEvaluation.Dataset.class);
        assertThat(OfflineRelabel.applyLabels(dataset, fixture)).isEqualTo(dataset);
        Map<String, JsonNode> reports = new LinkedHashMap<>();
        Map<String, String> hashes = new LinkedHashMap<>();
        for (String timeout : List.of("2s", "5s")) {
            Path path = RetrievalAblation.SOURCE.resolve("qwen-" + timeout + "-v2.json");
            String mode = "QWEN_" + timeout.toUpperCase(Locale.ROOT) + "_REUSED";
            reports.put(mode, mapper.readTree(path.toFile()));
            hashes.put(mode, RetrievalAblation.hash(path));
        }
        return RetrievalAblation.compare(dataset, capture, RetrievalAblation.hash(capturePath), reports, hashes);
    }

    @Test
    void savedReportRecomputesExactlyAndContainsOnlyExpectedArtifacts() throws Exception {
        var mapper = RerankEvaluationRun.datasetMapper();
        assertThat(mapper.valueToTree(recompute()).toString()).isEqualTo(mapper.readTree(ROOT.resolve("comparison.json").toFile()).toString());
        try (var files = Files.list(ROOT)) {
            assertThat(files.map(path -> path.getFileName().toString()).toList())
                    .containsExactlyInAnyOrder("README.md", "capture.json", "comparison.json", "report.html");
        }
    }

    @Test
    void preservesPositiveAndNegativeCasesAndLanguageLimitations() throws Exception {
        var report = recompute();
        assertThat(report.newProviderRequests()).isZero();
        assertThat(report.rows()).hasSize(30);
        assertThat(report.rows().stream().filter(row -> row.metrics().get("VECTOR") != null
                && row.metrics().get("HYBRID").ndcg() > row.metrics().get("VECTOR").ndcg() + 1e-12).map(RetrievalAblation.ScoredRow::id))
                .containsExactly("q20");
        assertThat(report.rows().stream().filter(row -> row.metrics().get("VECTOR") != null
                && row.metrics().get("HYBRID").ndcg() < row.metrics().get("VECTOR").ndcg() - 1e-12).map(RetrievalAblation.ScoredRow::id))
                .containsExactly("q06");
        assertThat(report.rows().stream().filter(row -> row.category().endsWith("-zh") && row.keywordHits() == 0)).hasSize(14);
        assertThat(report.rows()).allSatisfy(row -> assertThat(row.vectorHits()).isEqualTo(16));
        assertThat(report.groups().get("ALL").get("VECTOR").multiEvidenceComplete()).isEqualTo(3);
        assertThat(report.groups().get("ALL").get("HYBRID").multiEvidenceComplete()).isEqualTo(4);
        assertThat(report.groups().get("ALL").get("QWEN_5S_REUSED").multiEvidenceComplete()).isEqualTo(6);
        for (var row : report.rows().stream().filter(row -> row.category().startsWith("unanswerable-")).toList()) {
            assertThat(row.metrics().values()).containsOnlyNulls();
        }
    }

    @Test
    void htmlMatchesDataIncludesAllCasesAndEscapesUntrustedText() throws Exception {
        var report = recompute();
        String html = RetrievalAblationReport.render(report);
        assertThat(Files.readString(ROOT.resolve("report.html")).replace("\r\n", "\n")).isEqualTo(html.replace("\r\n", "\n"));
        assertThat(html).contains("1 题改善，1 题退化，22 道有答案题不变", "无答案题", "不是最终回答准确率");
        report.rows().forEach(row -> assertThat(html).contains("id='" + row.id() + "'"));
        var rows = new ArrayList<>(report.rows());
        var first = rows.getFirst();
        rows.set(0, new RetrievalAblation.ScoredRow(first.id(), first.category(), "<script>alert(1)</script>", first.relevance(),
                first.orders(), first.metrics(), first.candidateRecall(), first.allRequiredEvidence(), first.vectorHits(), first.keywordHits()));
        var unsafe = new RetrievalAblation.Comparison(report.mode(), report.datasetSha256(), report.captureSha256(),
                report.reusedReportSha256(), report.k(), report.newProviderRequests(), report.groups(), rows);
        assertThat(RetrievalAblationReport.render(unsafe)).contains("&lt;script&gt;").doesNotContain("<script>");
    }
}
