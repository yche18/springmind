package com.yche.springmind.qa.evaluation;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Verifies the new provisional experiment without contacting models or local infrastructure. */
class RecordedExtensionTest {
    private static final Path ROOT = Path.of("evaluation/retrieval/results/harborview-2026-10-10");

    private ExtensionEvaluation.Corpus corpus() throws Exception {
        return RerankEvaluationRun.datasetMapper().readValue(ROOT.resolve("corpus.json").toFile(), ExtensionEvaluation.Corpus.class);
    }

    private ExtensionEvaluation.Capture capture() throws Exception {
        return RerankEvaluationRun.datasetMapper().readValue(ROOT.resolve("capture.json").toFile(), ExtensionEvaluation.Capture.class);
    }

    private ExtensionEvaluation.Comparison recompute() throws Exception {
        return ExtensionEvaluation.compare(ExtensionFixture.read(), corpus(), capture(), RetrievalAblation.hash(ROOT.resolve("corpus.json")),
                RetrievalAblation.hash(ROOT.resolve("capture.json")));
    }

    @Test
    void allMetricsRecomputeExactlyFromFrozenPreQueryJudgments() throws Exception {
        var mapper = RerankEvaluationRun.datasetMapper();
        var actual = recompute();
        assertThat(mapper.valueToTree(actual).toString()).isEqualTo(mapper.readTree(ROOT.resolve("comparison.json").toFile()).toString());
        assertThat(actual.annotationStatus()).isEqualTo("PENDING_HUMAN_REVIEW");
        assertThat(actual.newProviderRequests()).isZero();
        assertThat(actual.rows()).hasSize(24);
        assertThat(Instant.parse(corpus().frozenAt())).isBefore(Instant.parse(capture().completedAt()));
        for (var summary : actual.groups().get("ALL").values()) {
            assertThat(summary.answerable()).isEqualTo(20);
            assertThat(summary.unanswerable()).isEqualTo(4);
            assertThat(summary.complementaryCases()).isEqualTo(6);
            assertThat(summary.top5FactCoverage()).isBetween(0.0, 1.0);
        }
        for (var row : actual.rows().stream().filter(row -> row.category().equals("unanswerable")).toList())
            assertThat(row.scores().values()).allSatisfy(score -> {
                assertThat(score.chunkMetrics()).isNull();
                assertThat(score.top5FactCoverage()).isNull();
            });
    }

    @Test
    void corpusContainsOnlyFrozenSourcesAndSplitHandbooks() throws Exception {
        var fixture = ExtensionFixture.read();
        var corpus = corpus();
        assertThat(corpus.fixtureSha256()).isEqualTo(RetrievalAblation.hash(ExtensionFixture.PATH));
        assertThat(corpus.chunks().size()).isGreaterThan(50);
        assertThat(corpus.chunks().values().stream().map(ExtensionEvaluation.Chunk::document).distinct())
                .containsExactlyInAnyOrderElementsOf(fixture.documents().keySet());
        assertThat(corpus.chunks().values().stream().map(ExtensionEvaluation.Chunk::chunkId).distinct().count()).isEqualTo(corpus.chunks().size());
        for (var chunk : corpus.chunks().values()) assertThat(ExtensionFixture.normalize(fixture.documents().get(chunk.document())))
                .contains(ExtensionFixture.normalize(chunk.text()));
        for (String id : List.of("restore-guide-en", "release-guide-zh"))
            assertThat(corpus.chunks().values().stream().filter(chunk -> chunk.document().equals(id)).count()).isGreaterThan(1);
        for (var row : capture().rows()) {
            assertThat(row.vector().size()).isLessThanOrEqualTo(50);
            assertThat(row.keyword().size()).isLessThanOrEqualTo(50);
            for (String mode : ExtensionEvaluation.MODES) {
                assertThat(row.orders().get(mode).size()).isLessThanOrEqualTo(30);
                assertThat(row.selectedWindows().get(mode).size()).isLessThanOrEqualTo(5);
            }
        }
    }

    @Test
    void reviewExposesEveryPendingJudgmentAndMatchesGeneratedPages() throws Exception {
        var mapper = RerankEvaluationRun.datasetMapper();
        var corpus = corpus();
        var fixture = ExtensionFixture.read();
        var review = mapper.readTree(ROOT.resolve("review-template.json").toFile());
        assertThat(review).hasSize(24);
        for (var row : review) {
            assertThat(row.path("status").asText()).isEqualTo("PENDING_HUMAN_REVIEW");
            assertThat(row.path("chunkGrades")).hasSize(corpus.chunks().size());
            assertThat(row.path("chunkGrades")).isEqualTo(mapper.valueToTree(corpus.grades().get(row.path("id").asText())));
        }
        assertThat(normalize(Files.readString(ROOT.resolve("review.html")))).isEqualTo(normalize(ExtensionReport.review(fixture, corpus)));
        assertThat(normalize(Files.readString(ROOT.resolve("report.html")))).isEqualTo(normalize(ExtensionReport.report(fixture, recompute(), capture())));
        try (var files = Files.list(ROOT)) {
            assertThat(files.map(path -> path.getFileName().toString()).toList()).containsExactlyInAnyOrder(
                    "README.md", "corpus.json", "capture.json", "comparison.json", "review.html", "report.html", "review-template.json");
        }
    }

    private static String normalize(String text) { return text.replace("\r\n", "\n"); }
}
