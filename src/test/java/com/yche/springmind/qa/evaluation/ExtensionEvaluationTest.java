package com.yche.springmind.qa.evaluation;

import com.yche.springmind.ingestion.transformer.ChunkingProperties;
import com.yche.springmind.ingestion.transformer.StructureAwareChunkTransformer;
import com.yche.springmind.ingestion.transformer.TextCleanupTransformer;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class ExtensionEvaluationTest {
    static Map<String, ExtensionEvaluation.Chunk> localChunks(ExtensionFixture fixture) {
        var transformer = new StructureAwareChunkTransformer(new ChunkingProperties(240, 320, 32));
        var cleanup = new TextCleanupTransformer();
        Map<String, ExtensionEvaluation.Chunk> chunks = new LinkedHashMap<>();
        long chunkId = 1, documentId = 1;
        for (var source : fixture.documents().entrySet()) {
            var parts = transformer.apply(cleanup.apply(List.of(Document.builder().text(source.getValue()).build())));
            for (int i = 0; i < parts.size(); i++) chunks.put(source.getKey() + "--c" + String.format(Locale.ROOT, "%03d", i),
                    new ExtensionEvaluation.Chunk(chunkId++, documentId, source.getKey(), i, parts.get(i).getText()));
            documentId++;
        }
        return chunks;
    }

    @Test
    void productionChunkingPreservesAllFrozenEvidenceQuotes() throws Exception {
        var fixture = ExtensionFixture.read();
        assertThat(RetrievalAblation.hash(ExtensionFixture.PATH)).isEqualTo(ExtensionEvaluation.FIXTURE_HASH);
        var chunks = localChunks(fixture);
        assertThat(chunks.size()).isGreaterThan(50);
        for (var question : fixture.cases()) for (var evidence : question.evidence()) {
            boolean found = chunks.values().stream().anyMatch(chunk -> ExtensionEvaluation.facts(question, chunk.document(), chunk.text()).contains(evidence.id()));
            if (!found) {
                System.out.println("Split evidence before retrieval: " + question.id() + "/" + evidence.id());
                chunks.values().stream().filter(chunk -> chunk.document().equals(evidence.document()))
                        .forEach(chunk -> System.out.println("chunk " + chunk.index() + ": " + chunk.text()));
            }
        }
        var grades = ExtensionEvaluation.project(fixture, chunks);
        assertThat(grades).hasSize(24);
        for (var question : fixture.cases()) {
            assertThat(grades.get(question.id())).hasSize(chunks.size());
            if (question.evidence().isEmpty()) assertThat(grades.get(question.id()).values()).containsOnly(0);
            else assertThat(ExtensionEvaluation.chunkCoverage(question, chunks.keySet(), chunks)).isEqualTo(1.0);
        }
        for (String source : List.of("restore-guide-en", "release-guide-zh"))
            assertThat(chunks.values().stream().filter(chunk -> chunk.document().equals(source)).count()).isGreaterThan(1);
    }

    @Test
    void exactQuoteMustBelongToTheRightDocumentAndMissingEvidenceFails() throws Exception {
        var fixture = ExtensionFixture.read();
        var question = fixture.cases().getFirst();
        assertThat(ExtensionEvaluation.facts(question, "relay-v2-zh", question.evidence().getFirst().quote())).isEmpty();
        assertThatThrownBy(() -> ExtensionEvaluation.project(fixture, Map.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("split or lost");
    }

    @Test
    void duplicateSupportingChunksDoNotInflateFactCoverage() throws Exception {
        var question = ExtensionFixture.read().cases().getFirst();
        var text = question.evidence().getFirst().quote();
        var chunk = new ExtensionEvaluation.Chunk(1, 1, question.evidence().getFirst().document(), 0, text);
        assertThat(ExtensionEvaluation.chunkCoverage(question, List.of("a", "b"), Map.of("a", chunk, "b", chunk))).isEqualTo(1.0);
        assertThat(ExtensionEvaluation.chunkCoverage(question, List.of(), Map.of("a", chunk))).isZero();
    }

    @Test
    void rawRanksFollowRrfAndChunkIdTieBreakAndRejectForeignHits() {
        var chunks = Map.of("a", new ExtensionEvaluation.Chunk(2, 1, "a", 0, "a"),
                "b", new ExtensionEvaluation.Chunk(1, 2, "b", 0, "b"));
        var vector = List.of(new ExtensionEvaluation.Hit("a", 1), new ExtensionEvaluation.Hit("b", 0.8));
        var keyword = List.of(new ExtensionEvaluation.Hit("b", 4), new ExtensionEvaluation.Hit("a", 3));
        assertThat(ExtensionEvaluation.orders(vector, keyword, chunks).get("HYBRID")).containsExactly("b", "a");
        assertThatThrownBy(() -> ExtensionEvaluation.orders(List.of(new ExtensionEvaluation.Hit("foreign", 1)), keyword, chunks))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ExtensionEvaluation.orders(List.of(vector.getFirst(), vector.getFirst()), keyword, chunks))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pendingReviewIncludesEveryCaseAndChunkWithoutModelScores() throws Exception {
        var fixture = ExtensionFixture.read();
        var chunks = localChunks(fixture);
        var corpus = new ExtensionEvaluation.Corpus(ExtensionEvaluation.FIXTURE_HASH, "before-query", "PENDING_HUMAN_REVIEW", 1,
                chunks, ExtensionEvaluation.project(fixture, chunks));
        String html = ExtensionReport.review(fixture, corpus);
        assertThat(html).contains("待人工复核", "默认 0 分", "完整语料原文").doesNotContain("人工复核已通过", "nDCG@5", "<script>");
        fixture.cases().forEach(question -> assertThat(html).contains("id='" + question.id() + "'", AnnotationReview.escape(question.expected())));
        chunks.keySet().forEach(id -> assertThat(html).contains(id));
    }
}
