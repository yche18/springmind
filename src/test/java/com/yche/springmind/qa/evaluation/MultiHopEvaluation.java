package com.yche.springmind.qa.evaluation;

import java.util.*;
import static com.yche.springmind.qa.evaluation.RetrievalAblation.require;

/** Reference-fact metrics, deliberately distinct from answer accuracy. */
final class MultiHopEvaluation {
    record Hit(long id, double score) {}
    record Row(String id, List<Hit> keyword, List<Hit> vector, Map<String, MultiHopReplay.Context> contexts) {}
    record Capture(String startedAt, String completedAt, String freezeSha256, String modelDigest,
                   String elasticVersion, String ollamaVersion, String index, int cloudCalls,
                   Map<String, String> cacheHashes, Map<Long, Long> chunkDocuments, List<Row> rows) {}
    record Score(Double factRecall, Boolean allFacts, Double documentRecall, Boolean allDocuments, int chunks) {}
    record Scored(String id, String split, String type, Map<String, Score> scores,
                  List<String> keywordOnlyFacts, List<String> vectorOnlyFacts, boolean unionOnlyComplete) {}
    record Summary(int questions, int answerable, Double factRecall, int complete, Double completeRate,
                   Double documentRecall, int allDocuments, double averageChunks) {}
    record Report(String annotationStatus, int cloudCalls, Map<String, Map<String, Summary>> groups, List<Scored> rows) {}

    static List<Long> ids(List<Hit> hits, int limit) { return hits.stream().limit(limit).map(Hit::id).toList(); }
    static List<Long> rrf(List<Hit> keyword, List<Hit> vector) {
        Map<Long, Double> scores = new HashMap<>();
        for (var list : List.of(keyword, vector)) for (int i = 0; i < list.size(); i++) scores.merge(list.get(i).id(), 1.0 / (61 + i), Double::sum);
        return scores.keySet().stream().sorted(Comparator.<Long>comparingDouble(scores::get).reversed().thenComparingLong(x -> x)).toList();
    }
    static Set<Long> union(List<Hit> a, List<Hit> b, int depth) {
        Set<Long> result = new LinkedHashSet<>(ids(a, depth)); result.addAll(ids(b, depth)); return result;
    }
    static Set<String> facts(MultiHopData.Question q, Collection<Long> ids) {
        Set<Long> present = new HashSet<>(ids);
        Set<String> result = new LinkedHashSet<>();
        for (var f : q.facts()) if (f.alternatives().stream().anyMatch(present::containsAll)) result.add(f.id());
        return result;
    }
    static Score score(MultiHopData.Question q, Collection<Long> ids, Map<Long, Long> docs) {
        require(ids.stream().allMatch(docs::containsKey), "Unknown chunk");
        if (q.facts().isEmpty()) return new Score(null, null, null, null, ids.size());
        var found = facts(q, ids);
        Set<Long> required = new HashSet<>(q.facts().stream().map(MultiHopData.Fact::documentId).toList());
        Set<Long> seen = new HashSet<>(); ids.forEach(id -> seen.add(docs.get(id))); seen.retainAll(required);
        return new Score((double) found.size() / q.facts().size(), found.size() == q.facts().size(),
                (double) seen.size() / required.size(), seen.size() == required.size(), ids.size());
    }
    static Report analyze(MultiHopData.Sample sample, Capture capture) {
        require(capture.cloudCalls() == 0 && capture.rows().size() == 60, "Incomplete or cloud capture");
        List<Scored> rows = new ArrayList<>();
        for (int i = 0; i < sample.questions().size(); i++) {
            var q = sample.questions().get(i); var row = capture.rows().get(i);
            require(q.id().equals(row.id()), "Case order changed");
            for (var hits : List.of(row.keyword(), row.vector())) {
                require(hits.size() <= 50 && hits.stream().map(Hit::id).distinct().count() == hits.size()
                        && hits.stream().allMatch(h -> Double.isFinite(h.score()) && capture.chunkDocuments().containsKey(h.id())), "Invalid hits");
            }
            Map<String, Score> scores = new LinkedHashMap<>();
            var fused = rrf(row.keyword(), row.vector());
            for (int k : List.of(5, 30, 50)) {
                scores.put("KEYWORD@" + k, score(q, ids(row.keyword(), k), capture.chunkDocuments()));
                scores.put("VECTOR@" + k, score(q, ids(row.vector(), k), capture.chunkDocuments()));
                scores.put("RRF@" + k, score(q, fused.stream().limit(k).toList(), capture.chunkDocuments()));
            }
            scores.put("UNION25", score(q, union(row.keyword(), row.vector(), 25), capture.chunkDocuments()));
            scores.put("UNION50", score(q, union(row.keyword(), row.vector(), 50), capture.chunkDocuments()));
            require(row.contexts().keySet().equals(Set.of("KEYWORD", "VECTOR", "RRF")), "Missing production context");
            row.contexts().forEach((mode, context) -> {
                require(context.tokens() <= 4096 && context.windows() <= 5, "Context bounds changed");
                scores.put(mode + "_CONTEXT", score(q, context.chunks(), capture.chunkDocuments()));
            });
            var k = facts(q, ids(row.keyword(), 50)); var v = facts(q, ids(row.vector(), 50));
            Set<String> ko = new LinkedHashSet<>(k); ko.removeAll(v);
            Set<String> vo = new LinkedHashSet<>(v); vo.removeAll(k);
            boolean unionOnly = Boolean.TRUE.equals(scores.get("UNION50").allFacts())
                    && !Boolean.TRUE.equals(scores.get("KEYWORD@50").allFacts()) && !Boolean.TRUE.equals(scores.get("VECTOR@50").allFacts());
            rows.add(new Scored(q.id(), q.split(), q.type(), scores, List.copyOf(ko), List.copyOf(vo), unionOnly));
        }
        Map<String, Map<String, Summary>> groups = new LinkedHashMap<>();
        for (String split : List.of("FORMAL", "CALIBRATION")) {
            groups.put(split, aggregate(rows.stream().filter(r -> r.split().equals(split)).toList()));
            for (String type : List.of("inference_query", "comparison_query", "temporal_query", "null_query"))
                groups.put(split + "/" + type, aggregate(rows.stream().filter(r -> r.split().equals(split) && r.type().equals(type)).toList()));
        }
        return new Report(sample.annotationStatus(), 0, groups, rows);
    }
    static Map<String, Summary> aggregate(List<Scored> rows) {
        Map<String, Summary> summary = new LinkedHashMap<>();
        for (String mode : rows.getFirst().scores().keySet()) {
            var judged = rows.stream().map(r -> r.scores().get(mode)).filter(s -> s.factRecall() != null).toList();
            int all = (int) judged.stream().filter(Score::allFacts).count();
            summary.put(mode, new Summary(rows.size(), judged.size(), judged.isEmpty() ? null : judged.stream().mapToDouble(Score::factRecall).average().orElseThrow(),
                    all, judged.isEmpty() ? null : (double) all / judged.size(),
                    judged.isEmpty() ? null : judged.stream().mapToDouble(Score::documentRecall).average().orElseThrow(),
                    (int) judged.stream().filter(Score::allDocuments).count(), rows.stream().mapToInt(r -> r.scores().get(mode).chunks()).average().orElseThrow()));
        }
        return summary;
    }
}
