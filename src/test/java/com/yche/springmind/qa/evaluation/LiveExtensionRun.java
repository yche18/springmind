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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static com.yche.springmind.qa.evaluation.RetrievalAblation.require;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Opt-in isolated ingestion and real local retrieval. All paid-model entry points are disabled. */
@EnabledIfSystemProperty(named = "eval.extension", matches = "true")
@SpringBootTest(properties = {"document.ingestion.worker-enabled=false", "qa.evidence.rerank-enabled=false",
        "spring.flyway.enabled=false", "spring.ai.vectorstore.pgvector.initialize-schema=false",
        "spring.ai.ollama.base-url=http://localhost:11434", "spring.ai.ollama.embedding.options.model=qllama/bge-small-zh-v1.5"})
@AutoConfigureMockMvc(print = org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint.NONE)
class LiveExtensionRun {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
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
    void ingestAndCapture() throws Exception {
        require(Boolean.getBoolean("eval.confirmLocalWrites"), "Explicit local-write confirmation required");
        require(RetrievalAblation.hash(ExtensionFixture.PATH).equals(ExtensionEvaluation.FIXTURE_HASH), "Pre-retrieval fixture changed");
        var fixture = ExtensionFixture.read();
        Path output = Path.of(System.getProperty("eval.extensionOutput", "target/retrieval-extension"));
        require(!Files.exists(output.resolve("capture.json")), "Do not overwrite an existing capture");
        String resume = System.getProperty("eval.extensionLocal", "");
        String run = resume.isBlank() ? "harborview-" + UUID.randomUUID().toString().substring(0, 8) : "";
        Path local = resume.isBlank() ? Path.of("evaluation/rerank/local", run) : Path.of(resume);
        Files.createDirectories(local);
        String username;
        String password;
        long groupId;
        Map<Long, String> sources = new LinkedHashMap<>();
        if (resume.isBlank()) {
            username = run;
            password = "Eval!" + UUID.randomUUID();
            data(post("/api/auth/register").contentType("application/json").content(mapper.writeValueAsBytes(Map.of(
                    "username", username, "email", username + "@example.invalid", "displayName", username, "password", password))));
            write(local.resolve("account.json"), Map.of("username", username, "password", password));
        } else {
            var account = mapper.readTree(local.resolve("account.json").toFile());
            var manifest = mapper.readTree(local.resolve("manifest.json").toFile());
            require(ExtensionEvaluation.FIXTURE_HASH.equals(manifest.path("fixtureSha256").asText()), "Resume fixture mismatch");
            username = account.path("username").asText();
            password = account.path("password").asText();
            run = manifest.path("runId").asText();
            require(run.startsWith("harborview-") && username.equals(run), "Not an isolated extension account");
            manifest.path("documents").fields().forEachRemaining(entry -> sources.put(Long.parseLong(entry.getKey()), entry.getValue().asText()));
            require(new HashSet<>(sources.values()).size() == sources.size()
                    && fixture.documents().keySet().containsAll(sources.values()), "Resume corpus mismatch");
        }
        String token = data(post("/api/auth/login").contentType("application/json").content(mapper.writeValueAsBytes(
                Map.of("loginId", username, "password", password)))).path("accessToken").asText();
        String authorization = "Bearer " + token;
        if (resume.isBlank()) {
            groupId = data(post("/api/groups").header("Authorization", authorization).contentType("application/json")
                    .content(mapper.writeValueAsBytes(Map.of("name", "Retrieval extension " + run,
                            "description", "Fictional Harborview corpus; isolated evaluation data.")))).asLong();
        } else groupId = mapper.readTree(local.resolve("manifest.json").toFile()).path("groupId").asLong();
        require(groupId > 0, "Missing evaluation group");
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("runId", run);
        manifest.put("groupId", groupId);
        manifest.put("fixtureSha256", ExtensionEvaluation.FIXTURE_HASH);
        manifest.put("documents", sources);
        write(local.resolve("manifest.json"), manifest);
        for (var source : fixture.documents().entrySet()) {
            if (sources.containsValue(source.getKey())) continue;
            long documentId = data(multipart("/api/documents/upload").file(new MockMultipartFile("file", source.getKey() + ".txt",
                    "text/plain", source.getValue().getBytes(StandardCharsets.UTF_8))).param("groupId", Long.toString(groupId))
                    .header("Authorization", authorization)).asLong();
            require(documentId > 0, "Upload did not return document ID");
            sources.put(documentId, source.getKey());
            write(local.resolve("manifest.json"), manifest);
        }
        System.out.println("Extension uploads complete: group=" + groupId + ", documents=66, privateManifest=" + local.resolve("manifest.json"));
        long deadline = System.nanoTime() + Duration.ofMinutes(8).toNanos();
        int checks = 0;
        while (true) {
            var states = jdbc.queryForList("select status from documents where group_id = ? and deleted = false", String.class, groupId);
            if (states.size() == 66 && states.stream().allMatch("READY"::equals)) break;
            require(!states.contains("FAILED"), "An extension ingestion job failed; retain manifest for diagnosis");
            require(System.nanoTime() < deadline, "Ingestion timed out; resume using the private manifest directory");
            if (checks++ % 10 == 0) System.out.println("Extension ingestion READY=" + states.stream().filter("READY"::equals).count() + "/66");
            Thread.sleep(1000);
        }
        var readable = mvc.perform(get("/api/documents").param("groupId", Long.toString(groupId)).header("Authorization", authorization)).andReturn();
        require(readable.getResponse().getStatus() == 200, "Evaluation group read failed");
        membership.requireGroupReadable(readable.getRequest(), groupId);
        Map<String, ExtensionEvaluation.Chunk> stored = new LinkedHashMap<>();
        Map<Long, String> keyByChunk = new HashMap<>();
        for (var source : sources.entrySet()) {
            var parts = chunks.selectReadyActiveChunksByDocumentId(groupId, source.getKey());
            require(!parts.isEmpty(), "Missing READY document chunks");
            if (source.getValue().contains("guide")) require(parts.size() > 1, "Long handbook did not split");
            for (var part : parts) {
                require(part.getGroupId() == groupId && part.getDocumentId().equals(source.getKey())
                        && ExtensionFixture.normalize(fixture.documents().get(source.getValue())).contains(ExtensionFixture.normalize(part.getChunkText())),
                        "Ingested content differs from frozen document");
                String key = source.getValue() + "--c" + String.format(Locale.ROOT, "%03d", part.getChunkIndex());
                require(keyByChunk.put(part.getId(), key) == null, "Duplicate chunk ID");
                stored.put(key, new ExtensionEvaluation.Chunk(part.getId(), source.getKey(), source.getValue(), part.getChunkIndex(), part.getChunkText()));
            }
        }
        require(stored.size() > 50, "Corpus must exceed channel candidate limit");
        String embeddingDigest = verifyIndexes(groupId, stored, keyByChunk);
        var corpus = new ExtensionEvaluation.Corpus(ExtensionEvaluation.FIXTURE_HASH, Instant.now().toString(),
                "PENDING_HUMAN_REVIEW", groupId, stored, ExtensionEvaluation.project(fixture, stored));
        Files.createDirectories(output);
        Path corpusPath = output.resolve("corpus.json");
        require(!Files.exists(corpusPath), "Use a new output directory rather than replacing pre-query judgments");
        write(corpusPath, corpus);
        String corpusHash = RetrievalAblation.hash(corpusPath);
        System.out.println("Frozen pre-query corpus: chunks=" + stored.size() + ", sha256=" + corpusHash);

        List<ExtensionEvaluation.CapturedRow> rows = new ArrayList<>();
        Logger logger = (Logger) LoggerFactory.getLogger(ElasticsearchChunkIndexService.class);
        var warnings = new ListAppender<ILoggingEvent>();
        warnings.start();
        logger.addAppender(warnings);
        try {
            for (var question : fixture.cases()) {
                membership.requireGroupReadable(readable.getRequest(), groupId);
                long start = System.nanoTime();
                var vector = vectors.search(groupId, question.query(), 50);
                double vectorMs = elapsed(start);
                start = System.nanoTime();
                var keyword = keywords.search(groupId, question.query(), 50);
                double keywordMs = elapsed(start);
                require(warnings.list.stream().noneMatch(event -> event.getLevel().isGreaterOrEqual(Level.WARN)), "Keyword search degraded; do not score an outage");
                for (var hit : vector) validateHit(stored, keyByChunk, hit.chunkId(), hit.documentId(), hit.chunkIndex(), hit.chunkText());
                for (var hit : keyword) validateHit(stored, keyByChunk, hit.chunkId(), hit.documentId(), hit.chunkIndex(), hit.chunkText());
                var vectorHits = vector.stream().map(hit -> new ExtensionEvaluation.Hit(keyByChunk.get(hit.chunkId()), hit.score())).toList();
                var keywordHits = keyword.stream().map(hit -> new ExtensionEvaluation.Hit(keyByChunk.get(hit.chunkId()), hit.rawScore())).toList();
                var orders = ExtensionEvaluation.orders(vectorHits, keywordHits, stored);
                Map<String, List<ExtensionEvaluation.Window>> candidateWindows = new LinkedHashMap<>();
                Map<String, List<ExtensionEvaluation.Window>> selectedWindows = new LinkedHashMap<>();
                for (String mode : ExtensionEvaluation.MODES) {
                    var vectorGate = mock(PgVectorRetrievalAdapter.class);
                    var keywordGate = mock(ElasticsearchChunkIndexService.class);
                    when(vectorGate.search(groupId, question.query(), 50)).thenReturn(mode.equals("KEYWORD") ? List.of() : vector);
                    when(keywordGate.search(groupId, question.query(), 50)).thenReturn(mode.equals("VECTOR") ? List.of() : keyword);
                    AtomicReference<List<Document>> inputs = new AtomicReference<>();
                    EvidenceReranker recorder = (query, docs) -> {
                        inputs.set(List.copyOf(docs));
                        return new EvidenceReranker.Result(docs, "DISABLED");
                    };
                    var retriever = new HybridEvidenceRetriever(vectorGate, keywordGate, chunks, planner,
                            new EvidenceSelector(EvidenceSelectionProperties.defaults(), recorder));
                    var result = retriever.retrieve(groupId, question.query(), 5);
                    verify(vectorGate).search(groupId, question.query(), 50);
                    verify(keywordGate).search(groupId, question.query(), 50);
                    require(inputs.get() != null, "Candidate interception failed");
                    candidateWindows.put(mode, windows(inputs.get(), corpus));
                    selectedWindows.put(mode, windows(result.documents(), corpus));
                }
                rows.add(new ExtensionEvaluation.CapturedRow(question.id(), vectorHits, keywordHits, orders, candidateWindows,
                        selectedWindows, vectorMs, keywordMs));
                System.out.println("Extension captured " + question.id() + ": vector=" + vector.size() + ", keyword=" + keyword.size());
            }
        } finally {
            logger.detachAppender(warnings);
            warnings.stop();
        }
        verifyNoInteractions(chatProvider, providerReranker);
        require(RetrievalAblation.hash(corpusPath).equals(corpusHash), "Judgments changed during retrieval");
        var capture = new ExtensionEvaluation.Capture(ExtensionEvaluation.FIXTURE_HASH, corpusHash, Instant.now().toString(),
                "qllama/bge-small-zh-v1.5:latest", embeddingDigest, 50, 30, 60, 4096, 1, 0, rows);
        ExtensionEvaluation.compare(fixture, corpus, capture, corpusHash, "not-written-yet");
        write(output.resolve("capture.json"), capture);
        manifest.put("completedAt", capture.completedAt());
        manifest.put("chunks", stored.size());
        write(local.resolve("manifest.json"), manifest);
        System.out.println("Extension complete; new Qwen/chat requests=0; output=" + output.toAbsolutePath());
    }

