package com.yche.springmind.qa.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Offline comparison of channel ablations with the already-reviewed, fixed corpus. */
final class RetrievalAblation {
    static final Path SOURCE = Path.of("evaluation/rerank/results/northstar-2026-10-10");
    static final List<String> MODES = List.of("KEYWORD", "VECTOR", "HYBRID");
    static final int K = 5;

    record Hit(long chunkId, String sourceId, double score) {}
    record CapturedRow(String id, List<Hit> vectorHits, List<Hit> keywordHits,
                       Map<String, List<String>> orders, double vectorMs, double keywordMs) {}
    record Capture(String capturedAt, String datasetSha256, String fixtureSha256,
                   String embeddingModel, String embeddingDigest, int channelTopK, int candidateLimit,
                   int rrfConstant, int neighborWindow, int evidenceTokenBudget, int newProviderRequests,
                   String queryMode, String captureMethod, List<CapturedRow> rows) {}
    record ScoredRow(String id, String category, String query, Map<String, Integer> relevance,
                     Map<String, List<String>> orders, Map<String, RerankEvaluation.Metrics> metrics,
                     Map<String, Double> candidateRecall, Map<String, Boolean> allRequiredEvidence,
                     int vectorHits, int keywordHits) {}
    record Aggregate(int questions, int answerable, int unanswerable, RerankEvaluation.Metrics metrics,
                     Double candidateRecall, int multiEvidenceQuestions, int multiEvidenceComplete,
                     Double multiEvidenceCompleteRate) {}
    record Comparison(String mode, String datasetSha256, String captureSha256,
                      Map<String, String> reusedReportSha256, int k, int newProviderRequests,
                      Map<String, Map<String, Aggregate>> groups, List<ScoredRow> rows) {}

    private RetrievalAblation() {}

    static Comparison compare(RerankEvaluation.Dataset dataset, Capture capture, String captureHash,
                              Map<String, JsonNode> reranked, Map<String, String> reportHashes) {
        RerankEvaluation.validate(dataset);
        require(capture.newProviderRequests() == 0 && capture.channelTopK() == 50
                && capture.candidateLimit() == 30 && capture.rrfConstant() == 60
                && capture.neighborWindow() == 1 && capture.evidenceTokenBudget() == 4096,
                "Unexpected capture settings");
        require(capture.rows().size() == dataset.cases().size(), "Capture case count mismatch");
        require(reranked.keySet().equals(Set.of("QWEN_2S_REUSED", "QWEN_5S_REUSED"))
                && reportHashes.keySet().equals(reranked.keySet()), "Both original timeout rounds must be retained");
        reranked.values().forEach(report -> {
            require(capture.datasetSha256().equals(report.path("datasetSha256").asText())
                    && report.path("k").asInt() == K
                    && "HUMAN_REVIEWED".equals(report.path("annotationStatus").asText())
                    && report.path("rows").size() == dataset.cases().size(), "Incompatible recorded rerank report");
        });
        var mapper = RerankEvaluationRun.datasetMapper();
        List<ScoredRow> rows = new ArrayList<>();
        for (int i = 0; i < dataset.cases().size(); i++) {
            var example = dataset.cases().get(i);
            var recorded = capture.rows().get(i);
            require(example.id().equals(recorded.id()), "Capture case order mismatch");
            require(recorded.orders().keySet().equals(new HashSet<>(MODES)), "Missing/unknown retrieval mode");
            Map<String, List<String>> orders = new LinkedHashMap<>(recorded.orders());
            require(example.candidates().equals(orders.get("HYBRID")), "Hybrid rank drift prevents rerank reuse");
            verifyRawRanks(recorded, capture.candidateLimit(), capture.rrfConstant());
            for (var entry : reranked.entrySet()) {
                var row = entry.getValue().path("rows").get(i);
                require(example.id().equals(row.path("id").asText())
                        && mapper.valueToTree(example.candidates()).equals(row.path("inputOrder")), "Rerank input mismatch");
                List<String> output = new ArrayList<>();
                row.path("outputOrder").forEach(id -> output.add(id.asText()));
                require(output.size() == example.candidates().size()
                        && new HashSet<>(output).equals(new HashSet<>(example.candidates())), "Rerank output is not a permutation");
                orders.put(entry.getKey(), List.copyOf(output));
            }
            Map<String, RerankEvaluation.Metrics> metrics = new LinkedHashMap<>();
            Map<String, Double> pools = new LinkedHashMap<>();
            Map<String, Boolean> complete = new LinkedHashMap<>();
            Set<String> required = requiredEvidence(example);
            for (var entry : orders.entrySet()) {
                List<String> order = entry.getValue();
                require(order.size() <= capture.candidateLimit() && new HashSet<>(order).size() == order.size()
                        && order.stream().allMatch(id -> id.startsWith(example.id() + "__") && dataset.documents().containsKey(id)),
                        "Foreign/duplicate/oversized candidate order");
                metrics.put(entry.getKey(), RerankEvaluation.metrics(order, example.relevance(), K));
                var pool = RerankEvaluation.metrics(order, example.relevance(), capture.candidateLimit());
                pools.put(entry.getKey(), pool == null ? null : pool.recall());
                complete.put(entry.getKey(), required.isEmpty() ? null : order.subList(0, Math.min(K, order.size())).containsAll(required));
            }
            rows.add(new ScoredRow(example.id(), example.category(), example.query(), example.relevance(), orders,
                    metrics, pools, complete, recorded.vectorHits().size(), recorded.keywordHits().size()));
        }
        Map<String, Map<String, Aggregate>> groups = new LinkedHashMap<>();
        groups.put("ALL", aggregate(rows));
        for (String language : List.of("zh", "en")) {
            groups.put(language, aggregate(rows.stream().filter(row -> row.category().endsWith("-" + language)).toList()));
        }
        for (String category : List.of("direct", "paraphrase", "multi-evidence", "unanswerable")) {
            groups.put(category, aggregate(rows.stream().filter(row -> row.category().startsWith(category + "-")).toList()));
        }
        return new Comparison("LOCAL_RETRIEVAL_ABLATION_RECORDED_RERANK", capture.datasetSha256(), captureHash,
                reportHashes, K, 0, groups, rows);
    }

