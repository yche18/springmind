package com.yche.springmind.qa.evaluation;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yche.springmind.retrieval.elasticsearch.ElasticsearchChunkIndexService;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static com.yche.springmind.qa.evaluation.RetrievalAblation.require;

/** No application boot: only loopback Elasticsearch and pinned local embeddings. */
@EnabledIfSystemProperty(named = "eval.multihop", matches = "true")
class LiveMultiHopRun {
    static final String INDEX = "springmind_eval_multihop_20261011";
    static final Path CACHE = Path.of("target/multihop-cache");
    final ObjectMapper mapper = MultiHopData.M;
    @Test void collect() throws Exception {
        require(Boolean.getBoolean("eval.confirmLocalWrites"), "Explicit isolated local index writes required");
        var corpus = MultiHopData.corpus(); MultiHopData.verify(corpus);
        var sample = MultiHopData.sample();
        var output = MultiHopData.OUT.resolve("capture.json");
        require(!Files.exists(output), "Existing captures are immutable; use the offline report instead");
        Files.createDirectories(CACHE); Files.createDirectories(MultiHopData.OUT);
        String freeze = RetrievalAblation.hash(MultiHopData.ROOT.resolve("freeze.json"));
        if (!Files.exists(CACHE.resolve("freeze.txt"))) Files.writeString(CACHE.resolve("freeze.txt"), freeze);
        require(Files.readString(CACHE.resolve("freeze.txt")).equals(freeze), "Wrong cache provenance");
        String started = Instant.now().toString();
        var model = EmbeddingComparison.models().get("BGE_M3");
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            boolean found = false;
            for (var tag : call(client, 11435, "GET", "/api/tags", null).path("models"))
                if (tag.path("name").asText().equals(model.tag())) found = tag.path("digest").asText().equals(model.digest());
            require(found, "Pinned BGE-M3 missing");
            String ollama = call(client, 11435, "GET", "/api/version", null).path("version").asText();
            String elastic = call(client, 9200, "GET", "/", null).path("version").path("number").asText();
            index(client, corpus, freeze);
            Map<String, String> inputs = new LinkedHashMap<>();
            corpus.chunks().forEach((id, c) -> inputs.put("c:" + id, c.text()));
            sample.questions().forEach(q -> inputs.put("q:" + q.id(), q.query()));
            Map<String, float[]> vectors;
            try { vectors = encode(client, inputs, model); }
            finally { call(client, 11435, "POST", "/api/generate", Map.of("model", model.tag(), "keep_alive", 0)); }
            List<MultiHopEvaluation.Row> rows = new ArrayList<>();
            for (var q : sample.questions()) {
                var response = call(client, 9200, "POST", "/" + INDEX + "/_search", FilenameAblation.request(1L, q.query(), true));
                require(!response.path("timed_out").asBoolean() && response.path("_shards").path("failed").asInt() == 0, "Incomplete keyword query");
                List<MultiHopEvaluation.Hit> keyword = new ArrayList<>();
                for (var h : response.path("hits").path("hits")) keyword.add(new MultiHopEvaluation.Hit(Long.parseLong(h.path("_id").asText()), h.path("_score").asDouble()));
                var queryVector = vectors.get("q:" + q.id());
                var vector = corpus.chunks().keySet().stream().map(id -> new MultiHopEvaluation.Hit(id, EmbeddingComparison.cosine(queryVector, vectors.get("c:" + id))))
                        .sorted(Comparator.comparingDouble(MultiHopEvaluation.Hit::score).reversed().thenComparingLong(MultiHopEvaluation.Hit::id)).limit(50).toList();
                rows.add(new MultiHopEvaluation.Row(q.id(), keyword, vector, MultiHopReplay.run(corpus, q, keyword, vector)));
                System.out.println("Local retrieval/replay " + rows.size() + "/60 " + q.id());
            }
            Map<Long, Long> docs = new TreeMap<>(); corpus.chunks().forEach((id, c) -> docs.put(id, c.documentId()));
            Map<String, String> hashes = new TreeMap<>();
            try (var files = Files.list(CACHE)) { for (var f : files.sorted().toList()) if (Files.isRegularFile(f)) hashes.put(f.getFileName().toString(), RetrievalAblation.hash(f)); }
            MultiHopData.verify(corpus);
            var capture = new MultiHopEvaluation.Capture(started, Instant.now().toString(), freeze, model.digest(), elastic, ollama, INDEX, 0, hashes, docs, rows);
            MultiHopEvaluation.analyze(sample, capture);
            ChallengeData.write(output, capture);
        }
    }
    void index(HttpClient client, MultiHopData.Corpus corpus, String freeze) throws Exception {
        if (!Files.exists(CACHE.resolve("index-created.json"))) {
            var service = new ElasticsearchChunkIndexService(mapper, "localhost", 9200, "http", INDEX);
            ObjectNode definition = mapper.valueToTree(ReflectionTestUtils.invokeMethod(service, "buildCreateIndexRequestBody"));
            ((ObjectNode) definition.path("settings")).put("number_of_shards", 1).put("number_of_replicas", 0);
            ((ObjectNode) definition.path("mappings")).set("_meta", mapper.valueToTree(Map.of("freeze", freeze)));
            call(client, 9200, "PUT", "/" + INDEX, definition);
            ChallengeData.write(CACHE.resolve("index-created.json"), call(client, 9200, "GET", "/" + INDEX, null));
        }
        require(call(client, 9200, "GET", "/" + INDEX, null).path(INDEX).path("mappings").path("_meta").path("freeze").asText().equals(freeze), "Foreign index");
        if (!Files.exists(CACHE.resolve("index-ready.json"))) {
            var chunks = new ArrayList<>(corpus.chunks().values());
            for (int start = 0; start < chunks.size(); start += 200) {
                StringBuilder bulk = new StringBuilder();
                for (var c : chunks.subList(start, Math.min(chunks.size(), start + 200))) {
                    bulk.append(mapper.writeValueAsString(Map.of("index", Map.of("_id", Long.toString(c.id()))))).append('\n');
                    bulk.append(mapper.writeValueAsString(Map.of("groupId", 1L, "documentId", c.documentId(), "chunkId", c.id(), "chunkIndex", c.index(),
                            "status", "READY", "deleted", false, "fileName", c.title(), "chunkText", c.text()))).append('\n');
                }
                require(!call(client, 9200, "POST", "/" + INDEX + "/_bulk", bulk.toString()).path("errors").asBoolean(), "Bulk indexing failed");
            }
            call(client, 9200, "POST", "/" + INDEX + "/_refresh", null);
            call(client, 9200, "PUT", "/" + INDEX + "/_settings", Map.of("index.blocks.write", true));
            ChallengeData.write(CACHE.resolve("index-ready.json"), call(client, 9200, "GET", "/" + INDEX, null));
        }
        require(call(client, 9200, "GET", "/" + INDEX + "/_count", null).path("count").asInt() == corpus.chunks().size(), "Incomplete corpus index");
    }
    Map<String, float[]> encode(HttpClient client, Map<String, String> inputs, EmbeddingComparison.Model model) throws Exception {
        var entries = new ArrayList<>(inputs.entrySet());
        Map<String, float[]> vectors = new HashMap<>();
        for (int offset = 0; offset < entries.size(); offset += 32) {
            var batch = entries.subList(offset, Math.min(entries.size(), offset + 32));
            Path file = CACHE.resolve(String.format(Locale.ROOT, "batch-%05d.bin", offset / 32));
            if (!Files.exists(file)) {
                var response = call(client, 11435, "POST", "/api/embed", Map.of("model", model.tag(), "input", batch.stream().map(Map.Entry::getValue).toList(),
                        "truncate", false, "keep_alive", "5m", "options", Map.of("num_ctx", 8192, "num_batch", 8192)));
                require(response.path("embeddings").size() == batch.size(), "Embedding count mismatch");
                if (!Files.exists(CACHE.resolve("runtime.json"))) ChallengeData.write(CACHE.resolve("runtime.json"), call(client, 11435, "GET", "/api/ps", null));
                Path temporary = file.resolveSibling(file.getFileName() + ".partial");
                try (var out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(temporary)))) {
                    out.writeInt(batch.size());
                    for (int i = 0; i < batch.size(); i++) {
                        out.writeUTF(batch.get(i).getKey());
                        float[] v = mapper.convertValue(response.path("embeddings").get(i), float[].class);
                        EmbeddingComparison.validateVector(v, model.dimensions()); out.writeInt(v.length);
                        for (float value : v) out.writeFloat(value);
                    }
                }
                Files.move(temporary, file);
            }
            try (var in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
                require(in.readInt() == batch.size(), "Wrong cached batch count");
                for (var entry : batch) {
                    require(in.readUTF().equals(entry.getKey()) && in.readInt() == model.dimensions(), "Wrong cached vector identity");
                    float[] v = new float[model.dimensions()]; for (int j = 0; j < v.length; j++) v[j] = in.readFloat();
                    EmbeddingComparison.validateVector(v, model.dimensions()); vectors.put(entry.getKey(), v);
                }
                require(in.read() == -1, "Trailing cached bytes");
            }
            if (offset % 640 == 0 || offset + batch.size() == entries.size()) System.out.println("Embedded " + (offset + batch.size()) + "/" + entries.size());
        }
        return vectors;
    }
    JsonNode call(HttpClient client, int port, String method, String path, Object body) throws Exception {
        require((port == 9200 || port == 11435) && path.startsWith("/") && !path.contains("://"), "Loopback only");
        String encoded = body == null ? "" : body instanceof String s ? s : mapper.writeValueAsString(body);
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofMinutes(3))
                .header("Content-Type", body instanceof String ? "application/x-ndjson" : "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(encoded)).build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        require(response.statusCode() >= 200 && response.statusCode() < 300, "Local request failed: " + path + " HTTP " + response.statusCode()
                + " " + response.body().substring(0, Math.min(500, response.body().length())));
        return mapper.readTree(response.body());
    }
}
