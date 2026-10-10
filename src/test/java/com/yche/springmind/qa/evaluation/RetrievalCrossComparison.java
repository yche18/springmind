package com.yche.springmind.qa.evaluation;

import java.nio.file.*;
import java.util.*;
import static com.yche.springmind.qa.evaluation.RetrievalAblation.require;

/** Offline factorial replay: two vector models crossed with two frozen keyword inputs. */
final class RetrievalCrossComparison {
    static final Path EMBEDDING = Path.of("evaluation/retrieval/results/harborview-bgem3-2026-10-10");
    static final Path KEYWORD = Path.of("evaluation/retrieval/results/harborview-filename-2026-10-10");
    static final Path OUTPUT = Path.of("evaluation/retrieval/results/harborview-cross-2026-10-11");
    static final List<String> MODES = List.of("KEYWORD_BODY", "KEYWORD_FILENAME", "CURRENT_VECTOR", "CURRENT_BODY_RRF",
            "CURRENT_FILENAME_RRF", "BGE_M3_VECTOR", "BGE_M3_BODY_RRF", "BGE_M3_FILENAME_RRF");
    record Contrast(String id, String before, String after) {}
    static final List<Contrast> CONTRASTS = List.of(
            new Contrast("filename_keyword", "KEYWORD_BODY", "KEYWORD_FILENAME"),
            new Contrast("rrf_current_body", "CURRENT_VECTOR", "CURRENT_BODY_RRF"),
            new Contrast("rrf_current_filename", "CURRENT_VECTOR", "CURRENT_FILENAME_RRF"),
            new Contrast("filename_current_rrf", "CURRENT_BODY_RRF", "CURRENT_FILENAME_RRF"),
            new Contrast("rrf_m3_body", "BGE_M3_VECTOR", "BGE_M3_BODY_RRF"),
            new Contrast("rrf_m3_filename", "BGE_M3_VECTOR", "BGE_M3_FILENAME_RRF"),
            new Contrast("filename_m3_rrf", "BGE_M3_BODY_RRF", "BGE_M3_FILENAME_RRF"));
    record Row(String id, String category, String language, Map<String, List<String>> rankings,
               Map<String, FilenameAblation.Score> scores) {}
    record Effect(String before, String after, RerankEvaluation.Metrics delta,
                  List<String> improved, List<String> regressed, List<String> unchanged) {}
    record Comparison(String annotationStatus, int newModelRequests, int newRetrievalRequests,
                      Map<String, String> inputHashes, Map<String, Map<String, FilenameAblation.Summary>> groups,
                      Map<String, Map<String, Effect>> effects, List<Row> rows) {}

    private RetrievalCrossComparison() {}

