package com.yche.springmind.qa.evaluation;

import java.util.*;
import static com.yche.springmind.qa.evaluation.RetrievalAblation.require;

/** Chunk-level ranking and distinct-fact coverage are kept separate from window assembly. */
final class ExtensionEvaluation {
    static final String FIXTURE_HASH = "279421dc7e5339e929111e92c075ec12caea8ae33fbfd335b9249a58e7cc5a6c";
    static final List<String> MODES = List.of("KEYWORD", "VECTOR", "HYBRID");
    record Chunk(long chunkId, long documentId, String document, int index, String text) {}
    record Corpus(String fixtureSha256, String frozenAt, String annotationStatus, long groupId,
                  Map<String, Chunk> chunks, Map<String, Map<String, Integer>> grades) {}
    record Hit(String chunk, double score) {}
    record Window(String document, List<String> chunks, String text) {}
    record CapturedRow(String id, List<Hit> vector, List<Hit> keyword, Map<String, List<String>> orders,
                       Map<String, List<Window>> candidateWindows, Map<String, List<Window>> selectedWindows,
                       double vectorMs, double keywordMs) {}
    record Capture(String fixtureSha256, String corpusSha256, String completedAt, String embeddingModel,
                   String embeddingDigest, int channelTopK, int candidateLimit, int rrfConstant,
                   int evidenceTokenBudget, int neighborWindow, int newProviderRequests, List<CapturedRow> rows) {}
    record Score(RerankEvaluation.Metrics chunkMetrics, Double candidateChunkRecall, Double rawChannelFactCoverage,
                 Double candidateFactCoverage, Double top5FactCoverage, Boolean allFactsTop5,
                 Double windowTop5FactCoverage, Double selectedFactCoverage) {}
    record Row(String id, String category, String language, Map<String, Score> scores) {}
    record Summary(int cases, int answerable, int unanswerable, RerankEvaluation.Metrics chunkMetrics,
                   Double candidateChunkRecall, Double rawChannelFactCoverage, Double candidateFactCoverage,
                   Double top5FactCoverage, int complementaryCases, int completeComplementaryTop5,
                   Double windowTop5FactCoverage, Double selectedFactCoverage) {}
    record Comparison(String annotationStatus, String fixtureSha256, String corpusSha256, String captureSha256,
                      int newProviderRequests, Map<String, Map<String, Summary>> groups, List<Row> rows) {}

    private ExtensionEvaluation() {}

    static Set<String> facts(ExtensionFixture.Case question, String document, String text) {
        Set<String> facts = new LinkedHashSet<>();
        for (var evidence : question.evidence()) if (evidence.document().equals(document)
                && ExtensionFixture.normalize(text).contains(ExtensionFixture.normalize(evidence.quote()))) facts.add(evidence.id());
        return facts;
    }

    static Map<String, Map<String, Integer>> project(ExtensionFixture fixture, Map<String, Chunk> chunks) {
        Map<String, Map<String, Integer>> judgments = new LinkedHashMap<>();
        for (var question : fixture.cases()) {
            Map<String, Integer> grades = new LinkedHashMap<>();
            Set<String> found = new HashSet<>();
            for (var entry : chunks.entrySet()) {
                Set<String> facts = facts(question, entry.getValue().document(), entry.getValue().text());
                found.addAll(facts);
                grades.put(entry.getKey(), facts.isEmpty() ? 0 : facts.size() == question.evidence().size() ? 3 : 2);
            }
            require(found.size() == question.evidence().size(), "A required quote was split or lost during ingestion: " + question.id());
            judgments.put(question.id(), grades);
        }
        return judgments;
    }

    static Map<String, List<String>> orders(List<Hit> vector, List<Hit> keyword, Map<String, Chunk> chunks) {
        Map<String, Double> fused = new HashMap<>();
        for (List<Hit> channel : List.of(vector, keyword)) {
            require(channel.size() <= 50 && channel.stream().map(Hit::chunk).distinct().count() == channel.size(), "Invalid channel size/duplicates");
            for (int i = 0; i < channel.size(); i++) {
                var hit = channel.get(i);
                require(chunks.containsKey(hit.chunk()) && Double.isFinite(hit.score()), "Foreign or invalid raw hit");
                fused.merge(hit.chunk(), 1.0 / (60 + i + 1), Double::sum);
            }
        }
        Map<String, List<String>> orders = new LinkedHashMap<>();
        orders.put("KEYWORD", keyword.stream().limit(30).map(Hit::chunk).toList());
        orders.put("VECTOR", vector.stream().limit(30).map(Hit::chunk).toList());
        orders.put("HYBRID", fused.keySet().stream().sorted(Comparator.<String>comparingDouble(fused::get).reversed()
                .thenComparingLong(id -> chunks.get(id).chunkId())).limit(30).toList());
        return orders;
    }

