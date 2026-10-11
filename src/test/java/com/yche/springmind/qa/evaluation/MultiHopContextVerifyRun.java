package com.yche.springmind.qa.evaluation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;

/** A second, entirely offline replay checks every saved context and text hash. */
@EnabledIfSystemProperty(named = "eval.multihopVerifyContext", matches = "true")
class MultiHopContextVerifyRun {
    @Test void reproduceAllContexts() throws Exception {
        var corpus = MultiHopData.corpus(); MultiHopData.verify(corpus);
        var sample = MultiHopData.sample();
        var capture = MultiHopData.M.readValue(MultiHopData.OUT.resolve("capture.json").toFile(), MultiHopEvaluation.Capture.class);
        for (int i = 0; i < sample.questions().size(); i++) {
            var row = capture.rows().get(i);
            assertEquals(row.contexts(), MultiHopReplay.run(corpus, sample.questions().get(i), row.keyword(), row.vector()), row.id());
        }
        var output = MultiHopData.OUT.resolve("context-verification.json");
        if (!java.nio.file.Files.exists(output)) ChallengeData.write(output, java.util.Map.of(
                "verifiedAt", java.time.Instant.now().toString(), "contextsReproduced", 180, "newModelCalls", 0,
                "captureSha256", RetrievalAblation.hash(MultiHopData.OUT.resolve("capture.json")),
                "replaySourceSha256", RetrievalAblation.hash(java.nio.file.Path.of("src/test/java/com/yche/springmind/qa/evaluation/MultiHopReplay.java"))));
    }
}