    static Comparison compare() throws Exception {
        var mapper = RerankEvaluationRun.datasetMapper();
        var embedding = EmbeddingComparison.compare(EMBEDDING);
        var vectorCapture = mapper.readValue(EMBEDDING.resolve("capture.json").toFile(), EmbeddingComparison.Capture.class);
        var keywordCapture = mapper.readValue(KEYWORD.resolve("capture.json").toFile(), FilenameAblation.Capture.class);
        var keyword = FilenameAblation.compare(keywordCapture, RetrievalAblation.hash(KEYWORD.resolve("capture.json")));
        require(keyword.originalOrderMatches() == 24 && keyword.originalScoreMatches() == 24,
                "Filename-enabled keyword baseline drift prevents matched replay");
        require(mapper.readTree(EMBEDDING.resolve("comparison.json").toFile()).equals(mapper.valueToTree(embedding))
                && mapper.readTree(KEYWORD.resolve("comparison.json").toFile()).equals(mapper.valueToTree(keyword)), "Source report drift");
        var fixture = ExtensionFixture.read();
        var corpus = FilenameAblation.corpus();
        Map<String, String> hashes = new LinkedHashMap<>(vectorCapture.inputHashes());
        hashes.put("embedding-capture", RetrievalAblation.hash(EMBEDDING.resolve("capture.json")));
        hashes.put("filename-capture", RetrievalAblation.hash(KEYWORD.resolve("capture.json")));
        vectorCapture.vectorHashes().forEach((key, value) -> hashes.put("vectors-" + key, value));
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < fixture.cases().size(); i++) {
            var question = fixture.cases().get(i);
            var v = vectorCapture.rows().get(i);
            var k = keywordCapture.rows().get(i);
            require(question.id().equals(v.id()) && question.id().equals(k.id()), "Query identity mismatch");
            Map<String, List<String>> ranks = new LinkedHashMap<>();
            ranks.put("KEYWORD_BODY", ids(k.hits().get("BODY_ONLY")));
            ranks.put("KEYWORD_FILENAME", ids(k.hits().get("BODY_AND_FILENAME")));
            for (String model : EmbeddingComparison.MODELS) {
                ranks.put(model + "_VECTOR", ids(v.hits().get(model)));
                ranks.put(model + "_BODY_RRF", fuse(v.hits().get(model), k.hits().get("BODY_ONLY"), corpus));
                ranks.put(model + "_FILENAME_RRF", fuse(v.hits().get(model), k.hits().get("BODY_AND_FILENAME"), corpus));
                require(ranks.get(model + "_FILENAME_RRF").equals(embedding.rows().get(i).rankings().get(model + "_HYBRID")),
                        "Previously measured fusion did not reproduce");
            }
            Map<String, FilenameAblation.Score> scores = new LinkedHashMap<>();
            for (String mode : MODES) {
                var rank = ranks.get(mode);
                var labels = corpus.grades().get(question.id());
                var metrics = RerankEvaluation.metrics(rank, labels, 5);
                var p30 = RerankEvaluation.metrics(rank, labels, 30);
                var p50 = mode.endsWith("RRF") ? null : RerankEvaluation.metrics(rank, labels, 50);
                Double coverage = ExtensionEvaluation.chunkCoverage(question, rank.stream().limit(5).toList(), corpus.chunks());
                scores.put(mode, new FilenameAblation.Score(metrics, p30 == null ? null : p30.recall(), p50 == null ? null : p50.recall(),
                        coverage, question.evidence().size() > 1 ? coverage == 1 : null));
            }
            rows.add(new Row(question.id(), question.category(), question.language(), ranks, scores));
        }
        Map<String, List<Row>> slices = new LinkedHashMap<>();
        slices.put("ALL", rows);
        for (String language : List.of("zh", "en")) slices.put(language, rows.stream().filter(row -> row.language().equals(language)).toList());
        for (String category : List.of("exact", "semantic", "cross-language", "multi-evidence", "long-document", "unanswerable"))
            slices.put(category, rows.stream().filter(row -> row.category().equals(category)).toList());
        Map<String, Map<String, FilenameAblation.Summary>> groups = new LinkedHashMap<>();
        Map<String, Map<String, Effect>> effects = new LinkedHashMap<>();
        for (var slice : slices.entrySet()) {
            var summaries = summarize(slice.getValue());
            groups.put(slice.getKey(), summaries);
            effects.put(slice.getKey(), effects(slice.getValue(), summaries));
        }
        return new Comparison("HUMAN_REVIEWED", 0, 0, hashes, groups, effects, rows);
    }

    static List<String> fuse(List<ExtensionEvaluation.Hit> vector, List<ExtensionEvaluation.Hit> keyword, ExtensionEvaluation.Corpus corpus) {
        return ExtensionEvaluation.orders(vector, keyword, corpus.chunks()).get("HYBRID");
    }
    private static List<String> ids(List<ExtensionEvaluation.Hit> hits) { return hits.stream().map(ExtensionEvaluation.Hit::chunk).toList(); }

    private static Map<String, Effect> effects(List<Row> rows, Map<String, FilenameAblation.Summary> summaries) {
        Map<String, Effect> effects = new LinkedHashMap<>();
        for (var contrast : CONTRASTS) {
            var before = summaries.get(contrast.before()).metrics();
            var after = summaries.get(contrast.after()).metrics();
            List<String> improved = new ArrayList<>(), regressed = new ArrayList<>(), unchanged = new ArrayList<>();
            for (var row : rows) {
                var a = row.scores().get(contrast.before()).metrics();
                var b = row.scores().get(contrast.after()).metrics();
                if (a == null) continue;
                double delta = b.ndcg() - a.ndcg();
                (delta > 1e-12 ? improved : delta < -1e-12 ? regressed : unchanged).add(row.id());
            }
            effects.put(contrast.id(), new Effect(contrast.before(), contrast.after(), before == null ? null : new RerankEvaluation.Metrics(
                    after.hit() - before.hit(), after.recall() - before.recall(), after.mrr() - before.mrr(), after.ndcg() - before.ndcg()), improved, regressed, unchanged));
        }
        return effects;
    }

    private static Map<String, FilenameAblation.Summary> summarize(List<Row> rows) {
        Map<String, FilenameAblation.Summary> result = new LinkedHashMap<>();
        for (String mode : MODES) {
            var values = rows.stream().map(row -> row.scores().get(mode)).toList();
            var metrics = values.stream().map(FilenameAblation.Score::metrics).filter(Objects::nonNull).toList();
            var multi = values.stream().map(FilenameAblation.Score::allFacts5).filter(Objects::nonNull).toList();
            result.put(mode, new FilenameAblation.Summary(rows.size(), metrics.size(), metrics.isEmpty() ? null : new RerankEvaluation.Metrics(
                    metrics.stream().mapToDouble(RerankEvaluation.Metrics::hit).average().orElseThrow(),
                    metrics.stream().mapToDouble(RerankEvaluation.Metrics::recall).average().orElseThrow(),
                    metrics.stream().mapToDouble(RerankEvaluation.Metrics::mrr).average().orElseThrow(),
                    metrics.stream().mapToDouble(RerankEvaluation.Metrics::ndcg).average().orElseThrow()),
                    mean(values.stream().map(FilenameAblation.Score::recall30).toList()), mean(values.stream().map(FilenameAblation.Score::recall50).toList()),
                    mean(values.stream().map(FilenameAblation.Score::factCoverage5).toList()), multi.size(), (int) multi.stream().filter(Boolean::booleanValue).count()));
        }
        return result;
    }
    private static Double mean(List<Double> values) {
        return values.stream().filter(Objects::nonNull).mapToDouble(Double::doubleValue).average().stream().boxed().findFirst().orElse(null);
    }
}
