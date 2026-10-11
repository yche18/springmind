package com.yche.springmind.qa.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;

/** A conversation-confirmed sign-off applies only to the exact reviewed dataset revision. */
final class ReviewApproval {
    private ReviewApproval() {}

    static boolean apply(Path directory, ObjectNode report) throws Exception {
        Path path = directory.resolve("review-approval.json");
        if (!Files.exists(path)) return false;
        return apply(RerankEvaluationRun.datasetMapper().readTree(path.toFile()), report);
    }

    static boolean apply(JsonNode approval, ObjectNode report) {
        if (!"HUMAN_REVIEWED".equals(approval.path("status").asText())
                || approval.path("datasetSha256").asText().isBlank()
                || approval.path("annotationRevision").asText().isBlank()
                || !approval.path("datasetSha256").equals(report.path("datasetSha256"))
                || !approval.path("annotationRevision").equals(report.path("annotationRevision"))) return false;
        report.put("annotationStatus", "HUMAN_REVIEWED");
        report.set("humanReview", approval.deepCopy());
        return true;
    }
}