    // Grade 1 is background, not a required answer component. Limit this metric to multi-evidence questions.
    static Set<String> requiredEvidence(RerankEvaluation.Example example) {
        if (!example.category().startsWith("multi-evidence-")) return Set.of();
        Set<String> required = new LinkedHashSet<>();
        example.relevance().forEach((id, grade) -> { if (grade >= 2) required.add(id); });
        require(required.size() >= 2 && example.relevance().values().stream().noneMatch(grade -> grade == 3),
                "This complete-evidence metric expects complementary grade-2 passages, not alternative sufficient sources");
        return required;
    }

    // Independent arithmetic check, valid only for this verified one-chunk-per-document corpus.
    static void verifyRawRanks(CapturedRow row, int limit, int rrfConstant) {
        Map<String, Double> scores = new HashMap<>();
        Map<String, Long> chunks = new HashMap<>();
        Map<Long, String> identities = new HashMap<>();
        for (String mode : List.of("VECTOR", "KEYWORD")) {
            List<Hit> hits = mode.equals("VECTOR") ? row.vectorHits() : row.keywordHits();
            require(hits.size() <= 50 && hits.stream().map(Hit::sourceId).distinct().count() == hits.size(), "Invalid raw channel hits");
            List<String> order = hits.stream().limit(limit).map(hit -> RetrievalSnapshot.key(row.id(), hit.sourceId())).toList();
            require(order.equals(row.orders().get(mode)), "Raw channel order differs from candidate order");
            for (int i = 0; i < hits.size(); i++) {
                Hit hit = hits.get(i);
                require(hit.chunkId() > 0 && Double.isFinite(hit.score()), "Invalid raw hit value");
                Long old = chunks.putIfAbsent(hit.sourceId(), hit.chunkId());
                String identity = identities.putIfAbsent(hit.chunkId(), hit.sourceId());
                require((old == null || old == hit.chunkId()) && (identity == null || identity.equals(hit.sourceId())), "Chunk identity mismatch");
                scores.merge(hit.sourceId(), 1.0 / (rrfConstant + i + 1), Double::sum);
            }
        }
        var fused = scores.keySet().stream().sorted(Comparator.<String>comparingDouble(scores::get).reversed()
                .thenComparingLong(chunks::get)).limit(limit).map(id -> RetrievalSnapshot.key(row.id(), id)).toList();
        require(fused.equals(row.orders().get("HYBRID")), "Recorded production RRF differs from raw rank arithmetic");
    }

    private static Map<String, Aggregate> aggregate(List<ScoredRow> rows) {
        Map<String, Aggregate> result = new LinkedHashMap<>();
        for (String mode : List.of("KEYWORD", "VECTOR", "HYBRID", "QWEN_2S_REUSED", "QWEN_5S_REUSED")) {
            var judged = rows.stream().map(row -> row.metrics().get(mode)).filter(Objects::nonNull).toList();
            var multi = rows.stream().map(row -> row.allRequiredEvidence().get(mode)).filter(Objects::nonNull).toList();
            int complete = (int) multi.stream().filter(Boolean::booleanValue).count();
            result.put(mode, new Aggregate(rows.size(), judged.size(), rows.size() - judged.size(),
                    judged.isEmpty() ? null : new RerankEvaluation.Metrics(
                            judged.stream().mapToDouble(RerankEvaluation.Metrics::hit).average().orElseThrow(),
                            judged.stream().mapToDouble(RerankEvaluation.Metrics::recall).average().orElseThrow(),
                            judged.stream().mapToDouble(RerankEvaluation.Metrics::mrr).average().orElseThrow(),
                            judged.stream().mapToDouble(RerankEvaluation.Metrics::ndcg).average().orElseThrow()),
                    rows.stream().map(row -> row.candidateRecall().get(mode)).filter(Objects::nonNull)
                            .mapToDouble(Double::doubleValue).average().stream().boxed().findFirst().orElse(null),
                    multi.size(), complete, multi.isEmpty() ? null : (double) complete / multi.size()));
        }
        return result;
    }

    static String hash(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }

    static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
