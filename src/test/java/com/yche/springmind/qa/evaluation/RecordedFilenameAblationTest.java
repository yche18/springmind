package com.yche.springmind.qa.evaluation;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Saved real search results remain independently reproducible without any running services. */
class RecordedFilenameAblationTest {
    private static final Path ROOT = Path.of("evaluation/retrieval/results/harborview-filename-2026-10-10");
    private final com.fasterxml.jackson.databind.ObjectMapper mapper = RerankEvaluationRun.datasetMapper();

    private FilenameAblation.Capture capture() throws Exception {
        return mapper.readValue(ROOT.resolve("capture.json").toFile(), FilenameAblation.Capture.class);
    }

    @Test
    void allNumbersAndPagesRecomputeFromUnchangedInputs() throws Exception {
        assertThat(RetrievalAblation.hash(ROOT.resolve("capture.json")))
                .isEqualTo("cccefe1e6f4f30f48669cae29171a6cf1eaf5de135cb6c19d790b321bac8fcb6");
        var capture = capture();
        var comparison = FilenameAblation.compare(capture, RetrievalAblation.hash(ROOT.resolve("capture.json")));
        assertThat((com.fasterxml.jackson.databind.JsonNode) mapper.valueToTree(comparison)).isEqualTo(mapper.readTree(ROOT.resolve("comparison.json").toFile()));
        assertThat(comparison.annotationStatus()).isEqualTo("HUMAN_REVIEWED");
        assertThat(comparison.originalOrderMatches()).isEqualTo(24);
        assertThat(comparison.originalScoreMatches()).isEqualTo(24);
        assertThat(comparison.newModelRequests()).isZero();
        assertThat(Files.readString(ROOT.resolve("report.html")).replace("\r\n", "\n"))
                .isEqualTo(FilenameAblationReportRun.render(capture, comparison).replace("\r\n", "\n"))
                .contains("改善 4，退化 0，不变 16", "人工确认").doesNotContain("<script>");
        var body = comparison.groups().get("ALL").get("BODY_ONLY");
        var full = comparison.groups().get("ALL").get("BODY_AND_FILENAME");
        assertThat(body.metrics().recall()).isEqualTo(0.75);
        assertThat(full.metrics().recall()).isEqualTo(0.90);
        assertThat(body.completeComplementaryCases()).isEqualTo(4);
        assertThat(full.completeComplementaryCases()).isEqualTo(5);
        var changed = comparison.rows().stream().filter(row -> row.scores().get("BODY_ONLY").metrics() != null)
                .filter(row -> Math.abs(row.scores().get("BODY_ONLY").metrics().ndcg()
                        - row.scores().get("BODY_AND_FILENAME").metrics().ndcg()) > 1e-12).map(FilenameAblation.Row::id).toList();
        assertThat(changed).containsExactly("e10", "e13", "e14", "e15");
        comparison.rows().stream().filter(row -> row.category().equals("unanswerable")).forEach(row ->
                assertThat(row.scores().values()).allSatisfy(score -> {
                    assertThat(score.metrics()).isNull();
                    assertThat(score.factCoverage5()).isNull();
                }));
    }

    @Test
    void verifiedIndexSnapshotContainsExactlyTheReviewedCorpus() throws Exception {
        assertThat(RetrievalAblation.hash(ROOT.resolve("index-snapshot.json")))
                .isEqualTo("84839a0dc3c09a90d7c08f7fb540a571d2ed5a0f7f307a924254be555a01fca2");
        var corpus = FilenameAblation.corpus();
        var snapshot = mapper.readTree(ROOT.resolve("index-snapshot.json").toFile());
        assertThat(snapshot).hasSize(105);
        Set<Long> ids = new HashSet<>();
        for (var hit : snapshot) {
            var source = hit.path("_source");
            long id = source.path("chunkId").asLong();
            assertThat(ids.add(id)).isTrue();
            var chunk = corpus.chunks().values().stream().filter(value -> value.chunkId() == id).findFirst().orElseThrow();
            assertThat(source.path("groupId").asLong()).isEqualTo(corpus.groupId());
            assertThat(source.path("documentId").asLong()).isEqualTo(chunk.documentId());
            assertThat(source.path("chunkIndex").asInt()).isEqualTo(chunk.index());
            assertThat(source.path("fileName").asText()).isEqualTo(chunk.document() + ".txt");
            assertThat(source.path("chunkText").asText()).isEqualTo(chunk.text());
            assertThat(source.path("status").asText()).isEqualTo("READY");
            assertThat(source.path("deleted").asBoolean()).isFalse();
        }
    }

    @Test
    void changedQueryForeignHitsAndMismatchedProvenanceAreRejected() throws Exception {
        var original = (ObjectNode) mapper.readTree(ROOT.resolve("capture.json").toFile());
        for (String field : List.of("fixtureSha256", "corpusSha256", "approvalSha256", "protocolSha256", "originalCaptureSha256")) {
            var invalid = original.deepCopy();
            invalid.put(field, "wrong");
            assertThatThrownBy(() -> FilenameAblation.compare(mapper.treeToValue(invalid, FilenameAblation.Capture.class), "test"))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("hash mismatch");
        }
        var request = original.deepCopy();
        ((ObjectNode) request.at("/rows/0/requests/BODY_ONLY/rescore/query")).put("query_weight", 99);
        assertThatThrownBy(() -> FilenameAblation.compare(mapper.treeToValue(request, FilenameAblation.Capture.class), "test"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("single-field ablation");
        var foreign = original.deepCopy();
        ((ObjectNode) foreign.at("/rows/0/hits/BODY_ONLY/0")).put("chunk", "foreign-chunk");
        assertThatThrownBy(() -> FilenameAblation.compare(mapper.treeToValue(foreign, FilenameAblation.Capture.class), "test"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("foreign raw hits");
        var model = original.deepCopy();
        model.put("newModelRequests", 1);
        assertThatThrownBy(() -> FilenameAblation.compare(mapper.treeToValue(model, FilenameAblation.Capture.class), "test"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("invalid capture");
    }

    @Test
    void numericJsonRoundTripsDoNotChangeTheProtocol() throws Exception {
        var capture = capture();
        var first = capture.rows().getFirst();
        var query = ExtensionFixture.read().cases().getFirst().query();
        first.requests().put("BODY_ONLY", FilenameAblation.request(FilenameAblation.corpus().groupId(), query, false));
        var inMemory = FilenameAblation.compare(capture, "same");
        var parsed = mapper.readValue(mapper.writeValueAsBytes(capture), FilenameAblation.Capture.class);
        assertThat(FilenameAblation.compare(parsed, "same")).isEqualTo(inMemory);
    }
}
