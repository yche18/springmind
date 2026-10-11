package com.yche.springmind.qa;

import com.yche.springmind.qa.model.vo.AskQuestionResponse;
import com.yche.springmind.qa.support.CitationAssembler;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CitationAssemblerTest {

    @Test
    void shouldPreserveDifferentDocumentsWithTheSameFilename() {
        Document first = Document.builder().text("A").metadata(Map.of("documentId", 1L, "fileName", "guide.md")).build();
        Document second = Document.builder().text("B").metadata(Map.of("documentId", 2L, "fileName", "guide.md")).build();
        assertThat(new CitationAssembler().assembleDocuments(List.of(first, first, second)))
                .extracting(AskQuestionResponse.Citation::documentId).containsExactly(1L, 2L);
    }

    @Test
    void shouldUseFileNameWhenPresent() {
        CitationAssembler citationAssembler = new CitationAssembler();
        Document document = Document.builder()
                .text("证据内容")
                .metadata(Map.of(
                        "documentId", 1001L,
                        "chunkId", 9001L,
                        "chunkIndex", 0,
                        "fileName", "产品手册.md",
                        "score", 0.92D
                ))
                .build();

        List<AskQuestionResponse.Citation> citations = citationAssembler.assembleDocuments(List.of(document));

        assertThat(citations).hasSize(1);
        assertThat(citations.getFirst().fileName()).isEqualTo("产品手册.md");
    }

    @Test
    void shouldFallbackToLegacyFileNameFieldForCitationMetadata() {
        CitationAssembler citationAssembler = new CitationAssembler();
        Document document = Document.builder()
                .text("证据内容")
                .metadata(Map.of(
                        "documentId", 1001L,
                        "chunkId", 9001L,
                        "chunkIndex", 0,
                        "documentName", "旧产品手册.md",
                        "score", 0.92D
                ))
                .build();

        List<AskQuestionResponse.Citation> citations = citationAssembler.assembleDocuments(List.of(document));

        assertThat(citations).hasSize(1);
        assertThat(citations.getFirst().fileName()).isEqualTo("旧产品手册.md");
    }
}
