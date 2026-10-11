package com.yche.springmind.qa.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yche.springmind.document.service.StaleProcessingDocumentRecoveryRunner;
import com.yche.springmind.groupmembership.service.GroupMembershipService;
import com.yche.springmind.ingestion.mapper.DocumentChunkMapper;
import com.yche.springmind.qa.rag.*;
import com.yche.springmind.qa.service.QueryPlanningService;
import com.yche.springmind.retrieval.elasticsearch.ElasticsearchChunkIndexService;
import com.yche.springmind.retrieval.vectorstore.PgVectorRetrievalAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.ai.document.Document;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Opt-in local integration run. No mock retrieval, embedding, storage or ingestion services. */
@EnabledIfSystemProperty(named = "eval.capture", matches = "true")
@SpringBootTest(properties = {"document.ingestion.worker-enabled=false", "qa.evidence.rerank-enabled=false"})
@AutoConfigureMockMvc(printOnlyOnFailure = false, print = org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint.NONE)
class LiveRetrievalCaptureRun {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired GroupMembershipService membership;
    @Autowired DocumentChunkMapper chunks;
    @Autowired PgVectorRetrievalAdapter vectors;
    @Autowired ElasticsearchChunkIndexService keywords;
    @Autowired QueryPlanningService planner;
    @Autowired EvidenceSelectionProperties selectionProperties;
    // Do not backfill unrelated jobs when this second application context starts.
    @MockBean StaleProcessingDocumentRecoveryRunner recovery;

