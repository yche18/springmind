package com.yche.springmind.qa.evaluation;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class BeirEvaluationTest {
    @Test void ndcgUsesLinearGainAndUnjudgedIsNotProvenIrrelevant() {
        var labels = Map.of("a", 2, "b", 1, "zero", 0);
        var s = BeirEvaluation.score(List.of("b", "missing", "a", "zero"), labels);
        double expected = (1 + 2 / 2.0) / (2 + 1 / (Math.log(3) / Math.log(2)));
        assertThat(s.ndcg10()).isCloseTo(expected, within(1e-12));
        assertThat(s.recall5()).isEqualTo(1); assertThat(s.judgedFraction10()).isEqualTo(0.75);
        assertThat(s.mrr10()).isEqualTo(1);
    }
    @Test void candidateUnionHasNoRankingMetricsAndKeepsExclusiveRelevantItems() {
        var u = BeirEvaluation.union(List.of("a", "noise"), List.of("b", "noise"), List.of("noise"), Map.of("a", 1, "b", 2), 25);
        assertThat(u.size()).isEqualTo(3); assertThat(u.recall()).isEqualTo(1);
        assertThat(u.keywordExclusive()).containsExactly("a"); assertThat(u.denseExclusive()).containsExactly("b");
        assertThat(u.lostAtRrf10()).containsExactly("a", "b");
    }
    @Test void rrfRanksByAgreementAndBreaksTiesByDocumentId() {
        assertThat(BeirEvaluation.rrf(List.of("a", "c"), List.of("b", "c"), 50)).containsExactly("c", "a", "b");
        assertThat(BeirEvaluation.rrf(List.of("a", "c"), List.of("b", "c"), 1)).containsExactly("a", "b");
    }
    @Test void diagnosticRrfDoesNotInheritProductionOutputCap() {
        var a = java.util.stream.IntStream.range(0,60).mapToObj(i -> "a"+i).toList();
        var b = java.util.stream.IntStream.range(0,60).mapToObj(i -> "b"+i).toList();
        assertThat(BeirEvaluation.rrf(a,b,50)).hasSize(100);
        assertThat(BeirEvaluation.rrf(a,b,1000)).hasSize(120);
    }
    @Test void duplicateRankedDocumentsAreRejected() {
        assertThatThrownBy(() -> BeirEvaluation.score(List.of("a","a"),Map.of("a",1))).isInstanceOf(IllegalArgumentException.class);
    }
}
