package com.yche.springmind.qa.evaluation;

import com.yche.springmind.ingestion.mapper.DocumentChunkMapper;
import com.yche.springmind.ingestion.model.entity.DocumentChunkEntity;
import com.yche.springmind.qa.model.QueryPlanResult;
import com.yche.springmind.qa.rag.*;
import com.yche.springmind.qa.service.QueryPlanningService;
import com.yche.springmind.retrieval.elasticsearch.ElasticsearchChunkIndexService;
import com.yche.springmind.retrieval.vectorstore.PgVectorRetrievalAdapter;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static com.yche.springmind.qa.evaluation.RetrievalAblation.require;

/** Replays captured ranks through real production clustering and budget selection. */
final class MultiHopReplay {
    record Context(List<Long> chunks, int windows, int tokens, String textSha256) {}
    static Map<String, Context> run(MultiHopData.Corpus corpus, MultiHopData.Question q,
                                   List<MultiHopEvaluation.Hit> keyword, List<MultiHopEvaluation.Hit> vector) throws Exception {
        return run(corpus, q, keyword, vector, (question, documents) -> new EvidenceReranker.Result(documents, "DISABLED"));
    }
    static Map<String, Context> run(MultiHopData.Corpus corpus, MultiHopData.Question q,
                                   List<MultiHopEvaluation.Hit> keyword, List<MultiHopEvaluation.Hit> vector,
                                   EvidenceReranker reranker) throws Exception {
        Map<String, Context> results = new LinkedHashMap<>();
        var scoreNormalizer = new ElasticsearchChunkIndexService(MultiHopData.M, "localhost", 9200, "http", "unused_offline");
        for (String mode : List.of("KEYWORD", "VECTOR", "RRF")) {
            var v = mock(PgVectorRetrievalAdapter.class);
            var k = mock(ElasticsearchChunkIndexService.class);
            var store = mock(DocumentChunkMapper.class);
            var planner = mock(QueryPlanningService.class);
            when(planner.plan(null, q.query())).thenReturn(QueryPlanResult.fallback(q.query()));
            when(v.search(1L, q.query(), 50)).thenReturn(mode.equals("KEYWORD") ? List.of() : vector.stream().map(h -> {
                var c = corpus.chunks().get(h.id());
                return new PgVectorRetrievalAdapter.VectorHit(c.documentId(), c.id(), c.index(), c.text(), h.score());
            }).toList());
            when(k.search(1L, q.query(), 50)).thenReturn(mode.equals("VECTOR") ? List.of() : keyword.stream().map(h -> {
                var c = corpus.chunks().get(h.id());
                Double normalized = org.springframework.test.util.ReflectionTestUtils.invokeMethod(scoreNormalizer, "normalizeKeywordScore", h.score());
                return new ElasticsearchChunkIndexService.KeywordHit(c.documentId(), c.id(), c.index(), c.title(), c.text(), h.score(), normalized);
            }).toList());
            when(store.selectQaReadyChunksByIds(eq(1L), anyList())).thenAnswer(call -> {
                List<Long> ids = call.getArgument(1);
                return ids.stream().map(corpus.chunks()::get).map(c -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("groupId", 1L); row.put("documentId", c.documentId()); row.put("chunkId", c.id());
                    row.put("chunkIndex", c.index()); row.put("fileName", c.title()); row.put("chunkText", c.text()); return row;
                }).toList();
            });
            when(store.selectReadyActiveChunksByDocumentId(eq(1L), anyLong())).thenAnswer(call -> {
                long doc = call.getArgument(1);
                return corpus.chunks().values().stream().filter(c -> c.documentId() == doc).map(c -> {
                    var entity = new DocumentChunkEntity(); entity.setId(c.id()); entity.setDocumentId(doc);
                    entity.setGroupId(1L); entity.setChunkIndex(c.index()); entity.setChunkText(c.text()); return entity;
                }).toList();
            });
            var result = new HybridEvidenceRetriever(v, k, store, planner,
                    new EvidenceSelector(EvidenceSelectionProperties.defaults(), reranker)).retrieve(1L, q.query(), 5);
            Set<Long> ids = new LinkedHashSet<>();
            for (var d : result.documents()) {
                long doc = ((Number) d.getMetadata().get("documentId")).longValue();
                int start = ((Number) d.getMetadata().get("startChunkIndex")).intValue();
                int end = ((Number) d.getMetadata().get("endChunkIndex")).intValue();
                corpus.chunks().values().stream().filter(c -> c.documentId() == doc && c.index() >= start && c.index() <= end)
                        .forEach(c -> ids.add(c.id()));
            }
            String context = result.documents().stream().map(org.springframework.ai.document.Document::getText)
                    .collect(java.util.stream.Collectors.joining(System.lineSeparator()));
            int tokens = new org.springframework.ai.tokenizer.JTokkitTokenCountEstimator().estimate(context);
            require(tokens <= 4096 && result.documents().size() <= 5, "Production context budget violated");
            results.put(mode, new Context(List.copyOf(ids), result.documents().size(), tokens, MultiHopData.sha(context)));
        }
        return results;
    }
}
