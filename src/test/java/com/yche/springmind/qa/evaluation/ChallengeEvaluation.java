package com.yche.springmind.qa.evaluation;

import java.nio.file.*;
import java.util.*;
import static com.yche.springmind.qa.evaluation.RetrievalAblation.require;

/** Exact offline replay with unordered candidate unions and separately scored RRF. */
final class ChallengeEvaluation {
    record Capture(String completedAt, String freezeSha256, String index, String ollamaVersion, String elasticVersion,
                   Map<String, String> artifactHashes, int localEmbeddingRequests, int keywordRequests, int cloudRequests) {}
    record Union(List<String> candidates, int size, Double recall, Double factCoverage,
                 List<String> vectorExclusiveRelevant, List<String> keywordExclusiveRelevant,
                 List<String> lostAtRrf30, List<String> lostAtRrf5) {}
    record Row(String id, String category, String language, Map<String, List<String>> rankings,
               Map<String, FilenameAblation.Score> scores, Map<String, Union> unions) {}
    record UnionSummary(int cases, int answerable, double meanSize, Double recall, Double factCoverage,
                        int vectorExclusiveRelevant, int keywordExclusiveRelevant, int lostAtRrf30, int lostAtRrf5) {}
    record Comparison(String annotationStatus, Capture capture, Map<String, Map<String, FilenameAblation.Summary>> groups,
                      Map<String, Map<String, UnionSummary>> unionGroups, List<Row> rows) {}
    static final List<String> MODES = List.of("KEYWORD_BODY", "KEYWORD_FILENAME", "CURRENT_VECTOR", "CURRENT_BODY_RRF",
            "CURRENT_FILENAME_RRF", "BGE_M3_VECTOR", "BGE_M3_BODY_RRF", "BGE_M3_FILENAME_RRF");
    private ChallengeEvaluation() {}

