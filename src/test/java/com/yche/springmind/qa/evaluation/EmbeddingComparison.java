package com.yche.springmind.qa.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.*;
import java.util.*;
import static com.yche.springmind.qa.evaluation.RetrievalAblation.require;

/** Matched exact cosine retrieval, followed by optional offline fusion of fixed keyword hits. */
final class EmbeddingComparison {
    static final Path PROTOCOL = Path.of("evaluation/retrieval/multilingual-embedding/README.md");
    static final Path LOCK = PROTOCOL.resolveSibling("model-lock.json");
    static final String SCHEMA = "retrieval_eval_bgem3_20261010";
    static final List<String> MODELS = List.of("CURRENT", "BGE_M3");
    static final List<String> MODES = List.of("CURRENT_VECTOR", "BGE_M3_VECTOR", "CURRENT_HYBRID", "BGE_M3_HYBRID", "KEYWORD_REFERENCE");
    record Model(String tag, String digest, int dimensions, int contextLength, String precision) {}
    record Vectors(String mode, String startedAt, String completedAt, Model model, Map<String, float[]> documents,
                   Map<String, float[]> queries, Map<String, Double> embeddingMs, Map<String, Long> inputTokens,
                   JsonNode loadedRuntime, int localEmbeddingRequests) {}
    record CapturedRow(String id, Map<String, List<ExtensionEvaluation.Hit>> hits, Map<String, Double> sqlMs) {}
    record Capture(String completedAt, String schema, String databaseVersion, String pgvectorVersion,
                   String ollamaVersion, Map<String, String> inputHashes, Map<String, String> vectorHashes,
                   int cloudRequests, int localEmbeddingRequests, List<CapturedRow> rows) {}
    record Row(String id, String category, String language, Map<String, FilenameAblation.Score> scores,
               Map<String, List<String>> rankings, boolean historicalVectorOrderMatches,
               double historicalVectorTop50Overlap, RerankEvaluation.Metrics historicalVectorMetrics) {}
    record Comparison(String annotationStatus, String captureSha256, int cloudRequests, int localEmbeddingRequests,
                      int historicalVectorOrderMatches, double historicalVectorTop50Overlap,
                      Map<String, Map<String, FilenameAblation.Summary>> groups, List<Row> rows) {}

    private EmbeddingComparison() {}

    static Path output() {
        return Path.of(System.getProperty("eval.embeddingOutput", "evaluation/retrieval/results/harborview-bgem3-2026-10-10"));
    }

    static Map<String, Model> models() throws Exception {
        return RerankEvaluationRun.datasetMapper().readValue(LOCK.toFile(), new com.fasterxml.jackson.core.type.TypeReference<LinkedHashMap<String, Model>>() {});
    }

    static Map<String, String> inputHashes() throws Exception {
        Map<String, String> hashes = new LinkedHashMap<>();
        hashes.put("fixture", RetrievalAblation.hash(ExtensionFixture.PATH));
        for (String name : List.of("corpus", "review-approval", "capture"))
            hashes.put(name, RetrievalAblation.hash(FilenameAblation.SOURCE.resolve(name + ".json")));
        hashes.put("protocol", RetrievalAblation.hash(PROTOCOL));
        hashes.put("model-lock", RetrievalAblation.hash(LOCK));
        return hashes;
    }

    static void validateVector(float[] vector, int dimension) {
        require(vector != null && vector.length == dimension, "Embedding dimension mismatch");
        double norm = 0;
        for (float value : vector) { require(Float.isFinite(value), "Non-finite embedding"); norm += (double) value * value; }
        require(norm > 1e-12, "Zero embedding");
    }

    static double cosine(float[] left, float[] right) {
        require(left.length == right.length, "Cosine dimension mismatch");
        double dot = 0, a = 0, b = 0;
        for (int i = 0; i < left.length; i++) { dot += (double) left[i] * right[i]; a += (double) left[i] * left[i]; b += (double) right[i] * right[i]; }
        require(a > 0 && b > 0, "Zero cosine norm");
        return dot / Math.sqrt(a * b);
    }

