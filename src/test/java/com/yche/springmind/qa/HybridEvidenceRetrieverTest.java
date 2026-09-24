package com.yche.springmind.qa;

import com.yche.springmind.ingestion.mapper.DocumentChunkMapper;
import com.yche.springmind.ingestion.model.entity.DocumentChunkEntity;
import com.yche.springmind.qa.model.QueryPlanStrategy;
import com.yche.springmind.qa.model.QueryPlanResult;
import com.yche.springmind.qa.rag.HybridEvidenceRetriever;
import com.yche.springmind.qa.rag.RetrievedEvidenceBundle;
import com.yche.springmind.qa.service.QueryPlanningService;
import com.yche.springmind.retrieval.elasticsearch.ElasticsearchChunkIndexService;
import com.yche.springmind.retrieval.vectorstore.PgVectorRetrievalAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HybridEvidenceRetrieverTest {

    @Test
    void shouldIncludeFileNameInEvidenceTextForPromptContext() {
        PgVectorRetrievalAdapter vectorRetrievalAdapter = mock(PgVectorRetrievalAdapter.class);
        ElasticsearchChunkIndexService elasticsearchChunkIndexService = mock(ElasticsearchChunkIndexService.class);
        DocumentChunkMapper documentChunkMapper = mock(DocumentChunkMapper.class);
        QueryPlanningService queryPlanningService = mock(QueryPlanningService.class);
        HybridEvidenceRetriever retrievalService = new HybridEvidenceRetriever(
                vectorRetrievalAdapter,
                elasticsearchChunkIndexService,
                documentChunkMapper,
                queryPlanningService,
                1
        );
        when(queryPlanningService.plan(null, "上传流程")).thenReturn(new QueryPlanResult(
                QueryPlanStrategy.DIRECT,
                List.of("上传流程")
        ));
        when(vectorRetrievalAdapter.search(2001L, "上传流程", 50)).thenReturn(List.of(
                new PgVectorRetrievalAdapter.VectorHit(1001L, 9001L, 0, "产品团队每两周发布一次。", 0.93D)
        ));
        when(elasticsearchChunkIndexService.search(2001L, "上传流程", 50)).thenReturn(List.of());
        when(documentChunkMapper.selectQaReadyChunksByIds(2001L, List.of(9001L))).thenReturn(List.of(
                Map.of(
                        "groupId", 2001L,
                        "documentId", 1001L,
                        "chunkId", 9001L,
                        "chunkIndex", 0,
                        "fileName", "产品手册.md"
                )
        ));
        DocumentChunkEntity chunk = new DocumentChunkEntity();
        chunk.setId(9001L);
        chunk.setDocumentId(1001L);
        chunk.setGroupId(2001L);
        chunk.setChunkIndex(0);
        chunk.setChunkText("产品团队每两周发布一次。");
        when(documentChunkMapper.selectReadyActiveChunksByDocumentId(2001L, 1001L)).thenReturn(List.of(chunk));

        RetrievedEvidenceBundle bundle = retrievalService.retrieve(2001L, "上传流程", 5);

        assertThat(bundle.documents()).hasSize(1);
        Document evidence = bundle.documents().getFirst();
        assertThat(evidence.getMetadata()).containsEntry("fileName", "产品手册.md");
        assertThat(evidence.getText()).contains("文件名：产品手册.md");
        assertThat(evidence.getText()).contains("产品团队每两周发布一次。");
    }
}