    static void validateVectors(EmbeddingComparison.Vectors vectors, String mode) throws Exception {
        var model = EmbeddingComparison.models().get(mode);
        var corpus = ChallengeData.corpus();
        var fixture = ChallengeData.fixture();
        require(vectors.mode().equals(mode) && vectors.model().equals(model)
                && vectors.documents().keySet().equals(corpus.chunks().keySet())
                && vectors.queries().keySet().equals(new HashSet<>(fixture.cases().stream().map(ExtensionFixture.Case::id).toList()))
                && vectors.localEmbeddingRequests() == corpus.chunks().size() + fixture.cases().size(), "Incomplete/wrong local vectors");
        vectors.documents().values().forEach(v -> EmbeddingComparison.validateVector(v, model.dimensions()));
        vectors.queries().values().forEach(v -> EmbeddingComparison.validateVector(v, model.dimensions()));
        require(vectors.embeddingMs().size() == vectors.localEmbeddingRequests() && vectors.inputTokens().size() == vectors.localEmbeddingRequests()
                && vectors.embeddingMs().values().stream().allMatch(v -> Double.isFinite(v) && v >= 0)
                && vectors.inputTokens().values().stream().allMatch(v -> v > 0), "Incomplete embedding diagnostics");
    }
    static List<ExtensionEvaluation.Hit> exact(EmbeddingComparison.Vectors v, String query, ChallengeData.Corpus corpus) {
        return v.documents().entrySet().stream().map(e -> new ExtensionEvaluation.Hit(e.getKey(), EmbeddingComparison.cosine(e.getValue(), v.queries().get(query))))
                .sorted(Comparator.comparingDouble(ExtensionEvaluation.Hit::score).reversed().thenComparingLong(h -> corpus.chunks().get(h.chunk()).chunkId()))
                .limit(50).toList();
    }
    static Comparison compare() throws Exception {
        ChallengeData.verifyFreeze();
        var mapper = RerankEvaluationRun.datasetMapper();
        var root = ChallengeData.OUTPUT;
        var capture = mapper.readValue(root.resolve("capture.json").toFile(), Capture.class);
        require(capture.freezeSha256().equals(RetrievalAblation.hash(ChallengeData.ROOT.resolve("freeze.json"))) && capture.index().equals(ChallengeData.INDEX)
                && capture.cloudRequests() == 0 && capture.localEmbeddingRequests() == 282 && capture.keywordRequests() == 72, "Capture provenance/count mismatch");
        require(capture.artifactHashes().keySet().equals(Set.of("current-vectors.json", "bge_m3-vectors.json", "keyword.json", "index-snapshot.json", "index-metadata.json", "run.json")), "Missing captured artifacts");
        for (var entry : capture.artifactHashes().entrySet()) require(RetrievalAblation.hash(root.resolve(entry.getKey())).equals(entry.getValue()), "Artifact changed: " + entry.getKey());
        Map<String, EmbeddingComparison.Vectors> vectors = new LinkedHashMap<>();
        for (String mode : EmbeddingComparison.MODELS) {
            var value = mapper.readValue(root.resolve(mode.toLowerCase(Locale.ROOT) + "-vectors.json").toFile(), EmbeddingComparison.Vectors.class);
            validateVectors(value, mode);
            vectors.put(mode, value);
        }
        var keyword = mapper.readValue(root.resolve("keyword.json").toFile(), new com.fasterxml.jackson.core.type.TypeReference<List<FilenameAblation.CapturedRow>>() {});
        var fixture = ChallengeData.fixture();
        var corpus = ChallengeData.corpus();
        require(keyword.size() == fixture.cases().size(), "Missing lexical queries");
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < fixture.cases().size(); i++) {
            var q = fixture.cases().get(i);
            var k = keyword.get(i);
            require(q.id().equals(k.id()) && k.hits().keySet().equals(Set.copyOf(FilenameAblation.MODES)), "Wrong lexical query arms");
            for (String mode : FilenameAblation.MODES) {
                require(mapper.readTree(FilenameAblation.request(ChallengeData.GROUP, q.query(), mode.equals("BODY_AND_FILENAME")).toString()).equals(k.requests().get(mode)), "Lexical request changed");
                require(k.hits().get(mode).size() <= 50 && k.hits().get(mode).stream().map(ExtensionEvaluation.Hit::chunk).distinct().count() == k.hits().get(mode).size()
                        && k.hits().get(mode).stream().allMatch(h -> corpus.chunks().containsKey(h.chunk()) && Double.isFinite(h.score())), "Invalid lexical hits");
            }
            Map<String, List<String>> ranks = new LinkedHashMap<>();
            ranks.put("KEYWORD_BODY", ids(k.hits().get("BODY_ONLY")));
            ranks.put("KEYWORD_FILENAME", ids(k.hits().get("BODY_AND_FILENAME")));
            for (String mode : EmbeddingComparison.MODELS) {
                var v = exact(vectors.get(mode), q.id(), corpus);
                ranks.put(mode + "_VECTOR", ids(v));
                for (String field : List.of("BODY", "FILENAME"))
                    ranks.put(mode + "_" + field + "_RRF", ExtensionEvaluation.orders(v,
                            k.hits().get(field.equals("BODY") ? "BODY_ONLY" : "BODY_AND_FILENAME"), corpus.chunks()).get("HYBRID"));
            }
            Map<String, FilenameAblation.Score> scores = new LinkedHashMap<>();
            for (String mode : MODES) {
                var rank = ranks.get(mode);
                var labels = corpus.grades().get(q.id());
                var m = RerankEvaluation.metrics(rank, labels, 5);
                var m30 = RerankEvaluation.metrics(rank, labels, 30);
                var m50 = mode.endsWith("RRF") ? null : RerankEvaluation.metrics(rank, labels, 50);
                Double facts = q.evidence().isEmpty() ? null : ChallengeData.coverage(q, rank.stream().limit(5).toList(), corpus);
                scores.put(mode, new FilenameAblation.Score(m, m30 == null ? null : m30.recall(), m50 == null ? null : m50.recall(), facts,
                        q.evidence().stream().map(ExtensionFixture.Evidence::id).distinct().count() > 1 ? facts == 1 : null));
            }
            Map<String, Union> unions = new LinkedHashMap<>();
            for (String mode : EmbeddingComparison.MODELS) for (String field : List.of("BODY", "FILENAME")) for (int budget : List.of(25, 50)) {
                String key = mode + "_" + field;
                unions.put(key + "_U" + budget, union(q, ranks.get(mode + "_VECTOR"), ranks.get("KEYWORD_" + field), ranks.get(key + "_RRF"), budget, corpus));
            }
            rows.add(new Row(q.id(), q.category(), q.language(), ranks, scores, unions));
        }
        Map<String, List<Row>> slices = new LinkedHashMap<>();
        slices.put("ALL", rows);
        for (String language : List.of("zh", "en")) slices.put(language, rows.stream().filter(r -> r.language().equals(language)).toList());
        for (String category : ChallengeData.CATEGORIES) slices.put(category, rows.stream().filter(r -> r.category().equals(category)).toList());
        Map<String, Map<String, FilenameAblation.Summary>> groups = new LinkedHashMap<>();
        Map<String, Map<String, UnionSummary>> unions = new LinkedHashMap<>();
        for (var slice : slices.entrySet()) {
            Map<String, FilenameAblation.Summary> summaries = new LinkedHashMap<>();
            for (String mode : MODES) summaries.put(mode, summarize(slice.getValue().stream().map(r -> r.scores().get(mode)).toList()));
            groups.put(slice.getKey(), summaries);
            Map<String, UnionSummary> summary = new LinkedHashMap<>();
            for (String mode : rows.getFirst().unions().keySet()) {
                var values = slice.getValue().stream().map(r -> r.unions().get(mode)).toList();
                summary.put(mode, new UnionSummary(values.size(), (int) values.stream().filter(v -> v.recall() != null).count(),
                        values.stream().mapToInt(Union::size).average().orElseThrow(), mean(values.stream().map(Union::recall).toList()),
                        mean(values.stream().map(Union::factCoverage).toList()), values.stream().mapToInt(v -> v.vectorExclusiveRelevant().size()).sum(),
                        values.stream().mapToInt(v -> v.keywordExclusiveRelevant().size()).sum(), values.stream().mapToInt(v -> v.lostAtRrf30().size()).sum(),
                        values.stream().mapToInt(v -> v.lostAtRrf5().size()).sum()));
            }
            unions.put(slice.getKey(), summary);
        }
        return new Comparison("PENDING_HUMAN_REVIEW", capture, groups, unions, rows);
    }
    static Union union(ExtensionFixture.Case q, List<String> vector, List<String> keyword, List<String> rrf, int budget, ChallengeData.Corpus corpus) {
        Set<String> v = new LinkedHashSet<>(vector.stream().limit(budget).toList()), k = new LinkedHashSet<>(keyword.stream().limit(budget).toList());
        Set<String> all = new TreeSet<>(v);
        all.addAll(k);
        var labels = corpus.grades().get(q.id());
        var relevant = all.stream().filter(id -> labels.get(id) > 0).toList();
        long total = labels.values().stream().filter(grade -> grade > 0).count();
        return new Union(List.copyOf(all), all.size(), total == 0 ? null : (double) relevant.size() / total,
                total == 0 ? null : ChallengeData.coverage(q, all, corpus), relevant.stream().filter(id -> !k.contains(id)).toList(),
                relevant.stream().filter(id -> !v.contains(id)).toList(), relevant.stream().filter(id -> !rrf.contains(id)).toList(),
                relevant.stream().filter(id -> !rrf.stream().limit(5).toList().contains(id)).toList());
    }
    private static List<String> ids(List<ExtensionEvaluation.Hit> hits) { return hits.stream().map(ExtensionEvaluation.Hit::chunk).toList(); }
    private static FilenameAblation.Summary summarize(List<FilenameAblation.Score> values) {
        var m = values.stream().map(FilenameAblation.Score::metrics).filter(Objects::nonNull).toList();
        var multi = values.stream().map(FilenameAblation.Score::allFacts5).filter(Objects::nonNull).toList();
        return new FilenameAblation.Summary(values.size(), m.size(), m.isEmpty() ? null : new RerankEvaluation.Metrics(
                m.stream().mapToDouble(RerankEvaluation.Metrics::hit).average().orElseThrow(), m.stream().mapToDouble(RerankEvaluation.Metrics::recall).average().orElseThrow(),
                m.stream().mapToDouble(RerankEvaluation.Metrics::mrr).average().orElseThrow(), m.stream().mapToDouble(RerankEvaluation.Metrics::ndcg).average().orElseThrow()),
                mean(values.stream().map(FilenameAblation.Score::recall30).toList()), mean(values.stream().map(FilenameAblation.Score::recall50).toList()),
                mean(values.stream().map(FilenameAblation.Score::factCoverage5).toList()), multi.size(), (int) multi.stream().filter(Boolean::booleanValue).count());
    }
    private static Double mean(List<Double> values) { return values.stream().filter(Objects::nonNull).mapToDouble(Double::doubleValue).average().stream().boxed().findFirst().orElse(null); }
}
