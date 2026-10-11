package com.yche.springmind.qa.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;

/** Deterministic offline arithmetic and HTML; neither Spring nor model clients are started. */
class RetrievalAblationReportRun {
    @Test
    void report() throws Exception {
        Path root = Path.of(System.getProperty("eval.ablationDir", "target/retrieval-ablation"));
        Path capturePath = root.resolve("capture.json");
        var mapper = RerankEvaluationRun.datasetMapper();
        var capture = mapper.readValue(capturePath.toFile(), RetrievalAblation.Capture.class);
        Path snapshotPath = RetrievalAblation.SOURCE.resolve("snapshot-v2.json");
        RetrievalAblation.require(capture.datasetSha256().equals(RetrievalAblation.hash(snapshotPath)), "Reviewed dataset hash changed");
        var dataset = mapper.readValue(snapshotPath.toFile(), RerankEvaluation.Dataset.class);
        Map<String, JsonNode> reports = new LinkedHashMap<>();
        Map<String, String> hashes = new LinkedHashMap<>();
        for (String timeout : List.of("2s", "5s")) {
            Path path = RetrievalAblation.SOURCE.resolve("qwen-" + timeout + "-v2.json");
            String mode = "QWEN_" + timeout.toUpperCase(Locale.ROOT) + "_REUSED";
            reports.put(mode, mapper.readTree(path.toFile()));
            hashes.put(mode, RetrievalAblation.hash(path));
        }
        var comparison = RetrievalAblation.compare(dataset, capture, RetrievalAblation.hash(capturePath), reports, hashes);
        mapper.writerWithDefaultPrettyPrinter().writeValue(root.resolve("comparison.json").toFile(), comparison);
        Files.writeString(root.resolve("report.html"), RetrievalAblationReport.render(comparison));
        System.out.println("Offline ablation report generated: " + root.toAbsolutePath() + "; newProviderRequests=0");
    }
}
