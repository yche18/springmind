package com.yche.springmind.qa.evaluation;

import com.yche.springmind.qa.rag.EvidenceReranker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.util.*;
import java.time.Instant;
import static com.yche.springmind.qa.evaluation.RetrievalAblation.require;

@EnabledIfSystemProperty(named = "eval.multihopRerankFreeze", matches = "true")
class MultiHopRerankFreezeRun {
    @Test void freezeWithoutProviderCalls() throws Exception {
        var corpus = MultiHopData.corpus(); MultiHopData.verify(corpus);
        var sample = MultiHopData.sample(); var baseline = MultiHopRerank.baseline();
        List<MultiHopRerank.Input> inputs = new ArrayList<>();
        for (int i = 0; i < sample.questions().size(); i++) {
            var q = sample.questions().get(i); var row = baseline.rows().get(i); int[] arm = {0};
            var contexts = MultiHopReplay.run(corpus, q, row.keyword(), row.vector(), (query, docs) -> {
                try { inputs.add(MultiHopRerank.input(q.id(), MultiHopRerank.MODES.get(arm[0]++), query, docs)); }
                catch (Exception e) { throw new IllegalStateException(e); }
                return new EvidenceReranker.Result(docs, "DISABLED");
            });
            require(contexts.equals(row.contexts()), "Baseline drift before paid evaluation: " + q.id());
        }
        ChallengeData.write(MultiHopRerank.ROOT.resolve("inputs.json"), inputs);
        long requests = inputs.stream().filter(in -> in.candidates().size() >= 2).count();
        int tokens = inputs.stream().mapToInt(MultiHopRerank.Input::estimatedTokens).sum();
        ChallengeData.write(MultiHopRerank.ROOT.resolve("freeze.json"), Map.of("frozenAt", Instant.now().toString(),
                "hashes", MultiHopRerank.hashes(), "plannedProviderRequests", requests, "estimatedInputTokensNotBilled", tokens,
                "model", MultiHopRerank.MODEL, "endpoint", MultiHopRerank.URL, "timeoutMs", 10000, "generationCalls", 0));
        MultiHopRerank.verify();
        System.out.println("Frozen inputs=" + inputs.size() + ", requests=" + requests + ", estimated input tokens=" + tokens
                + ", clipped windows=" + inputs.stream().flatMap(in -> in.candidates().stream()).filter(MultiHopRerank.Candidate::clipped).count());
    }
}
