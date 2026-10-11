package com.yche.springmind.qa.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static com.yche.springmind.qa.evaluation.RetrievalAblation.require;

/** Local Elasticsearch reads only; deliberately does not boot Spring or instantiate model clients. */
@EnabledIfSystemProperty(named = "eval.filename", matches = "true")
class LiveFilenameAblationRun {
    private static final String BASE = "http://localhost:9200";
    private static final String INDEX = "springmind_document_chunks";
    private final com.fasterxml.jackson.databind.ObjectMapper mapper = RerankEvaluationRun.datasetMapper();
    private String pit;

    @Test
    void captureBothKeywordArms() throws Exception {
        FilenameAblation.verifyApproval();
        Path output = FilenameAblation.output();
        require(!Files.exists(output.resolve("capture.json")) && !Files.exists(output.resolve("index-snapshot.json")),
                "Use a new output directory; do not overwrite an experiment");
        var fixture = ExtensionFixture.read();
        var corpus = FilenameAblation.corpus();
        String started = Instant.now().toString();
        Map<Long, String> keys = new HashMap<>();
        corpus.chunks().forEach((key, chunk) -> keys.put(chunk.chunkId(), key));
        List<FilenameAblation.CapturedRow> rows = new ArrayList<>();
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            JsonNode metadata = send(client, "GET", "/" + INDEX, null);
            pit = send(client, "POST", "/" + INDEX + "/_pit?keep_alive=2m", null).path("id").asText();
            require(!pit.isBlank(), "Could not open a consistent search view");
            try {
                ObjectNode all = mapper.valueToTree(Map.of("size", 1000, "track_total_hits", true,
                        "query", Map.of("term", Map.of("groupId", corpus.groupId()))));
                JsonNode snapshot = search(client, all);
                require(snapshot.path("hits").path("total").path("value").asInt() == corpus.chunks().size(), "Indexed corpus count changed");
                var sourceHits = snapshot.path("hits").path("hits");
                require(sourceHits.size() == corpus.chunks().size(), "Incomplete index snapshot");
                Set<String> seen = new HashSet<>();
                for (var hit : sourceHits) require(seen.add(validateSource(hit.path("_source"), corpus, keys)), "Duplicate indexed chunk");
                Files.createDirectories(output);
                // Persist source verification before any experimental query; keep it even after failure.
                mapper.writerWithDefaultPrettyPrinter().writeValue(output.resolve("index-snapshot.json").toFile(), sourceHits);
                for (int i = 0; i < fixture.cases().size(); i++) {
                    var question = fixture.cases().get(i);
                    Map<String, JsonNode> requests = new LinkedHashMap<>();
                    Map<String, List<ExtensionEvaluation.Hit>> hits = new LinkedHashMap<>();
                    var order = i % 2 == 0 ? FilenameAblation.MODES : FilenameAblation.MODES.reversed();
                    for (String mode : order) {
                        var request = FilenameAblation.request(corpus.groupId(), question.query(), mode.equals("BODY_AND_FILENAME"));
                        requests.put(mode, request);
                        var response = search(client, request);
                        List<ExtensionEvaluation.Hit> captured = new ArrayList<>();
                        for (var hit : response.path("hits").path("hits")) {
                            String key = validateSource(hit.path("_source"), corpus, keys);
                            require(hit.path("_score").isNumber(), "Missing keyword score");
                            captured.add(new ExtensionEvaluation.Hit(key, hit.path("_score").asDouble()));
                        }
                        hits.put(mode, captured);
                    }
                    rows.add(new FilenameAblation.CapturedRow(question.id(), requests, hits));
                    System.out.println("Filename ablation " + question.id() + ": body=" + hits.get("BODY_ONLY").size()
                            + ", withFilename=" + hits.get("BODY_AND_FILENAME").size());
                }
                var capture = new FilenameAblation.Capture(started, Instant.now().toString(), RetrievalAblation.hash(ExtensionFixture.PATH),
                        RetrievalAblation.hash(FilenameAblation.SOURCE.resolve("corpus.json")),
                        RetrievalAblation.hash(FilenameAblation.SOURCE.resolve("review-approval.json")), RetrievalAblation.hash(FilenameAblation.PROTOCOL),
                        RetrievalAblation.hash(FilenameAblation.SOURCE.resolve("capture.json")), metadata, 0, rows);
                FilenameAblation.compare(capture, "not-written-yet");
                mapper.writerWithDefaultPrettyPrinter().writeValue(output.resolve("capture.json").toFile(), capture);
            } finally {
                send(client, "DELETE", "/_pit", mapper.valueToTree(Map.of("id", pit)));
            }
        }
        System.out.println("Filename ablation complete: 48 searches, zero model requests; " + output.toAbsolutePath());
    }

    private String validateSource(JsonNode source, ExtensionEvaluation.Corpus corpus, Map<Long, String> keys) {
        String key = keys.get(source.path("chunkId").asLong());
        var chunk = corpus.chunks().get(key);
        require(chunk != null && source.path("groupId").asLong() == corpus.groupId()
                && source.path("documentId").asLong() == chunk.documentId() && source.path("chunkIndex").asInt() == chunk.index()
                && source.path("fileName").asText().equals(chunk.document() + ".txt")
                && source.path("chunkText").asText().equals(chunk.text()), "Index source drift or foreign hit");
        // Query responses omit these fields, but the full validation snapshot must include them.
        if (source.has("status")) require(source.path("status").asText().equals("READY") && !source.path("deleted").asBoolean(), "Inactive chunk");
        return key;
    }

    private JsonNode search(HttpClient client, ObjectNode original) throws Exception {
        ObjectNode request = original.deepCopy();
        request.set("pit", mapper.valueToTree(Map.of("id", pit, "keep_alive", "2m")));
        var response = send(client, "POST", "/_search", request);
        require(!response.path("timed_out").asBoolean() && response.path("_shards").path("failed").asInt() == 0
                && response.path("hits").path("hits").isArray(), "Incomplete Elasticsearch response");
        if (response.hasNonNull("pit_id")) pit = response.path("pit_id").asText();
        return response;
    }

    private JsonNode send(HttpClient client, String method, String path, JsonNode body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(BASE + path)).timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json").method(method,
                        body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        require(response.statusCode() >= 200 && response.statusCode() < 300,
                "Elasticsearch " + method + " " + path + " returned HTTP " + response.statusCode());
        return mapper.readTree(response.body());
    }
}
