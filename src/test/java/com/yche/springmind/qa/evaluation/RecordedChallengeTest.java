package com.yche.springmind.qa.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Reconstructs all measurements from recorded local outputs without service calls. */
class RecordedChallengeTest {
    private static ChallengeEvaluation.Comparison result;
    @BeforeAll static void replay() throws Exception { result = ChallengeEvaluation.compare(); }

    @Test
    void metricsPagesAndPendingReviewStatusReproduceOffline() throws Exception {
        var mapper = RerankEvaluationRun.datasetMapper();
        var root = ChallengeData.OUTPUT;
        assertThat((JsonNode) mapper.valueToTree(result)).isEqualTo(mapper.readTree(root.resolve("comparison.json").toFile()));
        assertThat(Files.readString(root.resolve("report.html"))).isEqualTo(ChallengeReportRun.report(result));
        assertThat(Files.readString(root.resolve("review.html"))).isEqualTo(ChallengeReportRun.review(result))
                .contains("PENDING_HUMAN_REVIEW", "3780", "c24", "全库初始分级").doesNotContain("<script>");
        assertThat(result.annotationStatus()).isEqualTo("PENDING_HUMAN_REVIEW");
        assertThat(result.capture().cloudRequests()).isZero();
        assertThat(result.capture().localEmbeddingRequests()).isEqualTo(282);
        assertThat(result.capture().keywordRequests()).isEqualTo(72);
        assertThat(result.rows()).hasSize(36);
        for (var q : mapper.readTree(root.resolve("review-template.json").toFile()).path("questions")) assertThat(q.path("confirmed").asBoolean()).isFalse();
    }

    @Test
    void everyRrfOrderMatchesIndependentArithmetic() throws Exception {
        var chunks = ChallengeData.corpus().chunks();
        for (var row : result.rows()) for (String model : EmbeddingComparison.MODELS) for (String field : List.of("BODY", "FILENAME")) {
            var v = row.rankings().get(model + "_VECTOR");
            var k = row.rankings().get("KEYWORD_" + field);
            Set<String> pool = new HashSet<>(v); pool.addAll(k);
            var expected = pool.stream().sorted(Comparator.<String>comparingDouble(id -> rankScore(v, id) + rankScore(k, id)).reversed()
                    .thenComparingLong(id -> chunks.get(id).chunkId())).limit(30).toList();
            assertThat(row.rankings().get(model + "_" + field + "_RRF")).isEqualTo(expected);
            assertThat(row.scores().get(model + "_" + field + "_RRF").recall50()).isNull();
        }
    }

    @Test
    void unionBudgetsExclusivesAndLossesMatchSetArithmetic() throws Exception {
        var corpus = ChallengeData.corpus();
        for (var row : result.rows()) for (String model : EmbeddingComparison.MODELS) for (String field : List.of("BODY", "FILENAME")) {
            String key = model + "_" + field;
            for (int budget : List.of(25, 50)) {
                var u = row.unions().get(key + "_U" + budget);
                var v = new HashSet<>(row.rankings().get(model + "_VECTOR").stream().limit(budget).toList());
                var k = new HashSet<>(row.rankings().get("KEYWORD_" + field).stream().limit(budget).toList());
                Set<String> all = new HashSet<>(v); all.addAll(k);
                assertThat(u.candidates()).containsExactlyInAnyOrderElementsOf(all).doesNotHaveDuplicates().hasSize(u.size());
                assertThat(u.size()).isLessThanOrEqualTo(2 * budget);
                Set<String> positives = new HashSet<>();
                corpus.grades().get(row.id()).forEach((id, grade) -> { if (grade > 0) positives.add(id); });
                var relevant = all.stream().filter(positives::contains).toList();
                if (!positives.isEmpty()) assertThat(u.recall()).isEqualTo((double) relevant.size() / positives.size());
                assertThat(u.keywordExclusiveRelevant()).containsExactlyInAnyOrderElementsOf(relevant.stream().filter(id -> !v.contains(id)).toList());
                assertThat(u.vectorExclusiveRelevant()).containsExactlyInAnyOrderElementsOf(relevant.stream().filter(id -> !k.contains(id)).toList());
                assertThat(u.lostAtRrf30()).containsExactlyInAnyOrderElementsOf(relevant.stream().filter(id -> !row.rankings().get(key + "_RRF").contains(id)).toList());
                assertThat(u.lostAtRrf5()).containsExactlyInAnyOrderElementsOf(relevant.stream().filter(id -> !row.rankings().get(key + "_RRF").stream().limit(5).toList().contains(id)).toList());
            }
            assertThat(row.unions().get(key + "_U50").candidates()).containsAll(row.unions().get(key + "_U25").candidates());
        }
    }

    @Test
    void unanswerableQueriesAreNotSuccessfulRefusalsOrZerosInTheMean() {
        assertThat(result.groups().get("ALL").values()).allSatisfy(s -> {
            assertThat(s.cases()).isEqualTo(36); assertThat(s.answerable()).isEqualTo(30); assertThat(s.complementaryCases()).isEqualTo(6);
        });
        assertThat(result.groups().get("unanswerable").values()).allSatisfy(s -> {
            assertThat(s.metrics()).isNull(); assertThat(s.factCoverage5()).isNull(); assertThat(s.recall30()).isNull();
        });
        assertThat(result.unionGroups().get("unanswerable").values()).allSatisfy(s -> {
            assertThat(s.recall()).isNull(); assertThat(s.factCoverage()).isNull(); assertThat(s.answerable()).isZero();
        });
    }

    @Test
    void isolatedIndexSnapshotContainsExactlyFrozenTextAndFilenames() throws Exception {
        var mapper = RerankEvaluationRun.datasetMapper();
        var f = ChallengeData.fixture(); var corpus = ChallengeData.corpus();
        var snapshot = mapper.readTree(ChallengeData.OUTPUT.resolve("index-snapshot.json").toFile());
        assertThat(snapshot.path("hits").path("total").path("value").asInt()).isEqualTo(105);
        Set<String> seen = new HashSet<>();
        for (var hit : snapshot.path("hits").path("hits")) {
            String id = hit.path("_id").asText();
            assertThat(seen.add(id)).isTrue();
            var chunk = corpus.chunks().get(id); assertThat(chunk).isNotNull();
            var source = hit.path("_source");
            assertThat(source.path("chunkText").asText()).isEqualTo(chunk.text());
            assertThat(source.path("fileName").asText()).isEqualTo(f.documents().get(chunk.document()).filename());
            assertThat(source.path("chunkId").asLong()).isEqualTo(chunk.chunkId());
            assertThat(source.path("groupId").asLong()).isEqualTo(ChallengeData.GROUP);
            assertThat(source.path("status").asText()).isEqualTo("READY");
            assertThat(source.path("deleted").asBoolean()).isFalse();
        }
        assertThat(seen).isEqualTo(corpus.chunks().keySet());
        var metadata = mapper.readTree(ChallengeData.OUTPUT.resolve("index-metadata.json").toFile()).path(ChallengeData.INDEX);
        assertThat(metadata.path("mappings").path("_meta").path("freezeSha256").asText()).isEqualTo(result.capture().freezeSha256());
        assertThat(metadata.path("settings").path("index").path("number_of_shards").asInt()).isEqualTo(1);
    }
    private static double rankScore(List<String> ids, String id) { int index = ids.indexOf(id); return index < 0 ? 0 : 1.0 / (61 + index); }
}