    static Comparison compare(ExtensionFixture fixture, Corpus corpus, Capture capture, String corpusHash, String captureHash) {
        require(FIXTURE_HASH.equals(corpus.fixtureSha256()) && corpus.fixtureSha256().equals(capture.fixtureSha256())
                && capture.corpusSha256().equals(corpusHash), "Frozen input hash mismatch");
        require(corpus.annotationStatus().equals("PENDING_HUMAN_REVIEW") && corpus.chunks().size() > 50,
                "This extension requires a provisional corpus larger than the channel limit");
        require(capture.rows().size() == fixture.cases().size() && capture.newProviderRequests() == 0
                && capture.channelTopK() == 50 && capture.candidateLimit() == 30 && capture.rrfConstant() == 60
                && capture.evidenceTokenBudget() == 4096 && capture.neighborWindow() == 1, "Capture protocol changed");
        require(project(fixture, corpus.chunks()).equals(corpus.grades()), "Frozen quote projection changed");
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < fixture.cases().size(); i++) {
            var question = fixture.cases().get(i);
            var captured = capture.rows().get(i);
            require(question.id().equals(captured.id()) && orders(captured.vector(), captured.keyword(), corpus.chunks()).equals(captured.orders()),
                    "Case identity or channel/RRF rank mismatch");
            require(captured.candidateWindows().keySet().equals(new HashSet<>(MODES))
                    && captured.selectedWindows().keySet().equals(new HashSet<>(MODES)), "Missing window mode");
            Map<String, Score> scores = new LinkedHashMap<>();
            for (String mode : MODES) {
                var ranking = captured.orders().get(mode);
                var grades = corpus.grades().get(question.id());
                var metrics = RerankEvaluation.metrics(ranking, grades, 5);
                var pool = RerankEvaluation.metrics(ranking, grades, 30);
                Set<String> raw = new LinkedHashSet<>();
                if (!mode.equals("KEYWORD")) captured.vector().forEach(hit -> raw.add(hit.chunk()));
                if (!mode.equals("VECTOR")) captured.keyword().forEach(hit -> raw.add(hit.chunk()));
                var top = ranking.stream().limit(5).toList();
                Double coverage = chunkCoverage(question, top, corpus.chunks());
                scores.put(mode, new Score(metrics, pool == null ? null : pool.recall(),
                        chunkCoverage(question, raw, corpus.chunks()), chunkCoverage(question, ranking, corpus.chunks()), coverage,
                        question.evidence().size() <= 1 ? null : coverage == 1.0,
                        windowCoverage(question, captured.candidateWindows().get(mode).stream().limit(5).toList(), corpus),
                        windowCoverage(question, captured.selectedWindows().get(mode), corpus)));
            }
            rows.add(new Row(question.id(), question.category(), question.language(), scores));
        }
        Map<String, Map<String, Summary>> groups = new LinkedHashMap<>();
        groups.put("ALL", summarize(rows));
        for (String language : List.of("zh", "en")) groups.put(language, summarize(rows.stream().filter(row -> row.language().equals(language)).toList()));
        for (String category : List.of("exact", "semantic", "cross-language", "multi-evidence", "long-document", "unanswerable"))
            groups.put(category, summarize(rows.stream().filter(row -> row.category().equals(category)).toList()));
        return new Comparison("PENDING_HUMAN_REVIEW", corpus.fixtureSha256(), corpusHash, captureHash, 0, groups, rows);
    }

    static Double chunkCoverage(ExtensionFixture.Case question, Collection<String> ids, Map<String, Chunk> chunks) {
        if (question.evidence().isEmpty()) return null;
        Set<String> found = new HashSet<>();
        for (String id : ids) {
            require(chunks.containsKey(id), "Unknown fact-coverage chunk");
            var chunk = chunks.get(id);
            found.addAll(facts(question, chunk.document(), chunk.text()));
        }
        return (double) found.size() / question.evidence().size();
    }

    private static Double windowCoverage(ExtensionFixture.Case question, List<Window> windows, Corpus corpus) {
        Set<String> found = new HashSet<>();
        for (var window : windows) {
            require(!window.chunks().isEmpty(), "Empty window");
            for (String id : window.chunks()) {
                var chunk = corpus.chunks().get(id);
                require(chunk != null && window.document().equals(chunk.document())
                        && ExtensionFixture.normalize(window.text()).contains(ExtensionFixture.normalize(chunk.text())), "Window text/identity mismatch");
            }
            found.addAll(facts(question, window.document(), window.text()));
        }
        return question.evidence().isEmpty() ? null : (double) found.size() / question.evidence().size();
    }

    private static Map<String, Summary> summarize(List<Row> rows) {
        Map<String, Summary> summaries = new LinkedHashMap<>();
        for (String mode : MODES) {
            var values = rows.stream().map(row -> row.scores().get(mode)).toList();
            var judged = values.stream().map(Score::chunkMetrics).filter(Objects::nonNull).toList();
            var multi = values.stream().map(Score::allFactsTop5).filter(Objects::nonNull).toList();
            var metrics = judged.isEmpty() ? null : new RerankEvaluation.Metrics(
                    judged.stream().mapToDouble(RerankEvaluation.Metrics::hit).average().orElseThrow(),
                    judged.stream().mapToDouble(RerankEvaluation.Metrics::recall).average().orElseThrow(),
                    judged.stream().mapToDouble(RerankEvaluation.Metrics::mrr).average().orElseThrow(),
                    judged.stream().mapToDouble(RerankEvaluation.Metrics::ndcg).average().orElseThrow());
            summaries.put(mode, new Summary(rows.size(), judged.size(), rows.size() - judged.size(), metrics,
                    mean(values.stream().map(Score::candidateChunkRecall).toList()), mean(values.stream().map(Score::rawChannelFactCoverage).toList()),
                    mean(values.stream().map(Score::candidateFactCoverage).toList()), mean(values.stream().map(Score::top5FactCoverage).toList()),
                    multi.size(), (int) multi.stream().filter(Boolean::booleanValue).count(),
                    mean(values.stream().map(Score::windowTop5FactCoverage).toList()), mean(values.stream().map(Score::selectedFactCoverage).toList())));
        }
        return summaries;
    }

    private static Double mean(List<Double> values) {
        return values.stream().filter(Objects::nonNull).mapToDouble(Double::doubleValue).average().stream().boxed().findFirst().orElse(null);
    }
}
