package com.yche.springmind.qa.evaluation;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yche.springmind.retrieval.elasticsearch.ElasticsearchChunkIndexService;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static com.yche.springmind.qa.evaluation.RetrievalAblation.require;

/** Opt-in loopback-only collection into a new isolated index; no Spring or cloud clients. */
@EnabledIfSystemProperty(named = "eval.challenge", matches = "true")
class LiveChallengeRun {
    private final ObjectMapper mapper = RerankEvaluationRun.datasetMapper();

    @Test
    void collect() throws Exception {
        require(Boolean.getBoolean("eval.confirmLocalWrites"), "Explicit isolated-index write confirmation required");
        ChallengeData.verifyFreeze();
        var root = ChallengeData.OUTPUT;
        require(!Files.exists(root.resolve("capture.json")), "Capture exists; replay offline instead");
        Files.createDirectories(root);
        String freeze = RetrievalAblation.hash(ChallengeData.ROOT.resolve("freeze.json"));
        if (!Files.exists(root.resolve("run.json"))) ChallengeData.write(root.resolve("run.json"), Map.of("freezeSha256", freeze, "startedAt", Instant.now().toString()));
        require(mapper.readTree(root.resolve("run.json").toFile()).path("freezeSha256").asText().equals(freeze), "Cannot resume different inputs");
        var fixture = ChallengeData.fixture();
        var corpus = ChallengeData.corpus();
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            String ollama = call(client, 11434, "GET", "/api/version", null).path("version").asText();
            String elastic = call(client, 9200, "GET", "/", null).path("version").path("number").asText();
            var tags = call(client, 11434, "GET", "/api/tags", null);
            for (String mode : EmbeddingComparison.MODELS) {
                var model = EmbeddingComparison.models().get(mode);
                boolean matches = false;
                for (var tag : tags.path("models")) if (tag.path("name").asText().equals(model.tag())) matches = tag.path("digest").asText().equals(model.digest());
                require(matches, "Pinned local model missing: " + mode);
                var show = call(client, 11434, "POST", "/api/show", Map.of("model", model.tag()));
                String arch = show.path("model_info").path("general.architecture").asText();
                require(show.path("details").path("quantization_level").asText().equals(model.precision())
                        && show.path("model_info").path(arch + ".embedding_length").asInt() == model.dimensions(), "Local model metadata changed");
                Path path = root.resolve(mode.toLowerCase(Locale.ROOT) + "-vectors.json");
                if (!Files.exists(path)) ChallengeData.write(path, encode(client, mode, model));
                ChallengeEvaluation.validateVectors(mapper.readValue(path.toFile(), EmbeddingComparison.Vectors.class), mode);
            }
            if (!Files.exists(root.resolve("index-metadata.json"))) {
                var service = new ElasticsearchChunkIndexService(mapper, "localhost", 9200, "http", ChallengeData.INDEX);
                ObjectNode definition = mapper.valueToTree(ReflectionTestUtils.invokeMethod(service, "buildCreateIndexRequestBody"));
                ((ObjectNode) definition.path("settings")).put("number_of_shards", 1).put("number_of_replicas", 0);
                ((ObjectNode) definition.path("mappings")).set("_meta", mapper.valueToTree(Map.of("freezeSha256", freeze)));
                // PUT fails if an index already exists; never take over an unrelated index.
                call(client, 9200, "PUT", "/" + ChallengeData.INDEX, definition);
                ChallengeData.write(root.resolve("index-metadata.json"), call(client, 9200, "GET", "/" + ChallengeData.INDEX, null));
            }
            var metadata = call(client, 9200, "GET", "/" + ChallengeData.INDEX, null).path(ChallengeData.INDEX);
            require(metadata.path("mappings").path("_meta").path("freezeSha256").asText().equals(freeze), "Foreign evaluation index");
            require(metadata.path("settings").path("index").path("number_of_shards").asInt() == 1, "Shard count changed");
            if (!Files.exists(root.resolve("index-snapshot.json"))) {
                for (var entry : corpus.chunks().entrySet()) {
                    var c = entry.getValue();
                    call(client, 9200, "PUT", "/" + ChallengeData.INDEX + "/_create/" + entry.getKey(), Map.of(
                            "groupId", ChallengeData.GROUP, "documentId", c.documentId(), "chunkId", c.chunkId(), "chunkIndex", c.index(),
                            "status", "READY", "deleted", false, "fileName", fixture.documents().get(c.document()).filename(), "chunkText", c.text()));
                }
                call(client, 9200, "POST", "/" + ChallengeData.INDEX + "/_refresh", null);
                call(client, 9200, "PUT", "/" + ChallengeData.INDEX + "/_settings", Map.of("index.blocks.write", true));
                var snapshot = call(client, 9200, "POST", "/" + ChallengeData.INDEX + "/_search", Map.of("size", 1000, "track_total_hits", true, "query", Map.of("match_all", Map.of())));
                require(snapshot.path("hits").path("total").path("value").asInt() == corpus.chunks().size(), "Wrong index corpus size");
                Set<String> seen = new HashSet<>();
                for (var hit : snapshot.path("hits").path("hits")) {
                    String id = hit.path("_id").asText();
                    var c = corpus.chunks().get(id);
                    require(c != null && seen.add(id) && hit.path("_source").path("chunkText").asText().equals(c.text())
                            && hit.path("_source").path("fileName").asText().equals(fixture.documents().get(c.document()).filename()), "Index source mismatch");
                }
                require(seen.size() == corpus.chunks().size(), "Incomplete index snapshot");
                ChallengeData.write(root.resolve("index-snapshot.json"), snapshot);
            }
            require(call(client, 9200, "GET", "/" + ChallengeData.INDEX + "/_settings", null).path(ChallengeData.INDEX).path("settings").path("index").path("blocks").path("write").asBoolean(), "Evaluation index is not write-blocked");
            if (!Files.exists(root.resolve("keyword.json"))) {
                List<FilenameAblation.CapturedRow> rows = new ArrayList<>();
                for (var q : fixture.cases()) {
                    Map<String, JsonNode> requests = new LinkedHashMap<>();
                    Map<String, List<ExtensionEvaluation.Hit>> hits = new LinkedHashMap<>();
                    for (String mode : FilenameAblation.MODES) {
                        var request = FilenameAblation.request(ChallengeData.GROUP, q.query(), mode.equals("BODY_AND_FILENAME"));
                        requests.put(mode, request);
                        var response = call(client, 9200, "POST", "/" + ChallengeData.INDEX + "/_search", request);
                        require(!response.path("timed_out").asBoolean() && response.path("_shards").path("failed").asInt() == 0, "Incomplete search");
                        List<ExtensionEvaluation.Hit> captured = new ArrayList<>();
                        for (var hit : response.path("hits").path("hits")) {
                            require(corpus.chunks().containsKey(hit.path("_id").asText()) && hit.path("_score").isNumber(), "Foreign/unscored search hit");
                            captured.add(new ExtensionEvaluation.Hit(hit.path("_id").asText(), hit.path("_score").asDouble()));
                        }
                        hits.put(mode, captured);
                    }
                    rows.add(new FilenameAblation.CapturedRow(q.id(), requests, hits));
                    System.out.println("Local challenge keyword capture: " + q.id());
                }
                ChallengeData.write(root.resolve("keyword.json"), rows);
            }
            ChallengeData.verifyFreeze();
            Map<String, String> hashes = new LinkedHashMap<>();
            for (String file : List.of("current-vectors.json", "bge_m3-vectors.json", "keyword.json", "index-snapshot.json", "index-metadata.json", "run.json"))
                hashes.put(file, RetrievalAblation.hash(root.resolve(file)));
            ChallengeData.write(root.resolve("capture.json"), new ChallengeEvaluation.Capture(Instant.now().toString(), freeze, ChallengeData.INDEX,
                    ollama, elastic, hashes, 2 * (corpus.chunks().size() + fixture.cases().size()), 2 * fixture.cases().size(), 0));
        }
        ChallengeEvaluation.compare();
        System.out.println("Challenge capture complete: 282 local nonempty embeddings, 72 lexical searches, zero cloud calls");
    }

    private EmbeddingComparison.Vectors encode(HttpClient client, String mode, EmbeddingComparison.Model model) throws Exception {
        Map<String, String> inputs = new LinkedHashMap<>();
        ChallengeData.corpus().chunks().forEach((id, c) -> inputs.put("doc:" + id, c.text()));
        ChallengeData.fixture().cases().forEach(q -> inputs.put("query:" + q.id(), q.query()));
        String started = Instant.now().toString();
        Map<String, float[]> documents = new LinkedHashMap<>(), queries = new LinkedHashMap<>();
        Map<String, Double> times = new LinkedHashMap<>();
        Map<String, Long> tokens = new LinkedHashMap<>();
        JsonNode runtime;
        try {
            int count = 0;
            for (var input : inputs.entrySet()) {
                long start = System.nanoTime();
                var response = call(client, 11434, "POST", "/api/embed", Map.of("model", model.tag(), "input", input.getValue(), "truncate", false,
                        "keep_alive", "5m", "options", Map.of("num_ctx", model.contextLength())));
                require(response.path("embeddings").size() == 1, "Missing embedding");
                var v = mapper.convertValue(response.path("embeddings").get(0), float[].class);
                EmbeddingComparison.validateVector(v, model.dimensions());
                (input.getKey().startsWith("doc:") ? documents : queries).put(input.getKey().substring(input.getKey().indexOf(':') + 1), v);
                times.put(input.getKey(), (System.nanoTime() - start) / 1e6);
                tokens.put(input.getKey(), response.path("prompt_eval_count").asLong());
                if (++count % 10 == 0 || count == inputs.size()) System.out.println(mode + " encoded " + count + "/" + inputs.size());
            }
            runtime = call(client, 11434, "GET", "/api/ps", null);
        } finally { call(client, 11434, "POST", "/api/embed", Map.of("model", model.tag(), "input", "", "keep_alive", 0)); }
        return new EmbeddingComparison.Vectors(mode, started, Instant.now().toString(), model, documents, queries, times, tokens, runtime, inputs.size());
    }
    private JsonNode call(HttpClient client, int port, String method, String path, Object body) throws Exception {
        require((port == 11434 || port == 9200) && path.startsWith("/") && !path.contains("://"), "Loopback only");
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofMinutes(3))
                .header("Content-Type", "application/json").method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        require(response.statusCode() >= 200 && response.statusCode() < 300, "Local service " + port + " " + path + " failed: HTTP " + response.statusCode());
        return mapper.readTree(response.body());
    }
}
