package com.yche.springmind.qa.evaluation;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static com.yche.springmind.qa.evaluation.RetrievalAblation.require;

/** Local full-corpus benchmark; source downloads are separate from inference. */
@EnabledIfSystemProperty(named = "eval.beir", matches = "true")
class LiveBeirRun {
    private final ObjectMapper mapper = RerankEvaluationRun.datasetMapper();
    private final int embeddingPort = Integer.getInteger("eval.beirOllamaPort", 11434);
    @Test void collect() throws Exception {
        require(Boolean.getBoolean("eval.confirmLocalWrites"), "Explicit isolated-index writes required");
        require(embeddingPort == 11434 || embeddingPort == 11435, "Only primary or isolated local Ollama is allowed");
        BeirData.verify();
        String freeze = RetrievalAblation.hash(BeirData.PROTOCOL.resolve("freeze.json"));
        var model = EmbeddingComparison.models().get("BGE_M3");
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            var tags = call(client, 11434, "GET", "/api/tags", null);
            boolean found = false;
            for (var tag : tags.path("models")) if (tag.path("name").asText().equals(model.tag())) found = tag.path("digest").asText().equals(model.digest());
            require(found, "Pinned BGE-M3 not installed");
            String ollama = call(client, 11434, "GET", "/api/version", null).path("version").asText();
            String elastic = call(client, 9200, "GET", "/", null).path("version").path("number").asText();
            for (String name : BeirData.DATASETS) {
                Path output = BeirData.capturePath(name);
                if (Files.exists(output)) {
                    var old = BeirData.capture(name);
                    require(old.freezeSha256().equals(freeze) && old.model().equals(model), "Existing capture has different inputs");
                    BeirEvaluation.compare(old); System.out.println(name + " already captured; no new requests"); continue;
                }
                var data = BeirData.read(name);
                var cache = Path.of("target/beir-cache", name); Files.createDirectories(cache);
                if (!Files.exists(cache.resolve("freeze.txt"))) Files.writeString(cache.resolve("freeze.txt"), freeze);
                require(Files.readString(cache.resolve("freeze.txt")).equals(freeze), "Wrong cache provenance");
                String started = Instant.now().toString();
                Map<String, String> inputs = new LinkedHashMap<>();
                data.documents().forEach((id, text) -> inputs.put("d:" + id, text)); data.queries().forEach((id, text) -> inputs.put("q:" + id, text));
                Map<String, float[]> vectors;
                try { vectors = encode(client, inputs, cache, model); }
                finally { call(client, 11434, "POST", "/api/generate", Map.of("model", model.tag(), "keep_alive", 0)); }
                String index = "springmind_eval_beir_" + name + "_20261011";
                if (!Files.exists(cache.resolve("index-created.json"))) {
                    var definition = Map.of("settings", Map.of("number_of_shards", 1, "number_of_replicas", 0), "mappings",
                            Map.of("_meta", Map.of("freeze", freeze), "properties", Map.of("doc_id", Map.of("type", "keyword"),
                                    "text", Map.of("type", "text", "analyzer", "english"))));
                    call(client, 9200, "PUT", "/" + index, definition);
                    ChallengeData.write(cache.resolve("index-created.json"), call(client, 9200, "GET", "/" + index, null));
                }
                require(call(client, 9200, "GET", "/" + index, null).path(index).path("mappings").path("_meta").path("freeze").asText().equals(freeze), "Foreign benchmark index");
                if (!Files.exists(cache.resolve("index-ready.json"))) {
                    var docs = new ArrayList<>(data.documents().entrySet());
                    for (int start = 0; start < docs.size(); start += 200) {
                        StringBuilder bulk = new StringBuilder();
                        for (var d : docs.subList(start, Math.min(docs.size(), start + 200))) {
                            bulk.append(mapper.writeValueAsString(Map.of("index", Map.of("_id", d.getKey())))).append('\n');
                            bulk.append(mapper.writeValueAsString(Map.of("doc_id", d.getKey(), "text", d.getValue()))).append('\n');
                        }
                        require(!call(client, 9200, "POST", "/" + index + "/_bulk", bulk.toString()).path("errors").asBoolean(), "Bulk indexing failed");
                    }
                    call(client, 9200, "POST", "/" + index + "/_refresh", null);
                    require(call(client, 9200, "GET", "/" + index + "/_count", null).path("count").asInt() == data.documents().size(), "Incomplete index");
                    call(client, 9200, "PUT", "/" + index + "/_settings", Map.of("index.blocks.write", true));
                    ChallengeData.write(cache.resolve("index-ready.json"), call(client, 9200, "GET", "/" + index, null));
                }
                List<BeirEvaluation.CapturedRow> rows = new ArrayList<>();
                int count = 0;
                for (var q : data.queries().entrySet()) {
                    var request = Map.of("size", 1000, "_source", false, "sort", List.of(Map.of("_score", "desc"), Map.of("doc_id", "asc")),
                            "query", Map.of("bool", Map.of("must", Map.of("match", Map.of("text", q.getValue())), "must_not", Map.of("term", Map.of("doc_id", q.getKey())))));
                    var response = call(client, 9200, "POST", "/" + index + "/_search", request);
                    require(!response.path("timed_out").asBoolean() && response.path("_shards").path("failed").asInt() == 0, "Incomplete keyword search");
                    List<BeirEvaluation.Hit> bm25 = new ArrayList<>();
                    for (var h : response.path("hits").path("hits")) bm25.add(new BeirEvaluation.Hit(h.path("_id").asText(), h.path("_score").asDouble()));
                    var queryVector = vectors.get("q:" + q.getKey());
                    var dense = data.documents().keySet().stream().filter(id -> !id.equals(q.getKey()))
                            .map(id -> new BeirEvaluation.Hit(id, EmbeddingComparison.cosine(queryVector, vectors.get("d:" + id))))
                            .sorted(Comparator.comparingDouble(BeirEvaluation.Hit::score).reversed().thenComparing(BeirEvaluation.Hit::id)).limit(1000).toList();
                    rows.add(new BeirEvaluation.CapturedRow(q.getKey(), data.qrels().get(q.getKey()), bm25, dense));
                    if (++count % 25 == 0 || count == data.queries().size()) System.out.println(name + " searched " + count + "/" + data.queries().size());
                }
                Map<String, String> hashes = new TreeMap<>();
                try (var files = Files.list(cache)) { for (var file : files.sorted().toList()) hashes.put(file.getFileName().toString(), RetrievalAblation.hash(file)); }
                BeirData.verify();
                var capture = new BeirEvaluation.Capture(name, started, Instant.now().toString(), freeze, model, ollama, elastic, index,
                        data.documents().size(), data.queries().size(), inputs.size(), 0, hashes, rows);
                BeirEvaluation.compare(capture);
                BeirData.writeCapture(name, capture);
                System.out.println(name + " complete: " + rows.size() + " queries, full corpus, zero cloud inference");
            }
        }
    }
    private Map<String, float[]> encode(HttpClient client, Map<String, String> inputs, Path cache, EmbeddingComparison.Model model) throws Exception {
        var entries = new ArrayList<>(inputs.entrySet());
        Map<String, float[]> vectors = new HashMap<>();
        for (int offset = 0; offset < entries.size(); offset += 16) {
            var batch = entries.subList(offset, Math.min(entries.size(), offset + 16));
            Path file = cache.resolve(String.format(Locale.ROOT, "batch-%05d.bin", offset / 16));
            if (!Files.exists(file)) {
                var response = call(client, 11434, "POST", "/api/embed", Map.of("model", model.tag(), "input", batch.stream().map(Map.Entry::getValue).toList(),
                        "truncate", false, "keep_alive", "5m", "options", Map.of("num_ctx", model.contextLength(), "num_batch", model.contextLength())));
                require(response.path("embeddings").size() == batch.size(), "Wrong embedding batch size");
                if (!Files.exists(cache.resolve("runtime.json"))) ChallengeData.write(cache.resolve("runtime.json"), Map.of(
                        "port", embeddingPort, "resumedAfterTexts", offset, "numCtx", model.contextLength(), "numBatch", model.contextLength(),
                        "loadedModel", call(client, 11434, "GET", "/api/ps", null)));
                Path temp = file.resolveSibling(file.getFileName() + ".partial");
                try (var out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(temp)))) {
                    out.writeInt(batch.size());
                    for (int i = 0; i < batch.size(); i++) {
                        out.writeUTF(batch.get(i).getKey());
                        var v = mapper.convertValue(response.path("embeddings").get(i), float[].class);
                        EmbeddingComparison.validateVector(v, model.dimensions()); out.writeInt(v.length);
                        for (float value : v) out.writeFloat(value);
                    }
                }
                Files.move(temp, file);
            }
            try (var in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
                require(in.readInt() == batch.size(), "Wrong cached batch size");
                for (var input : batch) {
                    require(in.readUTF().equals(input.getKey()) && in.readInt() == model.dimensions(), "Wrong cached vector identity/dimension");
                    float[] v = new float[model.dimensions()]; for (int j = 0; j < v.length; j++) v[j] = in.readFloat();
                    EmbeddingComparison.validateVector(v, model.dimensions()); vectors.put(input.getKey(), v);
                }
                require(in.read() == -1, "Unexpected cached bytes");
            }
            if (offset % 160 == 0 || offset + batch.size() == entries.size()) System.out.println(cache.getFileName() + " embedded " + (offset + batch.size()) + "/" + entries.size());
        }
        return vectors;
    }
    private JsonNode call(HttpClient client, int port, String method, String path, Object body) throws Exception {
        require((port == 11434 || port == 9200) && path.startsWith("/") && !path.contains("://"), "Loopback only");
        if (port == 11434) port = embeddingPort;
        String encoded = body == null ? "" : body instanceof String s ? s : mapper.writeValueAsString(body);
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofMinutes(3))
                .header("Content-Type", body instanceof String ? "application/x-ndjson" : "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(encoded)).build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        require(response.statusCode() >= 200 && response.statusCode() < 300, "Local " + port + " " + path + " HTTP " + response.statusCode()
                + ": " + response.body().substring(0, Math.min(500, response.body().length())));
        return mapper.readTree(response.body());
    }
}
