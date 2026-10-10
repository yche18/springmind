package com.yche.springmind.qa.evaluation;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static com.yche.springmind.qa.evaluation.RetrievalAblation.require;

/** Offline reports and review packets; never opens a network connection or loads a Spring context. */
class ExtensionReportRun {
    @Test
    void generate() throws Exception {
        var root = Path.of(System.getProperty("eval.extensionOutput", "target/retrieval-extension"));
        var mapper = RerankEvaluationRun.datasetMapper();
        var fixture = ExtensionFixture.read();
        require(RetrievalAblation.hash(ExtensionFixture.PATH).equals(ExtensionEvaluation.FIXTURE_HASH), "Frozen fixture changed");
        var corpus = mapper.readValue(root.resolve("corpus.json").toFile(), ExtensionEvaluation.Corpus.class);
        var capture = mapper.readValue(root.resolve("capture.json").toFile(), ExtensionEvaluation.Capture.class);
        var comparison = ExtensionEvaluation.compare(fixture, corpus, capture, RetrievalAblation.hash(root.resolve("corpus.json")),
                RetrievalAblation.hash(root.resolve("capture.json")));
        mapper.writerWithDefaultPrettyPrinter().writeValue(root.resolve("comparison.json").toFile(), comparison);
        Files.writeString(root.resolve("review.html"), ExtensionReport.review(fixture, corpus));
        Files.writeString(root.resolve("report.html"), ExtensionReport.report(fixture, comparison, capture));
        List<Map<String, Object>> template = new ArrayList<>();
        for (var question : fixture.cases()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", question.id());
            item.put("status", "PENDING_HUMAN_REVIEW");
            item.put("fixtureSha256", corpus.fixtureSha256());
            item.put("corpusSha256", comparison.corpusSha256());
            item.put("expected", question.expected());
            item.put("evidence", question.evidence());
            item.put("chunkGrades", corpus.grades().get(question.id()));
            item.put("reviewerNotes", "");
            template.add(item);
        }
        mapper.writerWithDefaultPrettyPrinter().writeValue(root.resolve("review-template.json").toFile(), template);
        System.out.println("Extension reports generated: 24 pending reviews; newProviderRequests=0; " + root.toAbsolutePath());
    }
}
