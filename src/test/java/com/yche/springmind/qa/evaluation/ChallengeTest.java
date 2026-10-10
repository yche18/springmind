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
}
