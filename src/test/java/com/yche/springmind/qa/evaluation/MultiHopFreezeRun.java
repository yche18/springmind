package com.yche.springmind.qa.evaluation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.time.Instant;
import java.util.Map;

@EnabledIfSystemProperty(named = "eval.multihopFreeze", matches = "true")
class MultiHopFreezeRun {
    @Test void freezeBeforeRetrieval() throws Exception {
        var corpus = MultiHopData.corpus();
        var sample = MultiHopData.prepare(corpus);
        ChallengeData.write(MultiHopData.ROOT.resolve("sample.json"), sample);
        ChallengeData.write(MultiHopData.ROOT.resolve("freeze.json"), Map.of("frozenAt", Instant.now().toString(),
                "sourceRevision", MultiHopData.REVISION, "documents", corpus.documents().size(), "chunks", corpus.chunks().size(),
                "chunksSha256", MultiHopData.sha(MultiHopData.M.writeValueAsString(corpus)), "hashes", MultiHopData.hashes()));
        MultiHopData.verify(corpus);
        System.out.println("Frozen " + sample.questions().size() + " questions; chunks=" + corpus.chunks().size()
                + "; unmapped facts=" + sample.questions().stream().flatMap(q -> q.facts().stream()).filter(f -> f.alternatives().isEmpty()).count());
    }
}
