package com.yche.springmind.qa;

import com.yche.springmind.qa.rag.*;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;
import static org.assertj.core.api.Assertions.*;

class EvidenceSelectorTest {
    private EvidenceSelector selector(int budget) {
        return new EvidenceSelector(new EvidenceSelectionProperties(budget, 30, false, "", "", "", Duration.ofSeconds(2)),
                (question, docs) -> new EvidenceReranker.Result(docs, "DISABLED"));
    }

    @Test
    void shouldRemoveOverlappingChunksButKeepNewEvidence() {
        Document first = document("first", 10L, 0, chunk(0, "First requirement"), chunk(1, "Shared requirement"));
        Document second = document("second", 10L, 2, chunk(1, "Shared requirement"), chunk(2, "Second requirement"));
        var result = selector(4096).select("requirements", List.of(first, second), 5);
        assertThat(result.documents()).hasSize(2);
        assertThat(result.documents().getLast().getText()).contains("Second requirement").doesNotContain("Shared requirement");
        assertThat(result.documents().getLast().getMetadata()).containsEntry("startChunkIndex", 2).containsEntry("endChunkIndex", 2);
        assertThat(result.documents().getLast().getMetadata()).doesNotContainKey("windowChunks");
    }

    @Test
    void shouldDeduplicateSameContentAcrossFiles() {
        var result = selector(4096).select("question", List.of(
                document("a", 10L, 0, chunk(0, "Same  requirement")),
                document("b", 20L, 0, chunk(0, "Same requirement"))), 5);
        assertThat(result.documents()).hasSize(1);
        assertThat(result.documents().getFirst().getMetadata()).containsEntry("documentId", 10L);
    }

    @Test
    void shouldKeepPrimaryChunkAndBoundActualJoinedContext() {
        Document window = document("a", 10L, 1, chunk(0, "before ".repeat(200)),
                chunk(1, "The deployment requires approval."), chunk(2, "after ".repeat(200)));
        var result = selector(128).select("approval", List.of(window), 5);
        assertThat(result.documents()).hasSize(1);
        Document selected = result.documents().getFirst();
        assertThat(selected.getText()).contains("requires approval").doesNotContain("before", "after");
        assertThat(selected.getMetadata()).containsEntry("startChunkIndex", 1).containsEntry("endChunkIndex", 1)
                .containsEntry("primaryChunkIndex", 1).containsEntry("chunkId", 1001L);
        String context = result.documents().stream().map(Document::getText).collect(Collectors.joining(System.lineSeparator()));
        int tokens = new JTokkitTokenCountEstimator().estimate(context);
        assertThat(tokens).isEqualTo(result.trace().selectedTokens()).isLessThanOrEqualTo(128);
        assertThat(result.trace().inputTokens()).isGreaterThan(result.trace().selectedTokens());
    }

    @Test
    void shouldRemoveSuffixPrefixOverlapWithoutChangingSourceRange() {
        String overlap = "This shared sentence appears across both chunks.";
        var result = selector(4096).select("question", List.of(document("a", 10L, 0,
                chunk(0, "Start. " + overlap), chunk(1, overlap + " End."))), 5);
        assertThat(result.documents().getFirst().getText()).containsOnlyOnce(overlap).contains("Start.", "End.");
        assertThat(result.documents().getFirst().getMetadata()).containsEntry("startChunkIndex", 0).containsEntry("endChunkIndex", 1);
    }

    @Test
    void shouldKeepRankingProvenanceAndHonorTopK() {
        var selector = new EvidenceSelector(EvidenceSelectionProperties.defaults(), (question, docs) -> {
            List<Document> reversed = new ArrayList<>(docs);
            Collections.reverse(reversed);
            return new EvidenceReranker.Result(reversed, "APPLIED");
        });
        var result = selector.select("question", List.of(document("a", 10L, 0, chunk(0, "One answer")),
                document("b", 20L, 0, chunk(0, "Another answer"))), 1);
        assertThat(result.documents()).hasSize(1);
        assertThat(result.documents().getFirst().getId()).isEqualTo("E1");
        assertThat(result.documents().getFirst().getMetadata()).containsEntry("documentId", 20L)
                .containsEntry("rrfRank", 2).containsEntry("selectionRank", 1).containsEntry("evidenceId", "E1");
        assertThat(result.trace().rerankStatus()).isEqualTo("APPLIED");
    }

    @Test
    void shouldNotSendOversizedChunksOrBlankEvidence() {
        var result = selector(128).select("question", List.of(document("a", 10L, 0, chunk(0, "large ".repeat(500)))), 5);
        assertThat(result.documents()).isEmpty();
        assertThat(result.trace().selectedTokens()).isZero();
        assertThat(selector(128).select("question", List.of(), 5).documents()).isEmpty();
    }

    @Test
    void shouldApplyOneSharedBudgetToMultilingualEvidence() {
        List<Document> documents = new ArrayList<>();
        for (long id = 0; id < 8; id++) {
            documents.add(document("file" + id, id, 0, chunk(0, "第" + id + "项：审批后部署。Deployment requires approval. 🧪")));
        }
        var result = selector(128).select("审批", documents, 8);
        assertThat(result.documents()).isNotEmpty().hasSizeLessThan(8);
        assertThat(result.trace().selectedTokens()).isLessThanOrEqualTo(128);
    }

    private EvidenceWindowChunk chunk(int index, String text) { return new EvidenceWindowChunk(1000L + index, index, text); }

    private Document document(String id, long documentId, int primary, EvidenceWindowChunk... chunks) {
        return Document.builder().id(id).text(Arrays.stream(chunks).map(EvidenceWindowChunk::text).collect(Collectors.joining("\n")))
                .metadata(Map.of("documentId", documentId, "fileName", id + ".txt", "chunkId", 1000L + primary,
                        "primaryChunkIndex", primary, "chunkIndex", primary, "windowChunks", List.of(chunks), "score", 0.9)).build();
    }
}