    static List<ExtensionEvaluation.Hit> exact(Vectors vectors, String question, ExtensionEvaluation.Corpus corpus) {
        return vectors.documents().entrySet().stream()
                .map(entry -> new ExtensionEvaluation.Hit(entry.getKey(), cosine(entry.getValue(), vectors.queries().get(question))))
                .sorted(Comparator.comparingDouble(ExtensionEvaluation.Hit::score).reversed()
                        .thenComparingLong(hit -> corpus.chunks().get(hit.chunk()).chunkId())).toList();
    }

    static void verifySql(List<ExtensionEvaluation.Hit> sql, List<ExtensionEvaluation.Hit> exact) {
        require(sql.size() == 50 && sql.stream().map(ExtensionEvaluation.Hit::chunk).distinct().count() == 50, "Incomplete/duplicate SQL ranking");
        Map<String, Double> scores = new HashMap<>();
        exact.forEach(hit -> scores.put(hit.chunk(), hit.score()));
        for (int i = 0; i < 50; i++) {
            var hit = sql.get(i);
            require(scores.containsKey(hit.chunk()) && Double.isFinite(hit.score())
                    && Math.abs(hit.score() - scores.get(hit.chunk())) <= 1e-6
                    && Math.abs(scores.get(hit.chunk()) - exact.get(i).score()) <= 1e-6, "SQL/exhaustive cosine mismatch");
        }
    }

    static void validateVectors(Vectors vectors, Model model, ExtensionFixture fixture, ExtensionEvaluation.Corpus corpus) {
        require(vectors.model().equals(model) && vectors.documents().keySet().equals(corpus.chunks().keySet())
                && vectors.queries().keySet().equals(new LinkedHashSet<>(fixture.cases().stream().map(ExtensionFixture.Case::id).toList())), "Vector provenance/key mismatch");
        require(vectors.localEmbeddingRequests() == corpus.chunks().size() + fixture.cases().size(), "Incomplete embedding requests");
        vectors.documents().values().forEach(value -> validateVector(value, model.dimensions()));
        vectors.queries().values().forEach(value -> validateVector(value, model.dimensions()));
        Set<String> expected = new HashSet<>();
        corpus.chunks().keySet().forEach(key -> expected.add("doc:" + key));
        fixture.cases().forEach(question -> expected.add("query:" + question.id()));
        require(vectors.embeddingMs().keySet().equals(expected) && vectors.inputTokens().keySet().equals(expected)
                && vectors.embeddingMs().values().stream().allMatch(value -> Double.isFinite(value) && value >= 0)
                && vectors.inputTokens().values().stream().allMatch(value -> value > 0), "Invalid embedding diagnostics");
    }

