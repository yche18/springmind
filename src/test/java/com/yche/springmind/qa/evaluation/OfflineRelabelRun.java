package com.yche.springmind.qa.evaluation;

import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

/** Explicit offline-only entry point for the recorded Northstar experiment. */
class OfflineRelabelRun {
    @Test
    void relabelRecordedExperiment() throws Exception {
        Path root = Path.of("evaluation/rerank/results/northstar-2026-10-10");
        var mapper = RerankEvaluationRun.datasetMapper();
        byte[] originalBytes = Files.readAllBytes(root.resolve("snapshot.json"));
        var original = mapper.readValue(originalBytes, RerankEvaluation.Dataset.class);
        var fixture = mapper.readValue(Path.of("evaluation/rerank/sample.json").toFile(), RerankEvaluation.Dataset.class);
        var revised = OfflineRelabel.applyLabels(original, fixture);
        byte[] revisedBytes = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(revised);
        Files.write(root.resolve("snapshot-v2.json"), revisedBytes);
        List<Map<String, Object>> changes = new ArrayList<>();
        for (int index = 0; index < original.cases().size(); index++) {
            var before = original.cases().get(index);
            var after = revised.cases().get(index);
            Set<String> ids = new TreeSet<>(before.relevance().keySet());
            ids.addAll(after.relevance().keySet());
            for (String id : ids) {
                int oldGrade = before.relevance().getOrDefault(id, 0), newGrade = after.relevance().getOrDefault(id, 0);
                if (oldGrade != newGrade) changes.add(Map.of("questionId", before.id(), "evidenceId", id,
                        "before", oldGrade, "after", newGrade));
            }
        }
        for (String name : List.of("qwen-2s", "qwen-5s")) {
            byte[] reportBytes = Files.readAllBytes(root.resolve(name + ".json"));
            var originalReport = mapper.readTree(reportBytes);
            if (!hash(originalBytes).equals(originalReport.path("datasetSha256").asText())) throw new IllegalArgumentException("Original snapshot hash mismatch");
            var report = OfflineRelabel.rescore(revised, originalReport);
            report.put("sourceDatasetSha256", hash(originalBytes));
            report.put("datasetSha256", hash(revisedBytes));
            report.put("sourceReportSha256", hash(reportBytes));
            report.put("recomputedAt", Instant.now().toString());
            report.set("labelChanges", mapper.valueToTree(changes));
            ReviewApproval.apply(root, report);
            mapper.writerWithDefaultPrettyPrinter().writeValue(root.resolve(name + "-v2.json").toFile(), report);
        }
        Map<String, String> notes = mapper.readValue(Path.of("evaluation/rerank/review-notes.json").toFile(), new TypeReference<>() {});
        ReviewPacketRun.writePacket(root, fixture, revised, mapper.readTree(root.resolve("qwen-5s-v2.json").toFile()), notes);
        System.out.println("Offline regrade complete: changedLabels=" + changes.size() + "; providerRequests=0; originals preserved");
    }

    private static String hash(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
