package com.yche.springmind.document;

import com.yche.springmind.document.mapper.DocumentMapper;
import com.yche.springmind.document.model.entity.DocumentEntity;
import com.yche.springmind.document.service.DocumentIngestionAsyncService;
import com.yche.springmind.ingestion.mapper.DocumentChunkMapper;
import com.yche.springmind.ingestion.model.entity.DocumentChunkEntity;
import com.yche.springmind.ingestion.service.DocumentIngestionProcessor;
import com.yche.springmind.ingestion.vector.VectorIngestionService;
import com.yche.springmind.retrieval.elasticsearch.ElasticsearchChunkIndexService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;

@ExtendWith(MockitoExtension.class)
class DocumentIngestionAsyncServiceTest {

    @Mock
    private DocumentMapper documentMapper;

    @Mock
    private DocumentIngestionProcessor documentIngestionProcessor;

    @Mock
    private DocumentChunkMapper documentChunkMapper;

    @Mock
    private VectorIngestionService vectorIngestionService;

    @Mock
    private ElasticsearchChunkIndexService elasticsearchChunkIndexService;

    @Test
    void shouldProcessDocumentAndMarkReady() {
        DocumentEntity document = new DocumentEntity();
        document.setId(3001L);
        document.setGroupId(2001L);
        document.setFileName("需求说明.txt");
        DocumentChunkEntity chunk = new DocumentChunkEntity();
        chunk.setId(9001L);

        given(documentMapper.selectByIdAndGroupId(3001L, 2001L)).willReturn(document);
        given(documentChunkMapper.selectByDocumentId(3001L)).willReturn(List.of(chunk));
        given(documentMapper.updateStatus(eq(3001L), eq(2001L), eq("READY"), eq(null), any(LocalDateTime.class)))
                .willReturn(1);

        DocumentIngestionAsyncService service = createService();
        service.ingestDocument(3001L, 2001L);

        InOrder inOrder = inOrder(documentChunkMapper, vectorIngestionService, elasticsearchChunkIndexService, documentIngestionProcessor, documentMapper);
        inOrder.verify(documentChunkMapper).deleteByDocumentId(3001L);
        inOrder.verify(vectorIngestionService).deleteDocumentVectors(3001L);
        inOrder.verify(elasticsearchChunkIndexService).deleteDocumentChunks(3001L);
        inOrder.verify(documentIngestionProcessor).process(3001L, 2001L);
        inOrder.verify(documentChunkMapper).selectByDocumentId(3001L);
        inOrder.verify(elasticsearchChunkIndexService).indexReadyChunks("需求说明.txt", List.of(chunk));
        inOrder.verify(documentMapper).updateStatus(eq(3001L), eq(2001L), eq("READY"), eq(null), any(LocalDateTime.class));
    }

    @Test
    void shouldMarkDocumentFailedInRecover() {
        given(documentMapper.updateStatus(eq(3001L), eq(2001L), eq("FAILED"), eq("向量写入失败"), any(LocalDateTime.class)))
                .willReturn(1);

        DocumentIngestionAsyncService service = createService();
        service.recover(new IllegalStateException("向量写入失败"), 3001L, 2001L);

        then(documentChunkMapper).should().deleteByDocumentId(3001L);
        then(vectorIngestionService).should().deleteDocumentVectors(3001L);
        then(elasticsearchChunkIndexService).should().deleteDocumentChunks(3001L);
        ArgumentCaptor<LocalDateTime> processedAtCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        then(documentMapper).should().updateStatus(
                eq(3001L),
                eq(2001L),
                eq("FAILED"),
                eq("向量写入失败"),
                processedAtCaptor.capture()
        );
        assertThat(processedAtCaptor.getValue()).isNotNull();
    }

    private DocumentIngestionAsyncService createService() {
        return new DocumentIngestionAsyncService(
                documentMapper,
                documentIngestionProcessor,
                documentChunkMapper,
                vectorIngestionService,
                elasticsearchChunkIndexService
        );
    }
}