    @Test
    void capture() throws Exception {
        if (!Boolean.getBoolean("eval.confirmLocalWrites")) {
            throw new IllegalArgumentException("Set eval.confirmLocalWrites=true to create a dedicated account/group and 16 documents");
        }
        byte[] fixtureBytes = Files.readAllBytes(Path.of("evaluation/rerank/sample.json"));
        var fixture = RerankEvaluationRun.datasetMapper().readValue(fixtureBytes, RerankEvaluation.Dataset.class);
        RerankEvaluation.validate(fixture);
        String runId = "northstar-" + UUID.randomUUID().toString().substring(0, 8);
        Path directory = Path.of("evaluation", "rerank", "local", runId);
        Files.createDirectories(directory);
        String password = "Eval!" + UUID.randomUUID();
        data(post("/api/auth/register").contentType("application/json").content(mapper.writeValueAsBytes(Map.of(
                "username", runId, "email", runId + "@example.invalid", "displayName", runId, "password", password))));
        String token = data(post("/api/auth/login").contentType("application/json").content(mapper.writeValueAsBytes(
                Map.of("loginId", runId, "password", password)))).path("accessToken").asText();
        String authorization = "Bearer " + token;
        // Keep only this newly generated test account's credentials locally, never in reports or logs.
        write(directory.resolve("account.json"), Map.of("username", runId, "password", password));
        long groupId = data(post("/api/groups").header("Authorization", authorization).contentType("application/json")
                .content(mapper.writeValueAsBytes(Map.of("name", "Rerank evaluation " + runId,
                        "description", "Synthetic Northstar corpus. Isolated evaluation data; not production policy.")))).asLong();
        if (groupId <= 0) throw new IllegalStateException("Group creation did not return an ID");
        Map<Long, String> sourceByDocumentId = new LinkedHashMap<>();
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("runId", runId);
        manifest.put("groupId", groupId);
        manifest.put("startedAt", Instant.now().toString());
        manifest.put("fixtureSha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(fixtureBytes)));
        manifest.put("documents", sourceByDocumentId);
        manifest.put("queryMode", "ORIGINAL_QUERY_NO_PLANNER");
        manifest.put("annotationStatus", "PROVISIONAL_INHERITED_SYNTHETIC_LABELS");
        manifest.put("candidateLimit", selectionProperties.candidateLimit());
        manifest.put("maxEvidenceTokens", selectionProperties.maxTokens());
        write(directory.resolve("manifest.json"), manifest);
        for (var source : fixture.documents().entrySet()) {
            long documentId = data(multipart("/api/documents/upload").file(new MockMultipartFile("file",
                    source.getKey() + ".txt", "text/plain", source.getValue().getBytes(StandardCharsets.UTF_8)))
                    .param("groupId", Long.toString(groupId)).header("Authorization", authorization)).asLong();
            if (documentId <= 0) throw new IllegalStateException("Upload did not return an ID");
            sourceByDocumentId.put(documentId, source.getKey());
            write(directory.resolve("manifest.json"), manifest);
        }
        System.out.println("Capture created isolated group=" + groupId + "; waiting for 16 real ingestion jobs. run=" + runId);
        long deadline = System.nanoTime() + java.time.Duration.ofMinutes(5).toNanos();
        while (true) {
            List<String> statuses = jdbc.queryForList("select status from documents where group_id = ? and deleted = false", String.class, groupId);
            if (statuses.size() == fixture.documents().size() && statuses.stream().allMatch("READY"::equals)) break;
            if (statuses.contains("FAILED")) throw new IllegalStateException("Synthetic ingestion failed; inspect only this group's jobs: " + groupId);
            if (System.nanoTime() > deadline) throw new IllegalStateException("Ingestion timeout; keep manifest and inspect the running backend worker");
            Thread.sleep(1000);
        }
        for (var source : sourceByDocumentId.entrySet()) {
            var stored = chunks.selectReadyActiveChunksByDocumentId(groupId, source.getKey());
            if (stored.size() != 1 || !normalize(stored.getFirst().getChunkText()).equals(normalize(fixture.documents().get(source.getValue())))) {
                throw new IllegalStateException("Source changed or split; manual label mapping required for " + source.getValue());
            }
        }
        // The authenticated API request supplies the same principal used by the normal membership guard.
        MvcResult readable = mvc.perform(get("/api/documents").param("groupId", Long.toString(groupId))
                .header("Authorization", authorization)).andReturn();
        if (readable.getResponse().getStatus() != 200) throw new IllegalStateException("Document access check failed");
        AtomicReference<List<Document>> lastInputs = new AtomicReference<>();
        EvidenceReranker recorder = (query, documents) -> {
            lastInputs.set(List.copyOf(documents));
            return new EvidenceReranker.Result(documents, "DISABLED");
        };
        var retriever = new HybridEvidenceRetriever(vectors, keywords, chunks, planner,
                new EvidenceSelector(selectionProperties, recorder));
        Map<String, List<Document>> captured = new LinkedHashMap<>();
        Map<String, Object> traces = new LinkedHashMap<>();
        for (var example : fixture.cases()) {
            membership.requireGroupReadable(readable.getRequest(), groupId);
            lastInputs.set(null);
            // Null user deliberately selects the production original-query fallback, with no chat API calls.
            var result = retriever.retrieve(groupId, example.query(), 5);
            if (lastInputs.get() == null) throw new IllegalStateException("Candidate interception did not execute");
            captured.put(example.id(), lastInputs.get());
            traces.put(example.id(), result);
            System.out.println("Captured " + example.id() + ": candidates=" + lastInputs.get().size());
        }
        var snapshot = RetrievalSnapshot.assemble(fixture, captured, sourceByDocumentId, groupId);
        write(directory.resolve("snapshot.json"), snapshot);
        write(directory.resolve("candidates.json"), captured);
        write(directory.resolve("traces.json"), traces);
        manifest.put("completedAt", Instant.now().toString());
        write(directory.resolve("manifest.json"), manifest);
        System.out.println("Capture complete: " + directory.toAbsolutePath() + "; no paid model calls made");
    }

    private JsonNode data(RequestBuilder request) throws Exception {
        var response = mvc.perform(request).andReturn().getResponse();
        if (response.getStatus() != 200) throw new IllegalStateException("Evaluation setup API failed, HTTP " + response.getStatus());
        JsonNode json = mapper.readTree(response.getContentAsByteArray());
        if (!json.path("success").asBoolean()) throw new IllegalStateException("Evaluation setup API rejected request");
        return json.path("data");
    }

    private void write(Path path, Object value) throws Exception {
        mapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), value);
    }

    private String normalize(String text) { return text.strip().replaceAll("\\s+", " "); }
}
