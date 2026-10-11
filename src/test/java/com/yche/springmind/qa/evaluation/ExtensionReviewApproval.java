package com.yche.springmind.qa.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.*;
import static com.yche.springmind.qa.evaluation.RetrievalAblation.require;

/** Sign-off is separate from the immutable pre-query corpus and binds its exact bytes. */
final class ExtensionReviewApproval {
    private ExtensionReviewApproval() {}

    static ExtensionEvaluation.Comparison apply(Path root, ExtensionEvaluation.Corpus corpus,
                                               ExtensionEvaluation.Comparison comparison) throws Exception {
        Path path = root.resolve("review-approval.json");
        if (!Files.exists(path)) return comparison;
        return apply(RerankEvaluationRun.datasetMapper().readTree(path.toFile()), corpus, comparison);
    }

    static ExtensionEvaluation.Comparison apply(JsonNode approval, ExtensionEvaluation.Corpus corpus,
                                               ExtensionEvaluation.Comparison comparison) {
        require("HUMAN_REVIEWED".equals(approval.path("status").asText()), "Invalid review status");
        require(comparison.fixtureSha256().equals(approval.path("fixtureSha256").asText())
                && comparison.corpusSha256().equals(approval.path("corpusSha256").asText()), "Review hash mismatch");
        require(approval.path("reviewedCases").asInt() == comparison.rows().size()
                && approval.path("reviewedChunkGrades").asInt() == corpus.grades().values().stream().mapToInt(java.util.Map::size).sum(),
                "Review scope mismatch");
        require(!approval.path("confirmationText").asText().isBlank()
                && !approval.path("reviewer").asText().isBlank(), "Missing review confirmation");
        return new ExtensionEvaluation.Comparison("HUMAN_REVIEWED", comparison.fixtureSha256(), comparison.corpusSha256(),
                comparison.captureSha256(), comparison.newProviderRequests(), comparison.groups(), comparison.rows());
    }
}
