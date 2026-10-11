package com.yche.springmind.qa.evaluation;

import com.yche.springmind.qa.rag.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static com.yche.springmind.qa.evaluation.RetrievalAblation.require;

/** Explicit paid reranking only; all candidate construction and selection is offline. */
@EnabledIfSystemProperty(named = "eval.multihopRerank", matches = "true")
class LiveMultiHopRerankRun {
    static final Path CACHE = Path.of("target/multihop-rerank-cache");
    @Test void collect() throws Exception {
        require(Boolean.getBoolean("eval.confirmExternal"), "Explicit permission to send public candidate text externally is required");
        require(MultiHopRerank.URL.equals(System.getenv("RERANK_URL")) && MultiHopRerank.MODEL.equals(System.getenv("RERANK_MODEL")), "Endpoint/model differs from frozen protocol");
        String key = System.getenv("RERANK_API_KEY"); require(key != null && !key.isBlank(), "Rerank credential missing");
        MultiHopRerank.verify();
        require(!Files.exists(MultiHopRerank.OUT.resolve("capture.json")), "Existing completed capture is immutable");
        var corpus = MultiHopData.corpus(); MultiHopData.verify(corpus);
        var sample = MultiHopData.sample(); var baseline = MultiHopRerank.baseline(); var inputs = MultiHopRerank.inputs();
        Files.createDirectories(CACHE); Files.createDirectories(MultiHopRerank.OUT);
        String freeze = RetrievalAblation.hash(MultiHopRerank.ROOT.resolve("freeze.json"));
        if (!Files.exists(CACHE.resolve("freeze.txt"))) Files.writeString(CACHE.resolve("freeze.txt"), freeze, StandardOpenOption.CREATE_NEW);
        require(Files.readString(CACHE.resolve("freeze.txt")).equals(freeze), "Foreign provider cache");
        var props = new EvidenceSelectionProperties(4096, 30, true, MultiHopRerank.URL, key, MultiHopRerank.MODEL, Duration.ofSeconds(10));
        List<MultiHopRerank.Row> rows = new ArrayList<>(); int[] cursor = {0}, consecutiveFailures = {0};
        try (var provider = new HttpEvidenceReranker(props, MultiHopData.M)) {
            for (int i = 0; i < sample.questions().size(); i++) {
                var q = sample.questions().get(i); var before = baseline.rows().get(i);
                Map<String, MultiHopRerank.Outcome> outcomes = new LinkedHashMap<>();
                var contexts = MultiHopReplay.run(corpus, q, before.keyword(), before.vector(), (query, docs) -> {
                    try {
                        var expected = inputs.get(cursor[0]++);
                        require(expected.equals(MultiHopRerank.input(q.id(), expected.mode(), query, docs)), "Frozen candidate input mismatch");
                        String name = q.id() + "-" + expected.mode();
                        Path saved = CACHE.resolve(name + ".json"), intent = CACHE.resolve(name + ".intent.json");
                        MultiHopRerank.Outcome outcome;
                        if (Files.exists(saved)) {
                            outcome = MultiHopData.M.readValue(saved.toFile(), MultiHopRerank.Outcome.class);
                        } else {
                            require(consecutiveFailures[0] < 3, "Three consecutive provider fallbacks; stop and investigate without automatic retries");
                            require(!Files.exists(intent), "Ambiguous prior attempt; do not repeat paid request: " + name);
                            if (docs.size() >= 2) {
                                require(intentCount() < 180, "Provider attempt limit exceeded");
                                ChallengeData.write(intent, Map.of("startedAt", Instant.now().toString(), "freezeSha256", freeze, "requestSha256", expected.requestSha256()));
                            }
                            long start = System.nanoTime();
                            var ranked = provider.rerank(query, docs);
                            long elapsed = (System.nanoTime() - start) / 1_000_000;
                            Map<String, Double> scores = new LinkedHashMap<>();
                            if (ranked.status().equals("APPLIED")) for (var d : ranked.documents())
                                scores.put(d.getId(), ((Number) d.getMetadata().get("rerankScore")).doubleValue());
                            outcome = new MultiHopRerank.Outcome(q.id(), expected.mode(), expected.requestSha256(), ranked.status(), elapsed,
                                    ranked.documents().stream().map(org.springframework.ai.document.Document::getId).toList(), scores);
                            MultiHopRerank.validate(expected, outcome);
                            ChallengeData.write(saved, outcome);
                        }
                        MultiHopRerank.validate(expected, outcome);
                        if (outcome.status().equals("FALLBACK")) consecutiveFailures[0]++; else consecutiveFailures[0] = 0;
                        outcomes.put(expected.mode(), outcome);
                        System.out.println("Rerank " + cursor[0] + "/180 " + name + " " + outcome.status() + " " + outcome.elapsedMs() + "ms");
                        return MultiHopRerank.replay(outcome, docs);
                    } catch (Exception e) { throw new IllegalStateException(e); }
                });
                rows.add(new MultiHopRerank.Row(q.id(), outcomes, contexts));
            }
        }
        require(cursor[0] == 180, "Missing arms"); MultiHopRerank.verify();
        Map<String, String> ledger = new TreeMap<>();
        try (var files = Files.list(CACHE)) { for (var path : files.sorted().toList()) ledger.put(path.getFileName().toString(), RetrievalAblation.hash(path)); }
        ChallengeData.write(MultiHopRerank.OUT.resolve("request-ledger-hashes.json"), ledger);
        ChallengeData.write(MultiHopRerank.OUT.resolve("capture.json"), new MultiHopRerank.Capture(Instant.now().toString(), freeze, intentCount(), 0, rows));
    }
    static int intentCount() throws Exception {
        try (var files = Files.list(CACHE)) { return (int) files.filter(p -> p.getFileName().toString().endsWith(".intent.json")).count(); }
    }
}
