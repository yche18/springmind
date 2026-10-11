package com.yche.springmind.qa.evaluation;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yche.springmind.ai.config.AiChatModelProvider;
import com.yche.springmind.auth.config.DevAdminInitializer;
import com.yche.springmind.document.service.StaleProcessingDocumentRecoveryRunner;
import com.yche.springmind.groupmembership.service.GroupMembershipService;
import com.yche.springmind.ingestion.mapper.DocumentChunkMapper;
import com.yche.springmind.qa.rag.*;
import com.yche.springmind.qa.service.QueryPlanningService;
import com.yche.springmind.retrieval.elasticsearch.ElasticsearchChunkIndexService;
import com.yche.springmind.retrieval.vectorstore.PgVectorRetrievalAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static com.yche.springmind.qa.evaluation.RetrievalAblation.require;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Explicit local-only capture. Reuses the isolated corpus; never uploads, ingests or calls a chat/rerank provider. */
@EnabledIfSystemProperty(named = "eval.ablation", matches = "true")
@SpringBootTest(properties = {"document.ingestion.worker-enabled=false", "qa.evidence.rerank-enabled=false",
        "spring.flyway.enabled=false", "spring.ai.vectorstore.pgvector.initialize-schema=false",
        "spring.ai.ollama.base-url=http://localhost:11434",
        "spring.ai.ollama.embedding.options.model=qllama/bge-small-zh-v1.5",
        "qa.evidence.candidate-limit=30", "qa.evidence.max-tokens=4096"})
@AutoConfigureMockMvc(print = org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint.NONE)
class LiveRetrievalAblationRun {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired GroupMembershipService membership;
    @Autowired DocumentChunkMapper chunks;
    @Autowired PgVectorRetrievalAdapter vectors;
    @Autowired ElasticsearchChunkIndexService keywords;
    @Autowired QueryPlanningService planner;
    @MockBean StaleProcessingDocumentRecoveryRunner recovery;
    @MockBean DevAdminInitializer administrator;
    @MockBean AiChatModelProvider chatProvider;
    @MockBean EvidenceReranker providerReranker;

