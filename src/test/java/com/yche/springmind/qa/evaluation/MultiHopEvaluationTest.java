package com.yche.springmind.qa.evaluation;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MultiHopEvaluationTest {
    @Test void singleChannelRrfPreservesOrderAndHybridUsesNumericTies() {
        var a = List.of(new MultiHopEvaluation.Hit(9, 100), new MultiHopEvaluation.Hit(2, 10));
        assertEquals(List.of(9L, 2L), MultiHopEvaluation.rrf(a, List.of()));
        assertEquals(List.of(2L, 9L), MultiHopEvaluation.rrf(a, List.of(new MultiHopEvaluation.Hit(2, .9), new MultiHopEvaluation.Hit(9, .8))));
    }
    @Test void completeEvidenceNeedsBothPartsAndUnmappedFactsStayMissing() {
        var q = new MultiHopData.Question("q", 0, "FORMAL", "inference_query", "question", "answer", List.of(
                new MultiHopData.Fact("a", 1, "quote", List.of(List.of(1L, 2L), List.of(3L))),
                new MultiHopData.Fact("b", 2, "unmapped", List.of())));
        assertTrue(MultiHopEvaluation.facts(q, List.of(1L)).isEmpty());
        assertEquals(Set.of("a"), MultiHopEvaluation.facts(q, List.of(1L, 2L)));
        assertEquals(Set.of("a"), MultiHopEvaluation.facts(q, List.of(3L)));
        var score = MultiHopEvaluation.score(q, List.of(3L), Map.of(3L, 1L));
        assertEquals(.5, score.factRecall()); assertFalse(score.allFacts());
    }
    @Test void nullQuestionsDoNotReceivePerfectRecall() {
        var q = new MultiHopData.Question("q", 0, "FORMAL", "null_query", "question", "Insufficient information.", List.of());
        var score = MultiHopEvaluation.score(q, List.of(1L), Map.of(1L, 1L));
        assertNull(score.factRecall()); assertNull(score.allFacts()); assertEquals(1, score.chunks());
    }
    @Test void frozenSplitHasNoDuplicateOrRelatedQuestionLeakage() throws Exception {
        var sample = MultiHopData.sample();
        assertEquals(50, sample.questions().stream().filter(q -> q.split().equals("FORMAL")).count());
        assertEquals(10, sample.questions().stream().filter(q -> q.split().equals("CALIBRATION")).count());
        assertEquals(60, sample.questions().stream().map(MultiHopData.Question::sourceRow).distinct().count());
        for (var a : sample.questions()) for (var b : sample.questions()) if (!a.split().equals(b.split())) {
            assertFalse(MultiHopData.similar(a.query(), b.query()));
            if (!a.facts().isEmpty()) assertNotEquals(new HashSet<>(a.facts().stream().map(MultiHopData.Fact::documentId).toList()),
                    new HashSet<>(b.facts().stream().map(MultiHopData.Fact::documentId).toList()));
        }
    }
}