    static Comparison compare(Path root) throws Exception {
        FilenameAblation.verifyApproval();
        var mapper = RerankEvaluationRun.datasetMapper();
        var fixture = ExtensionFixture.read();
        var corpus = FilenameAblation.corpus();
        var original = FilenameAblation.original();
        var capture = mapper.readValue(root.resolve("capture.json").toFile(), Capture.class);
        require(capture.inputHashes().equals(inputHashes()) && capture.schema().equals(SCHEMA)
                && capture.cloudRequests() == 0 && capture.localEmbeddingRequests() == 258
                && capture.rows().size() == fixture.cases().size(), "Capture protocol/hash mismatch");
        var models = models();
        Map<String, Vectors> vectors = new LinkedHashMap<>();
        require(capture.vectorHashes().keySet().equals(new HashSet<>(MODELS)), "Missing vector file hash");
        for (String mode : MODELS) {
            Path path = root.resolve(mode.toLowerCase(Locale.ROOT) + "-vectors.json");
            require(RetrievalAblation.hash(path).equals(capture.vectorHashes().get(mode)), "Vector file hash mismatch");
            var value = mapper.readValue(path.toFile(), Vectors.class);
            require(mode.equals(value.mode()), "Wrong model arm");
            validateVectors(value, models.get(mode), fixture, corpus);
            vectors.put(mode, value);
        }
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < fixture.cases().size(); i++) {
            var question = fixture.cases().get(i);
            var saved = capture.rows().get(i);
            require(question.id().equals(saved.id()) && saved.hits().keySet().equals(new HashSet<>(MODELS)), "Missing query/model");
            Map<String, List<String>> rankings = new LinkedHashMap<>();
            var keyword = original.rows().get(i).keyword();
            for (String mode : MODELS) {
                verifySql(saved.hits().get(mode), exact(vectors.get(mode), question.id(), corpus));
                rankings.put(mode + "_VECTOR", saved.hits().get(mode).stream().map(ExtensionEvaluation.Hit::chunk).toList());
                rankings.put(mode + "_HYBRID", ExtensionEvaluation.orders(saved.hits().get(mode), keyword, corpus.chunks()).get("HYBRID"));
            }
            rankings.put("KEYWORD_REFERENCE", keyword.stream().map(ExtensionEvaluation.Hit::chunk).toList());
            Map<String, FilenameAblation.Score> scores = new LinkedHashMap<>();
            for (String mode : MODES) {
                var ranking = rankings.get(mode);
                var labels = corpus.grades().get(question.id());
                var metrics = RerankEvaluation.metrics(ranking, labels, 5);
                var p30 = RerankEvaluation.metrics(ranking, labels, 30);
                var p50 = mode.endsWith("HYBRID") ? null : RerankEvaluation.metrics(ranking, labels, 50);
                Double facts = ExtensionEvaluation.chunkCoverage(question, ranking.stream().limit(5).toList(), corpus.chunks());
                scores.put(mode, new FilenameAblation.Score(metrics, p30 == null ? null : p30.recall(), p50 == null ? null : p50.recall(),
                        facts, question.evidence().size() > 1 ? facts == 1.0 : null));
            }
            var historical = original.rows().get(i).vector().stream().map(ExtensionEvaluation.Hit::chunk).toList();
            var current = rankings.get("CURRENT_VECTOR");
            rows.add(new Row(question.id(), question.category(), question.language(), scores, rankings, historical.equals(current),
                    current.stream().filter(historical::contains).count() / 50.0, RerankEvaluation.metrics(historical, corpus.grades().get(question.id()), 5)));
        }
        Map<String, Map<String, FilenameAblation.Summary>> groups = new LinkedHashMap<>();
        groups.put("ALL", summarize(rows));
        for (String language : List.of("zh", "en")) groups.put(language, summarize(rows.stream().filter(row -> row.language().equals(language)).toList()));
        for (String category : List.of("exact", "semantic", "cross-language", "multi-evidence", "long-document", "unanswerable"))
            groups.put(category, summarize(rows.stream().filter(row -> row.category().equals(category)).toList()));
        return new Comparison("HUMAN_REVIEWED", RetrievalAblation.hash(root.resolve("capture.json")), 0, capture.localEmbeddingRequests(),
                (int) rows.stream().filter(Row::historicalVectorOrderMatches).count(), rows.stream().mapToDouble(Row::historicalVectorTop50Overlap).average().orElseThrow(), groups, rows);
    }

    private static Map<String, FilenameAblation.Summary> summarize(List<Row> rows) {
        Map<String, FilenameAblation.Summary> result = new LinkedHashMap<>();
        for (String mode : MODES) {
            var scores = rows.stream().map(row -> row.scores().get(mode)).toList();
            var metrics = scores.stream().map(FilenameAblation.Score::metrics).filter(Objects::nonNull).toList();
            var multi = scores.stream().map(FilenameAblation.Score::allFacts5).filter(Objects::nonNull).toList();
            result.put(mode, new FilenameAblation.Summary(rows.size(), metrics.size(), metrics.isEmpty() ? null : new RerankEvaluation.Metrics(
                    metrics.stream().mapToDouble(RerankEvaluation.Metrics::hit).average().orElseThrow(),
                    metrics.stream().mapToDouble(RerankEvaluation.Metrics::recall).average().orElseThrow(),
                    metrics.stream().mapToDouble(RerankEvaluation.Metrics::mrr).average().orElseThrow(),
                    metrics.stream().mapToDouble(RerankEvaluation.Metrics::ndcg).average().orElseThrow()),
                    mean(scores.stream().map(FilenameAblation.Score::recall30).toList()), mean(scores.stream().map(FilenameAblation.Score::recall50).toList()),
                    mean(scores.stream().map(FilenameAblation.Score::factCoverage5).toList()), multi.size(), (int) multi.stream().filter(Boolean::booleanValue).count()));
        }
        return result;
    }

    private static Double mean(List<Double> values) {
        return values.stream().filter(Objects::nonNull).mapToDouble(Double::doubleValue).average().stream().boxed().findFirst().orElse(null);
    }
}
