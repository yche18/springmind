package com.yche.springmind.qa.evaluation;

import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** No provider calls: generates review materials from one already completed capture/replay. */
class ReviewPacketRun {
    @Test
    void createPacket() throws Exception {
        String captureArgument = System.getProperty("eval.captureDir");
        String reportArgument = System.getProperty("eval.report");
        if (captureArgument == null || reportArgument == null) throw new IllegalArgumentException("Set eval.captureDir and eval.report");
        Path capture = Path.of(captureArgument);
        Path reportPath = Path.of(reportArgument);
        var mapper = RerankEvaluationRun.datasetMapper();
        byte[] snapshotBytes = Files.readAllBytes(capture.resolve("snapshot.json"));
        var snapshot = mapper.readValue(snapshotBytes, RerankEvaluation.Dataset.class);
        byte[] fixtureBytes = Files.readAllBytes(Path.of("evaluation/rerank/sample.json"));
        var fixture = mapper.readValue(fixtureBytes, RerankEvaluation.Dataset.class);
        var manifest = mapper.readTree(capture.resolve("manifest.json").toFile());
        String fixtureHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(fixtureBytes));
        if (!fixtureHash.equals(manifest.path("fixtureSha256").asText())) throw new IllegalArgumentException("Fixture changed since capture");
        var report = mapper.readTree(reportPath.toFile());
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(snapshotBytes));
        if (!hash.equals(report.path("datasetSha256").asText())) throw new IllegalArgumentException("Report does not match captured snapshot");
        Map<String, String> notes = mapper.readValue(Path.of("evaluation/rerank/review-notes.json").toFile(), new TypeReference<>() {});
        writePacket(capture, fixture, snapshot, report, notes);
    }

    static void writePacket(Path capture, RerankEvaluation.Dataset fixture, RerankEvaluation.Dataset snapshot,
            com.fasterxml.jackson.databind.JsonNode report, Map<String, String> notes) throws Exception {
        var mapper = RerankEvaluationRun.datasetMapper();
        String html = AnnotationReview.render(fixture, snapshot, report, notes);
        Files.writeString(capture.resolve("review.html"), html);
        List<Map<String, Object>> review = new ArrayList<>();
        for (var example : snapshot.cases()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", example.id());
            item.put("query", example.query());
            item.put("status", "PENDING_HUMAN_REVIEW");
            item.put("expectedAnswerNotes", notes.get(example.id()));
            item.put("expectedAnswerable", example.relevance().values().stream().anyMatch(grade -> grade > 0));
            Map<String, Integer> grades = new LinkedHashMap<>();
            fixture.documents().keySet().forEach(source -> grades.put(source,
                    example.relevance().getOrDefault(RetrievalSnapshot.key(example.id(), source), 0)));
            item.put("sourceGrades", grades);
            item.put("reviewerNotes", "");
            review.add(item);
        }
        mapper.writerWithDefaultPrettyPrinter().writeValue(capture.resolve("review-template.json").toFile(), review);
        System.out.println("Review packet created: " + capture.resolve("review.html").toAbsolutePath());
    }
}
