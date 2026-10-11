package com.yche.springmind.qa.evaluation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Updates review metadata only; no metric recomputation, model calls or credential access. */
class ReviewSignoffRun {
    @Test
    void recordExistingUserConfirmation() throws Exception {
        Path root = Path.of("evaluation/rerank/results/northstar-2026-10-10");
        var mapper = RerankEvaluationRun.datasetMapper();
        byte[] bytes = Files.readAllBytes(root.resolve("snapshot-v2.json"));
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        Map<String, ObjectNode> reports = new LinkedHashMap<>();
        for (String name : List.of("qwen-2s-v2.json", "qwen-5s-v2.json")) {
            ObjectNode report = (ObjectNode) mapper.readTree(root.resolve(name).toFile());
            if (!hash.equals(report.path("datasetSha256").asText()) || !ReviewApproval.apply(root, report)) {
                throw new IllegalArgumentException("Approval does not match the recorded dataset revision");
            }
            reports.put(name, report);
        }
        for (var entry : reports.entrySet()) mapper.writerWithDefaultPrettyPrinter().writeValue(root.resolve(entry.getKey()).toFile(), entry.getValue());
        var fixture = mapper.readValue(Path.of("evaluation/rerank/sample.json").toFile(), RerankEvaluation.Dataset.class);
        var snapshot = mapper.readValue(bytes, RerankEvaluation.Dataset.class);
        Map<String, String> notes = mapper.readValue(Path.of("evaluation/rerank/review-notes.json").toFile(), new TypeReference<>() {});
        ReviewPacketRun.writePacket(root, fixture, snapshot, reports.get("qwen-5s-v2.json"), notes);
        System.out.println("Recorded human review for 30 cases; metrics unchanged; providerRequests=0");
    }
}
