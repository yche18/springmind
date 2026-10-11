package com.yche.springmind.qa.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import com.yche.springmind.retrieval.elasticsearch.ElasticsearchChunkIndexService;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.file.*;
import java.util.*;
import static com.yche.springmind.qa.evaluation.RetrievalAblation.require;

/** Remove one field at both scoring stages while preserving the production query. */
final class FilenameAblation {
    static final Path SOURCE = Path.of("evaluation/retrieval/results/harborview-2026-10-10");
    static final Path PROTOCOL = Path.of("evaluation/retrieval/filename-ablation/README.md");
    static final List<String> MODES = List.of("BODY_ONLY", "BODY_AND_FILENAME");
    record CapturedRow(String id, Map<String, JsonNode> requests, Map<String, List<ExtensionEvaluation.Hit>> hits) {}
    record Capture(String startedAt, String completedAt, String fixtureSha256, String corpusSha256,
                   String approvalSha256, String protocolSha256, String originalCaptureSha256,
                   JsonNode indexMetadata, int newModelRequests, List<CapturedRow> rows) {}
    record Score(RerankEvaluation.Metrics metrics, Double recall30, Double recall50, Double factCoverage5,
                 Boolean allFacts5) {}
    record Row(String id, String category, String language, Map<String, Score> scores,
               boolean originalKeywordOrderMatches, boolean originalKeywordScoresMatch) {}
    record Summary(int cases, int answerable, RerankEvaluation.Metrics metrics, Double recall30,
                   Double recall50, Double factCoverage5, int complementaryCases, int completeComplementaryCases) {}
    record Comparison(String annotationStatus, String captureSha256, int newModelRequests,
                      int originalOrderMatches, int originalScoreMatches,
                      Map<String, Map<String, Summary>> groups, List<Row> rows) {}

    private FilenameAblation() {}

    static Path output() {
        return Path.of(System.getProperty("eval.filenameOutput", "evaluation/retrieval/results/harborview-filename-2026-10-10"));
    }

    static ObjectNode request(long group, String query, boolean includeFilename) {
        var mapper = RerankEvaluationRun.datasetMapper();
        var service = new ElasticsearchChunkIndexService(mapper, "localhost", 9200, "http", "springmind_document_chunks");
        Object body = ReflectionTestUtils.invokeMethod(service, "buildKeywordSearchRequestBody", group, query, 50);
        ObjectNode request = mapper.valueToTree(body);
        if (!includeFilename) {
            removeFilename((ArrayNode) request.at("/query/bool/should"));
            removeFilename((ArrayNode) request.at("/rescore/query/rescore_query/bool/should"));
        }
        return request;
    }

    private static void removeFilename(ArrayNode clauses) {
        int removed = 0;
        for (int i = clauses.size() - 1; i >= 0; i--) {
            JsonNode clause = clauses.get(i);
            if (clause.path("match").has("fileName") || clause.path("match_phrase").has("fileName")) {
                clauses.remove(i);
                removed++;
            }
        }
        require(removed == 2 && clauses.size() == 2, "Production filename/body query structure changed");
    }

    static ExtensionEvaluation.Corpus corpus() throws Exception {
        return RerankEvaluationRun.datasetMapper().readValue(SOURCE.resolve("corpus.json").toFile(), ExtensionEvaluation.Corpus.class);
    }

    static ExtensionEvaluation.Capture original() throws Exception {
        return RerankEvaluationRun.datasetMapper().readValue(SOURCE.resolve("capture.json").toFile(), ExtensionEvaluation.Capture.class);
    }

    static void verifyApproval() throws Exception {
        require(ExtensionEvaluation.FIXTURE_HASH.equals(RetrievalAblation.hash(ExtensionFixture.PATH)), "Fixture changed");
        var initial = ExtensionEvaluation.compare(ExtensionFixture.read(), corpus(), original(),
                RetrievalAblation.hash(SOURCE.resolve("corpus.json")), RetrievalAblation.hash(SOURCE.resolve("capture.json")));
        require("HUMAN_REVIEWED".equals(ExtensionReviewApproval.apply(SOURCE, corpus(), initial).annotationStatus()), "Human approval required");
    }

