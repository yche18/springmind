package com.yche.springmind.qa.evaluation;

import com.yche.springmind.qa.rag.EvidenceReranker;
import org.springframework.ai.document.Document;

import java.util.*;

/** Fixed-pool replay: evaluates ranking, not retrieval, answer generation or refusal. */
public final class RerankEvaluation {
    private RerankEvaluation() {}

    public record Dataset(String provenance, Map<String, String> documents, List<Example> cases) {}
    public record Example(String id, String category, String query, List<String> candidates,
                          Map<String, Integer> relevance) {}
    public record Metrics(double hit, double recall, double mrr, double ndcg) {}
    public record Row(String id, String category, List<String> inputOrder, List<String> outputOrder,
                      String status, Double latencyMs, Double candidateRecall, Metrics baseline, Metrics reranked) {}
    public record Summary(int cases, int judgedAnswerableCases, int unanswerableCases,
                          Map<String, Long> statuses, Double candidateRecall,
                          Metrics baseline, Metrics reranked, Metrics delta,
                          Double latencyP50Ms, Double latencyP95Ms) {}
    public record Result(Summary summary, List<Row> rows) {}

    public static void validate(Dataset data) {
        require(data != null && ("SYNTHETIC".equals(data.provenance()) || "RRF_SNAPSHOT".equals(data.provenance())),
                "provenance must be SYNTHETIC or RRF_SNAPSHOT");
        require(data.documents() != null && !data.documents().isEmpty() && data.documents().size() <= 10_000,
                "documents must contain 1..10000 entries");
        data.documents().forEach((id, text) -> {
            require(validId(id), "Document IDs must be opaque alphanumeric identifiers (hyphens/underscores allowed)");
            require(text != null && !text.isBlank() && text.length() <= 100_000, "Invalid document text length");
        });
        require(data.cases() != null && !data.cases().isEmpty() && data.cases().size() <= 500,
                "cases must contain 1..500 examples");
        Set<String> ids = new HashSet<>();
        for (Example example : data.cases()) {
            require(example != null && validId(example.id()) && ids.add(example.id()), "Invalid or duplicate case ID");
            require(validId(example.category()), "Each case requires an opaque category");
            require(example.query() != null && !example.query().isBlank() && example.query().length() <= 16_000,
                    "Invalid query length");
            require(example.candidates() != null && example.candidates().size() <= 50,
                    "Each candidate pool must contain 0..50 IDs");
            require(example.candidates().stream().allMatch(RerankEvaluation::validId)
                    && new HashSet<>(example.candidates()).size() == example.candidates().size()
                    && data.documents().keySet().containsAll(example.candidates()), "Unknown or duplicate candidate ID");
            require(example.relevance() != null && example.relevance().keySet().stream().allMatch(RerankEvaluation::validId)
                    && data.documents().keySet().containsAll(example.relevance().keySet()),
                    "Relevance labels must reference corpus IDs");
            require(example.relevance().values().stream().allMatch(grade -> grade != null && grade >= 0 && grade <= 3),
                    "Relevance grades must be integers in 0..3");
        }
    }

