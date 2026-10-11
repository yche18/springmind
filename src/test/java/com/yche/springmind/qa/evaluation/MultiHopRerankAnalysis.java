package com.yche.springmind.qa.evaluation;

import java.util.*;
import static com.yche.springmind.qa.evaluation.RetrievalAblation.require;

/** Paired metrics on frozen windows, distinct from raw-chunk retrieval metrics. */
final class MultiHopRerankAnalysis {
    record Pair(MultiHopEvaluation.Score before, MultiHopEvaluation.Score after, MultiHopEvaluation.Score available,
                MultiHopEvaluation.Score top5Before, MultiHopEvaluation.Score top5After,
                List<String> gainedFacts, List<String> lostFacts, String status, long elapsedMs,
                int candidates, int clipped, int beforeTokens, int afterTokens) {}
    record Row(String id, String split, String type, Map<String, Pair> modes) {}
    record Summary(int questions, int answerable, Double recallBefore, Double recallAfter, int completeBefore, int completeAfter,
                   Double candidateRecall, int candidateComplete, Double top5RecallBefore, Double top5RecallAfter,
                   int improved, int tied, int worsened, int applied, int fallback, int skipped,
                   long p50Ms, long p95Ms, int clippedWindows, double meanTokensBefore, double meanTokensAfter) {}
    record Report(String annotationStatus, int providerAttempts, int generationCalls, String endpoint, String model,
                  int timeoutMs, Map<String, Map<String, Summary>> groups, List<Row> rows) {}