    private List<ExtensionEvaluation.Window> windows(List<Document> documents, ExtensionEvaluation.Corpus corpus) {
        List<ExtensionEvaluation.Window> result = new ArrayList<>();
        for (var document : documents) {
            var metadata = document.getMetadata();
            require(((Number) metadata.get("groupId")).longValue() == corpus.groupId(), "Foreign evidence window");
            long documentId = ((Number) metadata.get("documentId")).longValue();
            int first = ((Number) metadata.get("startChunkIndex")).intValue();
            int last = ((Number) metadata.get("endChunkIndex")).intValue();
            var parts = corpus.chunks().entrySet().stream().filter(entry -> entry.getValue().documentId() == documentId
                    && entry.getValue().index() >= first && entry.getValue().index() <= last)
                    .sorted(Comparator.comparingInt(entry -> entry.getValue().index())).toList();
            require(!parts.isEmpty(), "Empty/foreign evidence window");
            result.add(new ExtensionEvaluation.Window(parts.getFirst().getValue().document(), parts.stream().map(Map.Entry::getKey).toList(), document.getText()));
        }
        return result;
    }

    private void validateHit(Map<String, ExtensionEvaluation.Chunk> stored, Map<Long, String> keys,
                             long chunkId, long documentId, int index, String text) {
        var chunk = stored.get(keys.get(chunkId));
        require(chunk != null && chunk.documentId() == documentId && chunk.index() == index
                && ExtensionFixture.normalize(chunk.text()).equals(ExtensionFixture.normalize(text)), "Index drift/foreign hit");
    }

