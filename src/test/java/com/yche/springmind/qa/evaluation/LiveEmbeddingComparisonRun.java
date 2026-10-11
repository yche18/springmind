package com.yche.springmind.qa.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.yaml.snakeyaml.Yaml;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import static com.yche.springmind.qa.evaluation.RetrievalAblation.require;

/** Only local Ollama and isolated JDBC tables; no application context, migrations or cloud clients. */
@EnabledIfSystemProperty(named = "eval.embedding", matches = "true")
class LiveEmbeddingComparisonRun {
    private final com.fasterxml.jackson.databind.ObjectMapper mapper = RerankEvaluationRun.datasetMapper();

    @Test
    void embedAndMeasure() throws Exception {
        require(Boolean.getBoolean("eval.confirmLocalWrites"), "Explicit evaluation-table write confirmation required");
        FilenameAblation.verifyApproval();
        Path output = EmbeddingComparison.output();
        require(!Files.exists(output), "Use a new output directory; preserve previous attempts");
        var hashes = EmbeddingComparison.inputHashes();
        var fixture = ExtensionFixture.read();
        var corpus = FilenameAblation.corpus();
        var models = EmbeddingComparison.models();
        Files.createDirectories(output);
        Map<String, EmbeddingComparison.Vectors> vectors = new LinkedHashMap<>();
        String ollamaVersion;
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
             var database = connect()) {
            require(!exists(database, "select 1 from information_schema.schemata where schema_name = '" + EmbeddingComparison.SCHEMA + "'"),
                    "Evaluation schema already exists; never overwrite it");
            String databaseVersion = scalar(database, "select version()");
            String pgvectorVersion = scalar(database, "select extversion from pg_extension where extname='vector'");
            require(!pgvectorVersion.isBlank(), "pgvector is not installed");
            var tags = call(client, "GET", "/api/tags", null);
            ollamaVersion = call(client, "GET", "/api/version", null).path("version").asText();
            for (String mode : EmbeddingComparison.MODELS) {
                var model = models.get(mode);
                verifyModel(client, tags, model);
                var values = encode(client, mode, model, fixture, corpus);
                EmbeddingComparison.validateVectors(values, model, fixture, corpus);
                write(output.resolve(mode.toLowerCase(Locale.ROOT) + "-vectors.json"), values);
                vectors.put(mode, values);
            }
            require(hashes.equals(EmbeddingComparison.inputHashes()), "Frozen inputs changed during encoding");
            database.setAutoCommit(false);
            try (var statement = database.createStatement()) {
                statement.execute("create schema " + EmbeddingComparison.SCHEMA);
                for (String mode : EmbeddingComparison.MODELS) {
                    String table = table(mode);
                    statement.execute("create table " + table + " (chunk_id bigint primary key, source_key text unique not null, embedding vector("
                            + models.get(mode).dimensions() + ") not null)");
                    try (var insert = database.prepareStatement("insert into " + table + " (chunk_id, source_key, embedding) values (?, ?, ?::vector)")) {
                        for (var entry : corpus.chunks().entrySet()) {
                            insert.setLong(1, entry.getValue().chunkId());
                            insert.setString(2, entry.getKey());
                            insert.setString(3, vectorText(vectors.get(mode).documents().get(entry.getKey())));
                            insert.addBatch();
                        }
                        insert.executeBatch();
                    }
                    statement.execute("analyze " + table);
                    require(Integer.parseInt(scalar(database, "select count(*) from " + table)) == 105, "Incomplete isolated vector table");
                }
                database.commit();
            } catch (Exception error) {
                database.rollback();
                throw error;
            }
            // Neither table has a vector index; SQL and Java both score the full corpus.
            List<EmbeddingComparison.CapturedRow> rows = new ArrayList<>();
            for (var question : fixture.cases()) {
                Map<String, List<ExtensionEvaluation.Hit>> hits = new LinkedHashMap<>();
                Map<String, Double> timings = new LinkedHashMap<>();
                for (String mode : EmbeddingComparison.MODELS) {
                    long start = System.nanoTime();
                    List<ExtensionEvaluation.Hit> ranked = new ArrayList<>();
                    try (var query = database.prepareStatement("select source_key, 1 - (embedding <=> ?::vector) as score from "
                            + table(mode) + " order by embedding <=> ?::vector, chunk_id limit 50")) {
                        String vector = vectorText(vectors.get(mode).queries().get(question.id()));
                        query.setString(1, vector);
                        query.setString(2, vector);
                        try (var result = query.executeQuery()) {
                            while (result.next()) ranked.add(new ExtensionEvaluation.Hit(result.getString(1), result.getDouble(2)));
                        }
                    }
                    timings.put(mode, elapsed(start));
                    EmbeddingComparison.verifySql(ranked, EmbeddingComparison.exact(vectors.get(mode), question.id(), corpus));
                    hits.put(mode, ranked);
                }
                rows.add(new EmbeddingComparison.CapturedRow(question.id(), hits, timings));
                System.out.println("Exact vector retrieval verified: " + question.id());
            }
            database.commit();
            Map<String, String> vectorHashes = new LinkedHashMap<>();
            for (String mode : EmbeddingComparison.MODELS)
                vectorHashes.put(mode, RetrievalAblation.hash(output.resolve(mode.toLowerCase(Locale.ROOT) + "-vectors.json")));
            write(output.resolve("capture.json"), new EmbeddingComparison.Capture(Instant.now().toString(), EmbeddingComparison.SCHEMA,
                    databaseVersion, pgvectorVersion, ollamaVersion, hashes, vectorHashes, 0,
                    vectors.values().stream().mapToInt(EmbeddingComparison.Vectors::localEmbeddingRequests).sum(), rows));
        }
        EmbeddingComparison.compare(output);
        System.out.println("Local embedding comparison complete: 258 embedding requests, 48 exact SQL searches, zero cloud requests");
    }

    private EmbeddingComparison.Vectors encode(HttpClient client, String mode, EmbeddingComparison.Model model,
                                               ExtensionFixture fixture, ExtensionEvaluation.Corpus corpus) throws Exception {
        String started = Instant.now().toString();
        Map<String, float[]> documents = new LinkedHashMap<>(), queries = new LinkedHashMap<>();
        Map<String, Double> timings = new LinkedHashMap<>();
        Map<String, Long> tokens = new LinkedHashMap<>();
        Map<String, String> inputs = new LinkedHashMap<>();
        corpus.chunks().forEach((key, chunk) -> inputs.put("doc:" + key, chunk.text()));
        fixture.cases().forEach(question -> inputs.put("query:" + question.id(), question.query()));
        int count = 0;
        JsonNode runtime;
        try {
            for (var input : inputs.entrySet()) {
                long start = System.nanoTime();
                var response = call(client, "POST", "/api/embed", Map.of("model", model.tag(), "input", input.getValue(),
                        "truncate", false, "keep_alive", "5m", "options", Map.of("num_ctx", model.contextLength())));
                timings.put(input.getKey(), elapsed(start));
                require(response.path("embeddings").size() == 1, "Missing local embedding");
                float[] embedding = mapper.convertValue(response.path("embeddings").get(0), float[].class);
                EmbeddingComparison.validateVector(embedding, model.dimensions());
                tokens.put(input.getKey(), response.path("prompt_eval_count").asLong());
                if (input.getKey().startsWith("doc:")) documents.put(input.getKey().substring(4), embedding);
                else queries.put(input.getKey().substring(6), embedding);
                count++;
                if (count == 1 || count % 10 == 0 || count == inputs.size()) System.out.println(mode + " encoded " + count + "/" + inputs.size());
            }
            runtime = call(client, "GET", "/api/ps", null);
        } finally {
            call(client, "POST", "/api/embed", Map.of("model", model.tag(), "input", "", "keep_alive", 0));
        }
        return new EmbeddingComparison.Vectors(mode, started, Instant.now().toString(), model, documents, queries, timings, tokens, runtime, count);
    }

    private void verifyModel(HttpClient client, JsonNode tags, EmbeddingComparison.Model model) throws Exception {
        boolean matched = false;
        for (var installed : tags.path("models")) if (installed.path("name").asText().equals(model.tag()))
            matched = installed.path("digest").asText().equals(model.digest());
        require(matched, "Required pinned model is not installed");
        var show = call(client, "POST", "/api/show", Map.of("model", model.tag()));
        require(show.path("details").path("quantization_level").asText().equals(model.precision()), "Model precision changed");
        String architecture = show.path("model_info").path("general.architecture").asText();
        require(show.path("model_info").path(architecture + ".embedding_length").asInt() == model.dimensions(), "Model dimensions changed");
    }

    private Connection connect() throws Exception {
        Map<?, ?> config;
        try (var reader = Files.newBufferedReader(Path.of("src/main/resources/application-dev.yml"))) { config = new Yaml().load(reader); }
        var datasource = (Map<?, ?>) ((Map<?, ?>) config.get("spring")).get("datasource");
        String url = datasource.get("url").toString();
        require(url.startsWith("jdbc:postgresql://localhost:") || url.startsWith("jdbc:postgresql://127.0.0.1:"), "Only local evaluation JDBC is allowed");
        return DriverManager.getConnection(url, datasource.get("username").toString(), datasource.get("password").toString());
    }

    private JsonNode call(HttpClient client, String method, String path, Object body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:11434" + path)).timeout(Duration.ofMinutes(3))
                .header("Content-Type", "application/json").method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        require(response.statusCode() >= 200 && response.statusCode() < 300,
                "Local Ollama " + path + " failed with HTTP " + response.statusCode());
        return mapper.readTree(response.body());
    }

    private static String table(String mode) {
        require(EmbeddingComparison.MODELS.contains(mode), "Unknown table mode");
        return EmbeddingComparison.SCHEMA + "." + mode.toLowerCase(Locale.ROOT) + "_vectors";
    }
    private String vectorText(float[] vector) throws Exception { return mapper.writeValueAsString(vector); }
    private void write(Path path, Object value) throws Exception { mapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), value); }
    private static double elapsed(long start) { return (System.nanoTime() - start) / 1_000_000.0; }
    private static boolean exists(Connection connection, String sql) throws Exception {
        try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) { return result.next(); }
    }
    private static String scalar(Connection connection, String sql) throws Exception {
        try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
            require(result.next(), "Missing database diagnostic"); return result.getString(1);
        }
    }
}
