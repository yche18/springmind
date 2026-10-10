package com.yche.springmind.qa.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Verifies frozen cross-factor replay without a Spring context, services or models. */
class RecordedRetrievalCrossTest {
    private static RetrievalCrossComparison.Comparison comparison;

    @BeforeAll
    static void replay() throws Exception { comparison = RetrievalCrossComparison.compare(); }

    @Test
    void savedMetricsAndReportReproduceExactlyOffline() throws Exception {
        var mapper = RerankEvaluationRun.datasetMapper();
        assertThat((JsonNode) mapper.valueToTree(comparison)).isEqualTo(
                mapper.readTree(RetrievalCrossComparison.OUTPUT.resolve("comparison.json").toFile()));
        assertThat(Files.readString(RetrievalCrossComparison.OUTPUT.resolve("report.html")).replace("\r\n", "\n"))
                .isEqualTo(RetrievalCrossReportRun.render(comparison).replace("\r\n", "\n"))
                .contains("新增模型调用 0", "文件名缓解了退化", "e19").doesNotContain("<script>");
        assertThat(comparison.annotationStatus()).isEqualTo("HUMAN_REVIEWED");
        assertThat(comparison.newModelRequests()).isZero();
        assertThat(comparison.newRetrievalRequests()).isZero();
        assertThat(comparison.rows()).hasSize(24);
        assertThat(comparison.inputHashes().get("embedding-capture"))
                .isEqualTo("80738762128e53da1c02a3c9e9b3d489b6d4e8f7e0d0507c5dd7128c34d6c98a");
        assertThat(comparison.inputHashes().get("filename-capture"))
                .isEqualTo("cccefe1e6f4f30f48669cae29171a6cf1eaf5de135cb6c19d790b321bac8fcb6");
    }

    @Test
    void allFourFusionsMatchIndependentRankArithmetic() throws Exception {
        var chunks = FilenameAblation.corpus().chunks();
        for (var row : comparison.rows()) {
            assertThat(row.rankings().keySet()).containsExactlyElementsOf(RetrievalCrossComparison.MODES);
            for (String model : List.of("CURRENT", "BGE_M3")) {
                var vector = row.rankings().get(model + "_VECTOR");
                assertThat(vector).hasSize(50);
                for (String field : List.of("BODY", "FILENAME")) {
                    var keyword = row.rankings().get("KEYWORD_" + field);
                    Set<String> candidates = new HashSet<>(vector);
                    candidates.addAll(keyword);
                    var expected = candidates.stream().sorted(Comparator
                            .<String>comparingDouble(id -> contribution(vector, id) + contribution(keyword, id)).reversed()
                            .thenComparingLong(id -> chunks.get(id).chunkId())).limit(30).toList();
                    assertThat(row.rankings().get(model + "_" + field + "_RRF")).isEqualTo(expected);
                    assertThat(row.scores().get(model + "_" + field + "_RRF").recall50()).isNull();
                }
            }
        }
    }

    @Test
    void excludesUnanswerableCasesAndPartitionsAllPairedEffects() {
        assertThat(comparison.groups().get("ALL").values()).allSatisfy(summary -> {
            assertThat(summary.answerable()).isEqualTo(20);
            assertThat(summary.complementaryCases()).isEqualTo(6);
        });
        assertThat(comparison.groups().get("unanswerable").values()).allSatisfy(summary -> {
            assertThat(summary.metrics()).isNull();
            assertThat(summary.factCoverage5()).isNull();
        });
        for (var slice : comparison.effects().entrySet()) for (var effect : slice.getValue().values()) {
            var summaries = comparison.groups().get(slice.getKey());
            List<String> ids = new ArrayList<>(effect.improved());
            ids.addAll(effect.regressed());
            ids.addAll(effect.unchanged());
            assertThat(ids).doesNotHaveDuplicates().hasSize(summaries.get(effect.before()).answerable());
            var before = summaries.get(effect.before()).metrics();
            var after = summaries.get(effect.after()).metrics();
            if (before == null) assertThat(effect.delta()).isNull();
            else {
                assertThat(effect.delta().recall()).isCloseTo(after.recall() - before.recall(), within(1e-12));
                assertThat(effect.delta().ndcg()).isCloseTo(after.ndcg() - before.ndcg(), within(1e-12));
            }
        }
    }

    @Test
    void recordsBothGainsAndRegressionsWithoutChangingLabels() {
        var overall = comparison.groups().get("ALL");
        assertThat(overall.get("CURRENT_BODY_RRF").metrics().recall()).isEqualTo(0.8);
        assertThat(overall.get("BGE_M3_BODY_RRF").metrics().recall()).isEqualTo(0.925);
        assertThat(overall.get("BGE_M3_BODY_RRF").metrics().ndcg()).isCloseTo(0.8372038473168457, within(1e-12));
        assertThat(overall.get("BGE_M3_FILENAME_RRF").metrics().recall()).isEqualTo(0.975);
        assertThat(comparison.effects().get("ALL").get("rrf_m3_body").regressed()).containsExactly("e13", "e14", "e15", "e16");
        assertThat(comparison.effects().get("ALL").get("filename_current_rrf").regressed()).containsExactly("e19");
        assertRank("e15", "BGE_M3_VECTOR", "remote-exception-zh--c000", 1);
        assertRank("e15", "BGE_M3_BODY_RRF", "remote-exception-zh--c000", 13);
        assertRank("e15", "BGE_M3_BODY_RRF", "remote-standard-zh--c000", 14);
        assertRank("e15", "BGE_M3_FILENAME_RRF", "remote-exception-zh--c000", 2);
        assertRank("e15", "BGE_M3_FILENAME_RRF", "remote-standard-zh--c000", 4);
        for (String field : List.of("BODY", "FILENAME")) {
            assertRank("e16", "BGE_M3_" + field + "_RRF", "expense-deadline-en--c000", 6);
            assertRank("e12", "BGE_M3_" + field + "_RRF", "notify-urgent-en--c001", 2);
        }
        assertRank("e19", "CURRENT_BODY_RRF", "restore-guide-en--c004", 2);
        assertRank("e19", "CURRENT_FILENAME_RRF", "restore-guide-en--c004", 3);
    }

    private static double contribution(List<String> rank, String id) {
        int index = rank.indexOf(id);
        return index < 0 ? 0 : 1.0 / (61 + index);
    }
    private static void assertRank(String question, String mode, String chunk, int rank) {
        var row = comparison.rows().stream().filter(value -> value.id().equals(question)).findFirst().orElseThrow();
        assertThat(row.rankings().get(mode).indexOf(chunk) + 1).isEqualTo(rank);
    }
}