    private String verifyIndexes(long groupId, Map<String, ExtensionEvaluation.Chunk> stored, Map<Long, String> keys) throws Exception {
        require(jdbc.queryForObject("select count(*) from vector_store where metadata->>'groupId' = ?", Long.class,
                Long.toString(groupId)) == stored.size(), "Vector index does not contain the complete extension corpus");
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            var request = HttpRequest.newBuilder(URI.create("http://localhost:9200/springmind_document_chunks/_search"))
                    .timeout(Duration.ofSeconds(15)).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(Map.of("size", 1000,
                            "query", Map.of("term", Map.of("groupId", groupId)))))).build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            require(response.statusCode() == 200, "Keyword index validation failed");
            var hits = mapper.readTree(response.body()).path("hits").path("hits");
            require(hits.size() == stored.size(), "Keyword index count differs from READY corpus");
            Set<Long> seen = new HashSet<>();
            for (var hit : hits) {
                var source = hit.path("_source");
                require(source.path("groupId").asLong() == groupId && source.path("status").asText().equals("READY")
                        && !source.path("deleted").asBoolean() && seen.add(source.path("chunkId").asLong()), "Invalid indexed source");
                validateHit(stored, keys, source.path("chunkId").asLong(), source.path("documentId").asLong(),
                        source.path("chunkIndex").asInt(), source.path("chunkText").asText());
            }
            response = client.send(HttpRequest.newBuilder(URI.create("http://localhost:11434/api/tags"))
                    .timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofString());
            require(response.statusCode() == 200, "Cannot verify local embedding model");
            for (var model : mapper.readTree(response.body()).path("models")) if (model.path("name").asText().equals("qllama/bge-small-zh-v1.5:latest")) {
                String digest = model.path("digest").asText();
                require(digest.equals("9a2067741c2aacd74a623b0e09b40ab437bc4a5ebe9cd59a776376e414931c89"), "Unexpected embedding model version");
                return digest;
            }
        }
        throw new IllegalArgumentException("Embedding model missing");
    }

    private JsonNode data(RequestBuilder request) throws Exception {
        var response = mvc.perform(request).andReturn().getResponse();
        require(response.getStatus() == 200, "Evaluation setup API failed, HTTP " + response.getStatus());
        JsonNode json = mapper.readTree(response.getContentAsByteArray());
        require(json.path("success").asBoolean(), "Evaluation setup rejected");
        return json.path("data");
    }

    private void write(Path path, Object value) throws Exception { mapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), value); }
    private static double elapsed(long start) { return (System.nanoTime() - start) / 1_000_000.0; }
}