    public static Result run(Dataset data, int k, EvidenceReranker reranker, boolean compare) {
        validate(data);
        require(k >= 1 && k <= 50, "k must be 1..50");
        List<Row> rows = new ArrayList<>();
        for (Example example : data.cases()) {
            List<Document> candidates = example.candidates().stream()
                    .map(id -> Document.builder().id(id).text(data.documents().get(id)).build()).toList();
            long start = System.nanoTime();
            EvidenceReranker.Result ranked = reranker.rerank(example.query(), candidates);
            double latency = (System.nanoTime() - start) / 1_000_000.0;
            List<String> output = ranked.documents().stream().map(Document::getId).toList();
            require(output.size() == example.candidates().size()
                    && new HashSet<>(output).equals(new HashSet<>(example.candidates())),
                    "Reranker must return exactly the same candidate identities");
            require(compare ? Set.of("APPLIED", "FALLBACK", "SKIPPED").contains(ranked.status())
                    : "DISABLED".equals(ranked.status()), "Unexpected reranker mode/status");
            Metrics baseline = metrics(example.candidates(), example.relevance(), k);
            Metrics after = compare ? metrics(output, example.relevance(), k) : null;
            Metrics pool = metrics(example.candidates(), example.relevance(), 50);
            rows.add(new Row(example.id(), example.category(), example.candidates(), compare ? output : null,
                    ranked.status(), compare && candidates.size() >= 2 ? latency : null,
                    pool == null ? null : pool.recall(), baseline, after));
        }
        Metrics before = mean(rows.stream().map(Row::baseline).toList());
        Metrics after = compare ? mean(rows.stream().map(Row::reranked).toList()) : null;
        List<Double> latencies = rows.stream().map(Row::latencyMs).filter(Objects::nonNull).sorted().toList();
        Map<String, Long> statuses = new TreeMap<>();
        rows.forEach(row -> statuses.merge(row.status(), 1L, Long::sum));
        int answerable = (int) rows.stream().filter(row -> row.baseline() != null).count();
        Summary summary = new Summary(rows.size(), answerable, rows.size() - answerable, statuses,
                rows.stream().map(Row::candidateRecall).filter(Objects::nonNull).mapToDouble(Double::doubleValue)
                        .average().stream().boxed().findFirst().orElse(null),
                before, after, difference(before, after), percentile(latencies, 0.50), percentile(latencies, 0.95));
        return new Result(summary, List.copyOf(rows));
    }

    // Recall and ideal DCG use all judged relevant corpus entries, including missed candidates.
    public static Metrics metrics(List<String> ranking, Map<String, Integer> labels, int k) {
        require(k >= 1, "k must be positive");
        long relevant = labels.values().stream().filter(grade -> grade > 0).count();
        if (relevant == 0) return null;
        double dcg = 0, reciprocal = 0;
        int hits = 0;
        for (int index = 0; index < Math.min(k, ranking.size()); index++) {
            int grade = labels.getOrDefault(ranking.get(index), 0);
            if (grade > 0) {
                hits++;
                if (reciprocal == 0) reciprocal = 1.0 / (index + 1);
            }
            dcg += (Math.pow(2, grade) - 1) / log2(index + 2);
        }
        List<Integer> ideal = labels.values().stream().sorted(Comparator.reverseOrder()).limit(k).toList();
        double idcg = 0;
        for (int index = 0; index < ideal.size(); index++) {
            idcg += (Math.pow(2, ideal.get(index)) - 1) / log2(index + 2);
        }
        return new Metrics(hits > 0 ? 1 : 0, (double) hits / relevant, reciprocal, dcg / idcg);
    }

    private static Metrics mean(List<Metrics> values) {
        List<Metrics> judged = values.stream().filter(Objects::nonNull).toList();
        if (judged.isEmpty()) return null;
        return new Metrics(judged.stream().mapToDouble(Metrics::hit).average().orElseThrow(),
                judged.stream().mapToDouble(Metrics::recall).average().orElseThrow(),
                judged.stream().mapToDouble(Metrics::mrr).average().orElseThrow(),
                judged.stream().mapToDouble(Metrics::ndcg).average().orElseThrow());
    }

    private static Metrics difference(Metrics before, Metrics after) {
        if (before == null || after == null) return null;
        return new Metrics(after.hit() - before.hit(), after.recall() - before.recall(),
                after.mrr() - before.mrr(), after.ndcg() - before.ndcg());
    }

    private static Double percentile(List<Double> sorted, double percentile) {
        return sorted.isEmpty() ? null : sorted.get((int) Math.ceil(percentile * sorted.size()) - 1);
    }

    private static double log2(double number) { return Math.log(number) / Math.log(2); }
    private static boolean validId(String value) { return value != null && value.matches("[A-Za-z0-9_-]{1,80}"); }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
