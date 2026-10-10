package com.yche.springmind.qa.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Replays SQL/cosine verification, metrics and reports without models or a database. */
class RecordedEmbeddingComparisonTest {
    private static final Path ROOT = Path.of("evaluation/retrieval/results/harborview-bgem3-2026-10-10");
    private final com.fasterxml.jackson.databind.ObjectMapper mapper = RerankEvaluationRun.datasetMapper();

    @Test
    void allSqlRankingsMetricsAndPagesRecomputeOffline() throws Exception {
        assertThat(RetrievalAblation.hash(ROOT.resolve("capture.json")))
                .isEqualTo("80738762128e53da1c02a3c9e9b3d489b6d4e8f7e0d0507c5dd7128c34d6c98a");
        var comparison = EmbeddingComparison.compare(ROOT);
        assertThat((JsonNode) mapper.valueToTree(comparison)).isEqualTo(mapper.readTree(ROOT.resolve("comparison.json").toFile()));
        assertThat(Files.readString(ROOT.resolve("report.html")).replace("\r\n", "\n"))
                .isEqualTo(EmbeddingComparisonReportRun.render(ROOT, comparison).replace("\r\n", "\n"))
                .contains("人工确认", "云端调用为 0", "近似索引", "退化").doesNotContain("<script>");
        assertThat(comparison.annotationStatus()).isEqualTo("HUMAN_REVIEWED");
        assertThat(comparison.cloudRequests()).isZero();
        assertThat(comparison.localEmbeddingRequests()).isEqualTo(258);
        assertThat(comparison.historicalVectorOrderMatches()).isEqualTo(24);
        assertThat(comparison.historicalVectorTop50Overlap()).isEqualTo(1);
        assertThat(comparison.rows()).hasSize(24);
        assertThat(comparison.groups().get("ALL").values()).allSatisfy(summary -> {
            assertThat(summary.answerable()).isEqualTo(20);
            assertThat(summary.complementaryCases()).isEqualTo(6);
        });
        assertThat(comparison.groups().get("unanswerable").values()).allSatisfy(summary -> {
            assertThat(summary.metrics()).isNull();
            assertThat(summary.factCoverage5()).isNull();
        });
    }

    @Test
    void frozenKeywordReferenceAndRrfAreReusedWithoutNewCalls() throws Exception {
        var comparison = EmbeddingComparison.compare(ROOT);
        var original = FilenameAblation.original();
        var corpus = FilenameAblation.corpus();
        var capture = mapper.readValue(ROOT.resolve("capture.json").toFile(), EmbeddingComparison.Capture.class);
        for (int i = 0; i < comparison.rows().size(); i++) {
            var row = comparison.rows().get(i);
            var keyword = original.rows().get(i).keyword();
            assertThat(row.rankings().get("KEYWORD_REFERENCE")).isEqualTo(keyword.stream().map(ExtensionEvaluation.Hit::chunk).toList());
            for (String model : EmbeddingComparison.MODELS) {
                assertThat(row.rankings().get(model + "_HYBRID"))
                        .isEqualTo(ExtensionEvaluation.orders(capture.rows().get(i).hits().get(model), keyword, corpus.chunks()).get("HYBRID"));
                assertThat(row.scores().get(model + "_HYBRID").recall50()).isNull();
                assertThat(row.rankings().get(model + "_HYBRID")).hasSizeLessThanOrEqualTo(30);
                assertThat(row.rankings().get(model + "_VECTOR")).hasSize(50);
            }
        }
    }

    @Test
    void allLocalVectorsAreCompleteValidAndDimensionSeparated() throws Exception {
        var models = EmbeddingComparison.models();
        for (String mode : EmbeddingComparison.MODELS) {
            var vectors = mapper.readValue(ROOT.resolve(mode.toLowerCase(Locale.ROOT) + "-vectors.json").toFile(), EmbeddingComparison.Vectors.class);
            EmbeddingComparison.validateVectors(vectors, models.get(mode), ExtensionFixture.read(), FilenameAblation.corpus());
            assertThat(vectors.documents()).hasSize(105);
            assertThat(vectors.queries()).hasSize(24);
            assertThat(vectors.embeddingMs()).hasSize(129);
            assertThat(vectors.model().digest()).hasSize(64);
            boolean loaded = false;
            for (var runtime : vectors.loadedRuntime().path("models")) if (runtime.path("name").asText().equals(vectors.model().tag())) {
                loaded = true;
                assertThat(runtime.path("digest").asText()).isEqualTo(vectors.model().digest());
                assertThat(runtime.path("size_vram").asLong()).isZero();
            }
            assertThat(loaded).isTrue();
        }
    }

    @Test
    void capturesBothCrossLanguageGainsAndTheUnmodifiedRegression() throws Exception {
        var comparison = EmbeddingComparison.compare(ROOT);
        var overall = comparison.groups().get("ALL");
        assertThat(overall.get("CURRENT_VECTOR").metrics().recall()).isEqualTo(0.775);
        assertThat(overall.get("BGE_M3_VECTOR").metrics().recall()).isEqualTo(1);
        assertThat(overall.get("BGE_M3_HYBRID").metrics().recall()).isEqualTo(0.975);
        assertThat(comparison.groups().get("cross-language").get("BGE_M3_VECTOR").metrics().recall()).isEqualTo(1);
        var regressions = comparison.rows().stream().filter(row -> row.scores().get("CURRENT_VECTOR").metrics() != null)
                .filter(row -> row.scores().get("BGE_M3_VECTOR").metrics().ndcg() < row.scores().get("CURRENT_VECTOR").metrics().ndcg() - 1e-12)
                .map(EmbeddingComparison.Row::id).toList();
        assertThat(regressions).containsExactly("e12");
        var expense = comparison.rows().stream().filter(row -> row.id().equals("e16")).findFirst().orElseThrow();
        assertThat(expense.rankings().get("BGE_M3_VECTOR").indexOf("expense-deadline-en--c000")).isZero();
        assertThat(expense.rankings().get("BGE_M3_HYBRID").indexOf("expense-deadline-en--c000")).isEqualTo(5);
    }
}
