package com.yche.springmind.qa.evaluation;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class ChallengeTest {
    @Test
    void frozenCorpusContainsEveryInitialFactBeforeRetrieval() throws Exception {
        ChallengeData.verifyFreeze();
        var f = ChallengeData.fixture();
        var corpus = ChallengeData.corpus();
        assertThat(corpus.chunks()).hasSizeGreaterThan(100);
        assertThat(corpus.annotationStatus()).isEqualTo("PENDING_HUMAN_REVIEW");
        for (var q : f.cases()) {
            assertThat(corpus.grades().get(q.id())).hasSize(corpus.chunks().size());
            if (!q.evidence().isEmpty()) assertThat(ChallengeData.coverage(q, corpus.chunks().keySet(), corpus)).isEqualTo(1);
        }
        assertThat(f.cases().stream().filter(q -> q.evidence().stream().map(ExtensionFixture.Evidence::id).distinct().count() > 1)).hasSize(6);
    }

    @Test
    void alternativePassagesAreNotCountedAsTwoRequiredFacts() throws Exception {
        var f = ChallengeData.fixture();
        var q = f.cases().stream().filter(value -> value.id().equals("c24")).findFirst().orElseThrow();
        assertThat(q.evidence()).hasSize(2);
        assertThat(ChallengeData.coverage(q, List.of("d77--c000"), ChallengeData.corpus())).isEqualTo(1);
        assertThat(ChallengeData.coverage(q, List.of("d79--c000"), ChallengeData.corpus())).isEqualTo(1);
    }

    @Test
    void unionIsDeduplicatedAndTracksComplementaryEvidenceAndCappingLoss() throws Exception {
        var q = ChallengeData.fixture().cases().stream().filter(value -> value.id().equals("c27")).findFirst().orElseThrow();
        var corpus = ChallengeData.corpus();
        var union = ChallengeEvaluation.union(q, List.of("d84--c000", "d86--c000"), List.of("d85--c000", "d86--c000"),
                List.of("d84--c000", "d86--c000"), 25, corpus);
        assertThat(union.size()).isEqualTo(3);
        assertThat(union.recall()).isEqualTo(1);
        assertThat(union.factCoverage()).isEqualTo(1);
        assertThat(union.vectorExclusiveRelevant()).containsExactly("d84--c000");
        assertThat(union.keywordExclusiveRelevant()).containsExactly("d85--c000");
        assertThat(union.lostAtRrf30()).containsExactly("d85--c000");
        var reversed = ChallengeEvaluation.union(q, List.of("d86--c000", "d84--c000"), List.of("d86--c000", "d85--c000"),
                List.of("d84--c000", "d86--c000"), 25, corpus);
        assertThat(reversed).isEqualTo(union);
    }
}