    static Set<Long> windowChunks(MultiHopRerank.Input input, List<String> order, int limit) {
        Map<String, MultiHopRerank.Candidate> candidates = new HashMap<>(); input.candidates().forEach(c -> candidates.put(c.id(), c));
        Set<Long> ids = new LinkedHashSet<>();
        order.stream().limit(limit).forEach(id -> ids.addAll(candidates.get(id).chunks())); return ids;
    }
    static Report analyze(MultiHopRerank.Capture capture) throws Exception {
        MultiHopRerank.verify();
        require(capture.freezeSha256().equals(RetrievalAblation.hash(MultiHopRerank.ROOT.resolve("freeze.json")))
                && capture.generationCalls() == 0 && capture.providerAttempts() == 180 && capture.rows().size() == 60, "Incomplete/incompatible rerank capture");
        var sample = MultiHopData.sample(); var baseline = MultiHopRerank.baseline(); var inputs = MultiHopRerank.inputs();
        List<Row> rows = new ArrayList<>(); int cursor = 0;
        for (int i = 0; i < sample.questions().size(); i++) {
            var q = sample.questions().get(i); var saved = capture.rows().get(i); var old = baseline.rows().get(i);
            require(saved.id().equals(q.id()) && old.id().equals(q.id()), "Question order changed");
            require(saved.outcomes().keySet().equals(new HashSet<>(MultiHopRerank.MODES))
                    && saved.contexts().keySet().equals(new HashSet<>(MultiHopRerank.MODES)), "Missing mode");
            Map<String, Pair> modes = new LinkedHashMap<>();
            for (String mode : MultiHopRerank.MODES) {
                var input = inputs.get(cursor++); var outcome = saved.outcomes().get(mode);
                require(input.id().equals(q.id()) && input.mode().equals(mode), "Input order changed");
                MultiHopRerank.validate(input, outcome);
                var before = old.contexts().get(mode); var after = saved.contexts().get(mode);
                var order = input.candidates().stream().map(MultiHopRerank.Candidate::id).toList();
                var pool = windowChunks(input, order, order.size());
                require(pool.containsAll(before.chunks()) && pool.containsAll(after.chunks()), "Context introduced foreign evidence");
                require(after.windows() <= 5 && after.tokens() <= 4096 && after.tokens() >= 0, "Invalid context budget");
                if (!outcome.status().equals("APPLIED")) require(before.equals(after), "Fallback changed context");
                var foundBefore = MultiHopEvaluation.facts(q, before.chunks()); var foundAfter = MultiHopEvaluation.facts(q, after.chunks());
                Set<String> gained = new LinkedHashSet<>(foundAfter); gained.removeAll(foundBefore);
                Set<String> lost = new LinkedHashSet<>(foundBefore); lost.removeAll(foundAfter);
                modes.put(mode, new Pair(MultiHopEvaluation.score(q, before.chunks(), baseline.chunkDocuments()),
                        MultiHopEvaluation.score(q, after.chunks(), baseline.chunkDocuments()), MultiHopEvaluation.score(q, pool, baseline.chunkDocuments()),
                        MultiHopEvaluation.score(q, windowChunks(input, order, 5), baseline.chunkDocuments()),
                        MultiHopEvaluation.score(q, windowChunks(input, outcome.order(), 5), baseline.chunkDocuments()),
                        List.copyOf(gained), List.copyOf(lost), outcome.status(), outcome.elapsedMs(), order.size(),
                        (int) input.candidates().stream().filter(MultiHopRerank.Candidate::clipped).count(), before.tokens(), after.tokens()));
            }
            rows.add(new Row(q.id(), q.split(), q.type(), modes));
        }
        Map<String, Map<String, Summary>> groups = new LinkedHashMap<>();
        for (String split : List.of("FORMAL", "CALIBRATION")) {
            groups.put(split, summarize(rows.stream().filter(r -> r.split().equals(split)).toList()));
            for (String type : List.of("inference_query", "comparison_query", "temporal_query", "null_query"))
                groups.put(split + "/" + type, summarize(rows.stream().filter(r -> r.split().equals(split) && r.type().equals(type)).toList()));
        }
        return new Report("OFFICIAL_REFERENCE_USER_SPOT_CHECKED", capture.providerAttempts(), 0, MultiHopRerank.URL, MultiHopRerank.MODEL, 10000, groups, rows);
    }
    static Map<String, Summary> summarize(List<Row> rows) {
        Map<String, Summary> summaries = new LinkedHashMap<>();
        for (String mode : MultiHopRerank.MODES) {
            var all = rows.stream().map(r -> r.modes().get(mode)).toList();
            var judged = all.stream().filter(p -> p.before().factRecall() != null).toList();
            int improved = (int) judged.stream().filter(p -> p.after().factRecall() > p.before().factRecall()).count();
            int worse = (int) judged.stream().filter(p -> p.after().factRecall() < p.before().factRecall()).count();
            summaries.put(mode, new Summary(all.size(), judged.size(), average(judged, p -> p.before().factRecall()), average(judged, p -> p.after().factRecall()),
                    (int) judged.stream().filter(p -> p.before().allFacts()).count(), (int) judged.stream().filter(p -> p.after().allFacts()).count(),
                    average(judged, p -> p.available().factRecall()), (int) judged.stream().filter(p -> p.available().allFacts()).count(),
                    average(judged, p -> p.top5Before().factRecall()), average(judged, p -> p.top5After().factRecall()), improved, judged.size() - improved - worse, worse,
                    (int) all.stream().filter(p -> p.status().equals("APPLIED")).count(), (int) all.stream().filter(p -> p.status().equals("FALLBACK")).count(),
                    (int) all.stream().filter(p -> p.status().equals("SKIPPED")).count(), percentile(all, .5), percentile(all, .95),
                    all.stream().mapToInt(Pair::clipped).sum(), all.stream().mapToInt(Pair::beforeTokens).average().orElse(0),
                    all.stream().mapToInt(Pair::afterTokens).average().orElse(0)));
        }
        return summaries;
    }
    static Double average(List<Pair> pairs, java.util.function.ToDoubleFunction<Pair> value) {
        return pairs.isEmpty() ? null : pairs.stream().mapToDouble(value).average().orElseThrow();
    }
    static long percentile(List<Pair> pairs, double fraction) {
        var values = pairs.stream().map(Pair::elapsedMs).sorted().toList(); return values.get(Math.max(0, (int) Math.ceil(values.size() * fraction) - 1));
    }
}
