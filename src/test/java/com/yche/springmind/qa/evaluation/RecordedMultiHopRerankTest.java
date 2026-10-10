package com.yche.springmind.qa.evaluation;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RecordedMultiHopRerankTest {
    @Test void reproduceEveryMetricWithoutProviderOrRawNews() throws Exception {
        var m = MultiHopData.M;
        var capture = m.readValue(MultiHopRerank.OUT.resolve("capture.json").toFile(), MultiHopRerank.Capture.class);
        assertEquals(0, capture.generationCalls()); assertEquals(180, capture.providerAttempts());
        var computed = MultiHopRerankAnalysis.analyze(capture);
        assertEquals(m.readTree(MultiHopRerank.OUT.resolve("comparison.json").toFile()), m.valueToTree(computed));
        var verification = m.readTree(MultiHopRerank.OUT.resolve("verification.json").toFile());
        assertEquals(180, verification.path("contextsReproduced").asInt());
        assertEquals(0, verification.path("newProviderCalls").asInt());
        for (String file : List.of("capture.json", "comparison.json", "report.html", "request-ledger-hashes.json"))
            assertEquals(verification.path("hashes").path(file).asText(), RetrievalAblation.hash(MultiHopRerank.OUT.resolve(file)));
        var original = m.readTree(MultiHopData.OUT.resolve("comparison.json").toFile());
        for (String split : List.of("FORMAL", "CALIBRATION")) for (String mode : MultiHopRerank.MODES) {
            var summary = computed.groups().get(split).get(mode);
            assertEquals(original.path("groups").path(split).path(mode + "_CONTEXT").path("factRecall").asDouble(), summary.recallBefore(), 1e-12);
            assertEquals(summary.questions(), summary.applied() + summary.fallback() + summary.skipped());
            assertEquals(summary.answerable(), summary.improved() + summary.tied() + summary.worsened());
        }
    }
    @Test void reportIncludesAllQuestionsAndDoesNotClaimAnswerAccuracy() throws Exception {
        String html = Files.readString(MultiHopRerank.OUT.resolve("report.html"));
        for (var q : MultiHopData.sample().questions()) assertTrue(html.contains("id=\"" + q.id() + "\""));
        assertTrue(html.contains("未生成答案")); assertTrue(html.contains("不是答案准确率"));
        assertTrue(html.contains("用户部分抽查"));
    }
}