    @Test
    void captureExistingCorpus() throws Exception {
        Path local = Path.of(System.getProperty("eval.captureDir", "evaluation/rerank/local/northstar-d41905eb"));
        Path output = Path.of(System.getProperty("eval.ablationOutput", "target/retrieval-ablation/capture.json"));
        require(!Files.exists(output), "Choose a new capture path; existing results will not be overwritten");
        var dataMapper = RerankEvaluationRun.datasetMapper();
        var fixturePath = Path.of("evaluation/rerank/sample.json");
        var fixture = dataMapper.readValue(fixturePath.toFile(), RerankEvaluation.Dataset.class);
        var snapshotPath = RetrievalAblation.SOURCE.resolve("snapshot-v2.json");
        var snapshot = dataMapper.readValue(snapshotPath.toFile(), RerankEvaluation.Dataset.class);
        require(OfflineRelabel.applyLabels(snapshot, fixture).equals(snapshot), "Fixture differs from reviewed annotations");
        String datasetHash = RetrievalAblation.hash(snapshotPath);
        var approval = dataMapper.readTree(RetrievalAblation.SOURCE.resolve("review-approval.json").toFile());
        require("HUMAN_REVIEWED".equals(approval.path("status").asText())
                && datasetHash.equals(approval.path("datasetSha256").asText()), "Dataset is not the reviewed revision");
        var manifest = mapper.readTree(local.resolve("manifest.json").toFile());
        long groupId = manifest.path("groupId").asLong();
        require(groupId > 0 && manifest.path("runId").asText().startsWith("northstar-"), "Not an isolated Northstar capture");
        Map<Long, String> sources = new LinkedHashMap<>();
        manifest.path("documents").fields().forEachRemaining(entry -> sources.put(Long.parseLong(entry.getKey()), entry.getValue().asText()));
        require(sources.size() == fixture.documents().size()
                && new HashSet<>(sources.values()).equals(fixture.documents().keySet()), "Manifest corpus mismatch");

        // Credentials stay in memory and the ignored local account file; MockMvc output is disabled.
        var account = mapper.readTree(local.resolve("account.json").toFile());
        require(account.path("username").asText().equals(manifest.path("runId").asText()), "Account does not match capture");
        var login = mvc.perform(post("/api/auth/login").contentType("application/json").content(mapper.writeValueAsBytes(
                Map.of("loginId", account.path("username").asText(), "password", account.path("password").asText())))).andReturn();
        require(login.getResponse().getStatus() == 200, "Evaluation account login failed");
        JsonNode loginData = mapper.readTree(login.getResponse().getContentAsByteArray());
        require(loginData.path("success").asBoolean(), "Evaluation account login rejected");
        var readable = mvc.perform(get("/api/documents").param("groupId", Long.toString(groupId))
                .header("Authorization", "Bearer " + loginData.path("data").path("accessToken").asText())).andReturn();
        require(readable.getResponse().getStatus() == 200, "Group access failed");
        membership.requireGroupReadable(readable.getRequest(), groupId);

        Map<Long, Long> documentByChunk = new LinkedHashMap<>();
        for (var entry : sources.entrySet()) {
            var stored = chunks.selectReadyActiveChunksByDocumentId(groupId, entry.getKey());
            require(stored.size() == 1 && normalize(stored.getFirst().getChunkText()).equals(normalize(fixture.documents().get(entry.getValue()))),
                    "Corpus changed or split: " + entry.getValue());
            documentByChunk.put(stored.getFirst().getId(), entry.getKey());
        }
        String digest = embeddingDigest();
        List<RetrievalAblation.CapturedRow> rows = new ArrayList<>();
        Map<String, Map<String, List<Document>>> all = new LinkedHashMap<>();
        RetrievalAblation.MODES.forEach(mode -> all.put(mode, new LinkedHashMap<>()));
        Logger logger = (Logger) LoggerFactory.getLogger(ElasticsearchChunkIndexService.class);
        var warnings = new ListAppender<ILoggingEvent>();
        warnings.start();
        logger.addAppender(warnings);
        try {
            for (var example : fixture.cases()) {
                membership.requireGroupReadable(readable.getRequest(), groupId);
                long start = System.nanoTime();
                var vectorHits = vectors.search(groupId, example.query(), 50);
                double vectorMs = elapsed(start);
                start = System.nanoTime();
                var keywordHits = keywords.search(groupId, example.query(), 50);
                double keywordMs = elapsed(start);
                require(warnings.list.stream().noneMatch(event -> event.getLevel().isGreaterOrEqual(Level.WARN)),
                        "Keyword adapter degraded; an outage must not be scored as zero hits");
                for (var hit : vectorHits) validateHit(hit.chunkId(), hit.documentId(), hit.chunkIndex(), hit.chunkText(), sources, documentByChunk, fixture);
                for (var hit : keywordHits) validateHit(hit.chunkId(), hit.documentId(), hit.chunkIndex(), hit.chunkText(), sources, documentByChunk, fixture);
                require(vectorHits.stream().map(PgVectorRetrievalAdapter.VectorHit::chunkId).distinct().count() == vectorHits.size()
                        && keywordHits.stream().map(ElasticsearchChunkIndexService.KeywordHit::chunkId).distinct().count() == keywordHits.size(),
                        "Duplicate raw channel hits");
                Map<String, List<String>> orders = new LinkedHashMap<>();
                for (String mode : RetrievalAblation.MODES) {
                    // Only channel gating is doubled: both active lists came from real adapters above.
                    var vectorGate = mock(PgVectorRetrievalAdapter.class);
                    var keywordGate = mock(ElasticsearchChunkIndexService.class);
                    when(vectorGate.search(groupId, example.query(), 50)).thenReturn(mode.equals("KEYWORD") ? List.of() : vectorHits);
                    when(keywordGate.search(groupId, example.query(), 50)).thenReturn(mode.equals("VECTOR") ? List.of() : keywordHits);
                    AtomicReference<List<Document>> inputs = new AtomicReference<>();
                    EvidenceReranker recorder = (query, docs) -> {
                        inputs.set(List.copyOf(docs));
                        return new EvidenceReranker.Result(docs, "DISABLED");
                    };
                    var retriever = new HybridEvidenceRetriever(vectorGate, keywordGate, chunks, planner,
                            new EvidenceSelector(EvidenceSelectionProperties.defaults(), recorder));
                    retriever.retrieve(groupId, example.query(), 5);
                    verify(vectorGate).search(groupId, example.query(), 50);
                    verify(keywordGate).search(groupId, example.query(), 50);
                    require(inputs.get() != null, "Missing pre-rerank candidates");
                    all.get(mode).put(example.id(), inputs.get());
                    orders.put(mode, inputs.get().stream().map(doc -> RetrievalSnapshot.key(example.id(),
                            sources.get(((Number) doc.getMetadata().get("documentId")).longValue()))).toList());
                }
                rows.add(new RetrievalAblation.CapturedRow(example.id(),
                        vectorHits.stream().map(hit -> new RetrievalAblation.Hit(hit.chunkId(), sources.get(hit.documentId()), hit.score())).toList(),
                        keywordHits.stream().map(hit -> new RetrievalAblation.Hit(hit.chunkId(), sources.get(hit.documentId()), hit.rawScore())).toList(),
                        orders, vectorMs, keywordMs));
                System.out.println("Ablation captured " + example.id() + ": vector=" + vectorHits.size() + ", keyword=" + keywordHits.size());
            }
        } finally {
            logger.detachAppender(warnings);
            warnings.stop();
        }
        for (String mode : RetrievalAblation.MODES) {
            var assembled = RetrievalSnapshot.assemble(fixture, all.get(mode), sources, groupId);
            for (int i = 0; i < assembled.cases().size(); i++) {
                var current = assembled.cases().get(i);
                require(current.relevance().equals(snapshot.cases().get(i).relevance()), "Annotation drift");
                for (String id : current.candidates()) require(assembled.documents().get(id).equals(snapshot.documents().get(id)), "Candidate text drift");
            }
            if (mode.equals("HYBRID")) require(assembled.equals(snapshot), "Hybrid text/order drift: do not reuse old rerank output");
        }
        verifyNoInteractions(chatProvider, providerReranker);
        var capture = new RetrievalAblation.Capture(Instant.now().toString(), datasetHash, RetrievalAblation.hash(fixturePath),
                "qllama/bge-small-zh-v1.5:latest", digest, 50, 30, 60, 1, 4096, 0,
                "ORIGINAL_QUERY_NO_PLANNER", "REAL_ADAPTER_HITS_ONCE_PER_QUERY_REPLAYED_THROUGH_PRODUCTION_RETRIEVER_WITH_CHANNEL_GATES", rows);
        Files.createDirectories(output.toAbsolutePath().getParent());
        dataMapper.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), capture);
        System.out.println("All 30 hybrid inputs exactly match reviewed snapshot; new Qwen/chat requests=0; capture=" + output);
    }

    private void validateHit(long chunkId, long documentId, int chunkIndex, String text, Map<Long, String> sources,
                             Map<Long, Long> documentByChunk, RerankEvaluation.Dataset fixture) {
        require(Objects.equals(documentByChunk.get(chunkId), documentId) && chunkIndex == 0
                && sources.containsKey(documentId) && normalize(text).equals(normalize(fixture.documents().get(sources.get(documentId)))),
                "Index hit differs from the authorized, ready synthetic corpus");
    }

    private String embeddingDigest() throws Exception {
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            var response = client.send(HttpRequest.newBuilder(URI.create("http://localhost:11434/api/tags"))
                    .timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofString());
            require(response.statusCode() == 200, "Cannot verify local embedding model");
            for (var model : mapper.readTree(response.body()).path("models")) {
                if ("qllama/bge-small-zh-v1.5:latest".equals(model.path("name").asText())) {
                    String digest = model.path("digest").asText();
                    require(digest.equals("9a2067741c2aacd74a623b0e09b40ab437bc4a5ebe9cd59a776376e414931c89"), "Embedding model differs from original capture");
                    return digest;
                }
            }
        }
        throw new IllegalArgumentException("Original embedding model unavailable");
    }

    private static String normalize(String text) { return text.strip().replaceAll("\\s+", " "); }
    private static double elapsed(long start) { return (System.nanoTime() - start) / 1_000_000.0; }
}
