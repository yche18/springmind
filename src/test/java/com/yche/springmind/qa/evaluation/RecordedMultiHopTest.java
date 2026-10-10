package com.yche.springmind.qa.evaluation;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

/** Saved scores remain checkable without downloads, Elasticsearch or Ollama. */
class RecordedMultiHopTest {
    @Test void reproduceSavedMetricsAndVerifyArtifacts() throws Exception {
        var mapper = MultiHopData.M;
        var capture = mapper.readValue(MultiHopData.OUT.resolve("capture.json").toFile(), MultiHopEvaluation.Capture.class);
        assertEquals(RetrievalAblation.hash(MultiHopData.ROOT.resolve("freeze.json")), capture.freezeSha256());
        assertEquals(EmbeddingComparison.models().get("BGE_M3").digest(), capture.modelDigest());
        assertEquals(0, capture.cloudCalls());
        assertEquals(26110, capture.chunkDocuments().size());
        var expected = mapper.readTree(MultiHopData.OUT.resolve("comparison.json").toFile());
        assertEquals(expected, mapper.valueToTree(MultiHopEvaluation.analyze(MultiHopData.sample(), capture)));
        var hashes = mapper.readTree(MultiHopData.OUT.resolve("artifact-hashes.json").toFile());
        for (String file : java.util.List.of("capture.json", "comparison.json", "report.html"))
            assertEquals(hashes.path(file).asText(), RetrievalAblation.hash(MultiHopData.OUT.resolve(file)));
        for (var row : capture.rows()) {
            assertEquals(50, row.vector().size());
            for (var context : row.contexts().values()) {
                assertTrue(context.tokens() <= 4096); assertTrue(context.windows() <= 5);
                assertEquals(context.chunks().size(), context.chunks().stream().distinct().count());
            }
        }
    }
    @Test void htmlEscapesUntrustedDatasetText() {
        assertEquals("&lt;script&gt;&amp;&quot;&#39;", MultiHopReportRun.esc("<script>&\"'"));
    }
}
