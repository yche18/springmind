package com.yche.springmind.qa.evaluation;

import java.util.*;
import static com.yche.springmind.qa.evaluation.RetrievalAblation.require;

/** Document-level BEIR scoring with trec_eval-compatible linear nDCG gains. */
final class BeirEvaluation {
    record Hit(String id, double score) {}
    record CapturedRow(String queryId, Map<String, Integer> qrels, List<Hit> bm25, List<Hit> dense) {}
    record Capture(String dataset, String startedAt, String completedAt, String freezeSha256, EmbeddingComparison.Model model,
                   String ollamaVersion, String elasticVersion, String index, int documents, int queries,
                   int encodedTexts, int cloudRequests, Map<String, String> cacheHashes, List<CapturedRow> rows) {}
    record Metrics(double ndcg10, double mrr10, double recall5, double recall10, double recall50, double recall100,
                   double recall1000, double judgedFraction10) {}
    record Union(int size, double recall, List<String> keywordExclusive, List<String> denseExclusive, List<String> lostAtRrf10) {}
    record Row(String queryId, Map<String, Metrics> metrics, Map<String, Union> unions) {}
    record UnionMean(double meanSize, double recall, int keywordExclusive, int denseExclusive, int lostAtRrf10) {}
    record Effect(double meanDelta, double lower95, double upper95, List<String> improved, List<String> regressed, List<String> tied) {}
    record Comparison(String dataset, int documents, int queries, Map<String, Metrics> means, Map<String, UnionMean> unions,
                      Map<String, Effect> pairedEffects, List<Row> rows) {}
    static final List<String> MODES = List.of("BM25", "BGE_M3", "RRF_50", "RRF_1000");
    private BeirEvaluation() {}
    static List<String> ids(List<Hit> hits) { return hits.stream().map(Hit::id).toList(); }
    static List<String> rrf(List<String> a, List<String> b, int depth) {
        Map<String, Double> scores = new HashMap<>();
        for (var list : List.of(a, b)) for (int i = 0; i < Math.min(depth, list.size()); i++) scores.merge(list.get(i), 1.0 / (61 + i), Double::sum);
        return scores.keySet().stream().sorted(Comparator.<String>comparingDouble(scores::get).reversed().thenComparing(Comparator.naturalOrder())).limit(1000).toList();
    }
    static Metrics score(List<String> rank, Map<String, Integer> qrels) {
        require(new HashSet<>(rank).size() == rank.size(), "Duplicate scored result");
        long relevant = qrels.values().stream().filter(g -> g > 0).count();
        require(relevant > 0 && qrels.values().stream().allMatch(g -> g >= 0), "Invalid qrels");
        var ideal = qrels.values().stream().sorted(Comparator.reverseOrder()).limit(10).toList();
        double dcg = 0, idcg = 0, rr = 0; int judged = 0;
        for (int i = 0; i < ideal.size(); i++) idcg += ideal.get(i) / log2(i + 2);
        for (int i = 0; i < Math.min(10, rank.size()); i++) {
            int grade = qrels.getOrDefault(rank.get(i), 0);
            dcg += grade / log2(i + 2);
            if (grade > 0 && rr == 0) rr = 1.0 / (i + 1);
            if (qrels.containsKey(rank.get(i))) judged++;
        }
        return new Metrics(dcg / idcg, rr, recall(rank, qrels, 5), recall(rank, qrels, 10), recall(rank, qrels, 50),
                recall(rank, qrels, 100), recall(rank, qrels, 1000), rank.isEmpty() ? 0 : (double) judged / Math.min(10, rank.size()));
    }
    private static double recall(List<String> rank, Map<String, Integer> qrels, int k) {
        return (double) rank.stream().limit(k).filter(id -> qrels.getOrDefault(id, 0) > 0).count() / qrels.values().stream().filter(g -> g > 0).count();
    }
    static Union union(List<String> bm25, List<String> dense, List<String> rrf, Map<String, Integer> labels, int depth) {
        Set<String> k = new HashSet<>(bm25.stream().limit(depth).toList()), v = new HashSet<>(dense.stream().limit(depth).toList());
        Set<String> all = new TreeSet<>(k); all.addAll(v);
        var relevant = all.stream().filter(id -> labels.getOrDefault(id, 0) > 0).toList();
        return new Union(all.size(), (double) relevant.size() / labels.values().stream().filter(g -> g > 0).count(),
                relevant.stream().filter(id -> !v.contains(id)).toList(), relevant.stream().filter(id -> !k.contains(id)).toList(),
                relevant.stream().filter(id -> !rrf.stream().limit(10).toList().contains(id)).toList());
    }
    static Comparison compare(Capture capture) {
        require(BeirData.DATASETS.contains(capture.dataset()) && capture.cloudRequests() == 0 && capture.rows().size() == capture.queries()
                && capture.encodedTexts() == capture.documents() + capture.queries(), "Invalid capture counts");
        Set<String> queries = new HashSet<>();
        List<Row> rows = new ArrayList<>();
        for (var row : capture.rows()) {
            require(queries.add(row.queryId()), "Duplicate test query");
            for (var hits : List.of(row.bm25(), row.dense())) {
                require(hits.size() <= 1000 && new HashSet<>(ids(hits)).size() == hits.size() && !ids(hits).contains(row.queryId())
                        && hits.stream().allMatch(h -> Double.isFinite(h.score())), "Invalid hits");
            }
            Map<String, List<String>> ranks = new LinkedHashMap<>();
            var k = ids(row.bm25()); var v = ids(row.dense());
            ranks.put("BM25", k); ranks.put("BGE_M3", v); ranks.put("RRF_50", rrf(k, v, 50)); ranks.put("RRF_1000", rrf(k, v, 1000));
            Map<String, Metrics> metrics = new LinkedHashMap<>(); ranks.forEach((mode, rank) -> metrics.put(mode, score(rank, row.qrels())));
            Map<String, Union> unions = new LinkedHashMap<>();
            for (int depth : List.of(25, 50)) unions.put("U" + depth, union(k, v, ranks.get("RRF_50"), row.qrels(), depth));
            rows.add(new Row(row.queryId(), metrics, unions));
        }
        Map<String, Metrics> means = new LinkedHashMap<>();
        for (String mode : MODES) means.put(mode, mean(rows.stream().map(row -> row.metrics().get(mode)).toList()));
        Map<String, UnionMean> unions = new LinkedHashMap<>();
        for (String mode : List.of("U25", "U50")) {
            var u = rows.stream().map(row -> row.unions().get(mode)).toList();
            unions.put(mode, new UnionMean(u.stream().mapToInt(Union::size).average().orElseThrow(), u.stream().mapToDouble(Union::recall).average().orElseThrow(),
                    u.stream().mapToInt(s -> s.keywordExclusive().size()).sum(), u.stream().mapToInt(s -> s.denseExclusive().size()).sum(), u.stream().mapToInt(s -> s.lostAtRrf10().size()).sum()));
        }
        Map<String, Effect> effects = new LinkedHashMap<>();
        for (String rrf : List.of("RRF_50", "RRF_1000")) for (String baseline : List.of("BM25", "BGE_M3")) effects.put(rrf + "_minus_" + baseline, effect(rows, baseline, rrf));
        return new Comparison(capture.dataset(), capture.documents(), capture.queries(), means, unions, effects, rows);
    }
    static Effect effect(List<Row> rows, String before, String after) {
        List<String> wins = new ArrayList<>(), losses = new ArrayList<>(), ties = new ArrayList<>();
        double[] deltas = new double[rows.size()];
        for (int i = 0; i < rows.size(); i++) {
            var row = rows.get(i); double d = row.metrics().get(after).ndcg10() - row.metrics().get(before).ndcg10(); deltas[i] = d;
            (d > 1e-12 ? wins : d < -1e-12 ? losses : ties).add(row.queryId());
        }
        Random random = new Random(20261011L);
        double[] bootstrap = new double[2000];
        for (int b = 0; b < bootstrap.length; b++) { double sum = 0; for (int i = 0; i < deltas.length; i++) sum += deltas[random.nextInt(deltas.length)]; bootstrap[b] = sum / deltas.length; }
        Arrays.sort(bootstrap);
        return new Effect(Arrays.stream(deltas).average().orElseThrow(), bootstrap[49], bootstrap[1949], wins, losses, ties);
    }
    private static Metrics mean(List<Metrics> m) {
        return new Metrics(avg(m, Metrics::ndcg10), avg(m, Metrics::mrr10), avg(m, Metrics::recall5), avg(m, Metrics::recall10),
                avg(m, Metrics::recall50), avg(m, Metrics::recall100), avg(m, Metrics::recall1000), avg(m, Metrics::judgedFraction10));
    }
    private static double avg(List<Metrics> m, java.util.function.ToDoubleFunction<Metrics> f) { return m.stream().mapToDouble(f).average().orElseThrow(); }
    private static double log2(double n) { return Math.log(n) / Math.log(2); }
}
