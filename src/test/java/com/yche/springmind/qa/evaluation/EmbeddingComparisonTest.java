package com.yche.springmind.qa.evaluation;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class EmbeddingComparisonTest {
    @Test
    void cosineIsScaleInvariantAndRejectsInvalidVectors() {
        assertThat(EmbeddingComparison.cosine(new float[]{1, 0}, new float[]{3, 0})).isEqualTo(1);
        assertThat(EmbeddingComparison.cosine(new float[]{1, 0}, new float[]{0, 1})).isZero();
        assertThat(EmbeddingComparison.cosine(new float[]{1, 0}, new float[]{-1, 0})).isEqualTo(-1);
        assertThatThrownBy(() -> EmbeddingComparison.validateVector(new float[]{1}, 2)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EmbeddingComparison.validateVector(new float[]{0, 0}, 2)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EmbeddingComparison.validateVector(new float[]{Float.NaN, 0}, 2)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void independentCosineCheckRejectsMissingWrongOrDuplicateSqlHits() {
        List<ExtensionEvaluation.Hit> exact = new ArrayList<>();
        for (int i = 0; i < 105; i++) exact.add(new ExtensionEvaluation.Hit("c" + i, 1 - i / 200.0));
        EmbeddingComparison.verifySql(exact.subList(0, 50), exact);
        var wrong = new ArrayList<>(exact.subList(0, 50));
        wrong.set(0, exact.get(100));
        assertThatThrownBy(() -> EmbeddingComparison.verifySql(wrong, exact)).hasMessageContaining("cosine mismatch");
        wrong.set(0, exact.get(1));
        assertThatThrownBy(() -> EmbeddingComparison.verifySql(wrong, exact)).hasMessageContaining("duplicate");
        assertThatThrownBy(() -> EmbeddingComparison.verifySql(exact.subList(0, 49), exact)).hasMessageContaining("Incomplete");
    }

    @Test
    void sqlNumericalTiesAreAllowedButNotMeaningfulRankDifferences() {
        List<ExtensionEvaluation.Hit> exact = new ArrayList<>();
        for (int i = 0; i < 105; i++) exact.add(new ExtensionEvaluation.Hit("c" + i, i < 2 ? 1 : 1 - i / 200.0));
        var sql = new ArrayList<>(exact.subList(0, 50));
        Collections.swap(sql, 0, 1);
        EmbeddingComparison.verifySql(sql, exact);
        sql.set(0, new ExtensionEvaluation.Hit("c1", Double.NaN));
        assertThatThrownBy(() -> EmbeddingComparison.verifySql(sql, exact)).hasMessageContaining("cosine mismatch");
    }

    @Test
    void originalInputsRemainReviewedAndModelArmsHaveDistinctDimensions() throws Exception {
        FilenameAblation.verifyApproval();
        var models = EmbeddingComparison.models();
        assertThat(models.keySet()).containsExactly("CURRENT", "BGE_M3");
        assertThat(models.get("CURRENT").dimensions()).isEqualTo(512);
        assertThat(models.get("BGE_M3").dimensions()).isEqualTo(1024);
        assertThat(models.get("BGE_M3").tag()).isEqualTo("bge-m3:567m");
        assertThat(models.get("BGE_M3").precision()).isEqualTo("F16");
    }
}