    static Comparison compare(Capture capture, String captureHash) throws Exception {
        verifyApproval();
        var fixture = ExtensionFixture.read();
        var corpus = corpus();
        var original = original();
        require(capture.fixtureSha256().equals(RetrievalAblation.hash(ExtensionFixture.PATH))
                && capture.corpusSha256().equals(RetrievalAblation.hash(SOURCE.resolve("corpus.json")))
                && capture.approvalSha256().equals(RetrievalAblation.hash(SOURCE.resolve("review-approval.json")))
                && capture.protocolSha256().equals(RetrievalAblation.hash(PROTOCOL))
                && capture.originalCaptureSha256().equals(RetrievalAblation.hash(SOURCE.resolve("capture.json"))), "Ablation input hash mismatch");
        require(capture.newModelRequests() == 0 && capture.rows().size() == fixture.cases().size(), "Incomplete/invalid capture");
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < fixture.cases().size(); i++) {
            var question = fixture.cases().get(i);
            var saved = capture.rows().get(i);
            require(saved.id().equals(question.id()) && saved.hits().keySet().equals(new HashSet<>(MODES))
                    && saved.requests().keySet().equals(new HashSet<>(MODES)), "Missing case/mode");
            Map<String, Score> scores = new LinkedHashMap<>();
            for (String mode : MODES) {
                // JSON round trips narrow small long values to ints; compare canonical parsed trees.
                require(RerankEvaluationRun.datasetMapper().readTree(request(corpus.groupId(), question.query(), mode.equals("BODY_AND_FILENAME")).toString())
                                .equals(RerankEvaluationRun.datasetMapper().readTree(saved.requests().get(mode).toString())),
                        "Request differs from the declared single-field ablation");
                var hits = saved.hits().get(mode);
                require(hits.size() <= 50 && hits.stream().map(ExtensionEvaluation.Hit::chunk).distinct().count() == hits.size()
                        && hits.stream().allMatch(hit -> corpus.chunks().containsKey(hit.chunk()) && Double.isFinite(hit.score()) && hit.score() >= 0),
                        "Invalid/foreign raw hits");
                var ranking = hits.stream().map(ExtensionEvaluation.Hit::chunk).toList();
                var labels = corpus.grades().get(question.id());
                var metrics = RerankEvaluation.metrics(ranking, labels, 5);
                var pool30 = RerankEvaluation.metrics(ranking, labels, 30);
                var pool50 = RerankEvaluation.metrics(ranking, labels, 50);
                var coverage = ExtensionEvaluation.chunkCoverage(question, ranking.stream().limit(5).toList(), corpus.chunks());
                scores.put(mode, new Score(metrics, pool30 == null ? null : pool30.recall(), pool50 == null ? null : pool50.recall(),
                        coverage, question.evidence().size() > 1 ? coverage == 1.0 : null));
            }
            var old = original.rows().get(i).keyword();
            var fresh = saved.hits().get("BODY_AND_FILENAME");
            rows.add(new Row(question.id(), question.category(), question.language(), scores,
                    old.stream().map(ExtensionEvaluation.Hit::chunk).toList().equals(fresh.stream().map(ExtensionEvaluation.Hit::chunk).toList()), old.equals(fresh)));
        }
        Map<String, Map<String, Summary>> groups = new LinkedHashMap<>();
        groups.put("ALL", summarize(rows));
        for (String language : List.of("zh", "en")) groups.put(language, summarize(rows.stream().filter(row -> row.language().equals(language)).toList()));
        for (String category : List.of("exact", "semantic", "cross-language", "multi-evidence", "long-document", "unanswerable"))
            groups.put(category, summarize(rows.stream().filter(row -> row.category().equals(category)).toList()));
        return new Comparison("HUMAN_REVIEWED", captureHash, 0, (int) rows.stream().filter(Row::originalKeywordOrderMatches).count(),
                (int) rows.stream().filter(Row::originalKeywordScoresMatch).count(), groups, rows);
    }

    private static Map<String, Summary> summarize(List<Row> rows) {
        Map<String, Summary> result = new LinkedHashMap<>();
        for (String mode : MODES) {
            var scores = rows.stream().map(row -> row.scores().get(mode)).toList();
            var metrics = scores.stream().map(Score::metrics).filter(Objects::nonNull).toList();
            var multi = scores.stream().map(Score::allFacts5).filter(Objects::nonNull).toList();
            result.put(mode, new Summary(rows.size(), metrics.size(), metrics.isEmpty() ? null : new RerankEvaluation.Metrics(
                    metrics.stream().mapToDouble(RerankEvaluation.Metrics::hit).average().orElseThrow(),
                    metrics.stream().mapToDouble(RerankEvaluation.Metrics::recall).average().orElseThrow(),
                    metrics.stream().mapToDouble(RerankEvaluation.Metrics::mrr).average().orElseThrow(),
                    metrics.stream().mapToDouble(RerankEvaluation.Metrics::ndcg).average().orElseThrow()),
                    mean(scores.stream().map(Score::recall30).toList()), mean(scores.stream().map(Score::recall50).toList()),
                    mean(scores.stream().map(Score::factCoverage5).toList()), multi.size(), (int) multi.stream().filter(Boolean::booleanValue).count()));
        }
        return result;
    }

    private static Double mean(List<Double> values) {
        return values.stream().filter(Objects::nonNull).mapToDouble(Double::doubleValue).average().stream().boxed().findFirst().orElse(null);
    }
}
