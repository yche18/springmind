package com.yche.springmind.qa.evaluation;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class RetrievalSnapshotTest {
    private final RerankEvaluation.Dataset fixture = new RerankEvaluation.Dataset("SYNTHETIC",
            Map.of("a", "Relevant source", "b", "Distractor"), List.of(
            new RerankEvaluation.Example("q1", "test", "Question", List.of("a", "b"), Map.of("a", 3))));

    private Document document(long group, long id, String text) {
        return Document.builder().id("E1").text(text).metadata(Map.of("groupId", group, "documentId", id)).build();
    }

    @Test
    void preservesActualTextAndOrderAndKeepsMissedRelevantLabels() {
        var result = RetrievalSnapshot.assemble(fixture, Map.of("q1", List.of(document(7, 12, "Exact retrieval header + text"))),
                Map.of(11L, "a", 12L, "b"), 7);
        assertThat(result.provenance()).isEqualTo("RRF_SNAPSHOT");
        assertThat(result.cases().getFirst().candidates()).containsExactly("q1__b");
        assertThat(result.cases().getFirst().relevance()).containsOnly(entry("q1__a", 3));
        assertThat(result.documents()).containsEntry("q1__b", "Exact retrieval header + text");
        assertThat(RerankEvaluation.metrics(result.cases().getFirst().candidates(), result.cases().getFirst().relevance(), 5).recall()).isZero();
    }

    @Test
    void rejectsForeignGroupsForeignDocumentsAndAmbiguousWindows() {
        for (var documents : List.of(List.of(document(8, 11, "Text")), List.of(document(7, 13, "Text")),
                List.of(document(7, 11, "First"), document(7, 11, "Second")))) {
            assertThatThrownBy(() -> RetrievalSnapshot.assemble(fixture, Map.of("q1", documents), Map.of(11L, "a", 12L, "b"), 7))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void missingCaptureFailsButEmptyCandidatePoolIsRetained() {
        assertThatThrownBy(() -> RetrievalSnapshot.assemble(fixture, Map.of(), Map.of(11L, "a", 12L, "b"), 7))
                .isInstanceOf(IllegalArgumentException.class);
        var result = RetrievalSnapshot.assemble(fixture, Map.of("q1", List.of()), Map.of(11L, "a", 12L, "b"), 7);
        assertThat(result.cases().getFirst().candidates()).isEmpty();
        assertThat(result.cases().getFirst().relevance()).containsEntry("q1__a", 3);
    }
}
