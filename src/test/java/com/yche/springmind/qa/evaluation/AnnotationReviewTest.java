package com.yche.springmind.qa.evaluation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class AnnotationReviewTest {
    @Test
    void allQuestionsHaveExplicitReviewNotes() throws Exception {
        var mapper = RerankEvaluationRun.datasetMapper();
        var fixture = mapper.readValue(Path.of("evaluation/rerank/sample.json").toFile(), RerankEvaluation.Dataset.class);
        Map<String, String> notes = mapper.readValue(Path.of("evaluation/rerank/review-notes.json").toFile(), new TypeReference<>() {});
        assertThat(notes.keySet()).containsExactlyInAnyOrderElementsOf(fixture.cases().stream().map(RerankEvaluation.Example::id).toList());
        assertThat(notes.values()).allSatisfy(note -> assertThat(note).isNotBlank());
    }

    @Test
    void reportEscapesTextAndIncludesZeroGradeSourcesAndPendingReviewWarning() throws Exception {
        var example = new RerankEvaluation.Example("q1", "test", "<script>alert('x')</script>", List.of("q1__b"), Map.of("q1__a", 3));
        var fixture = new RerankEvaluation.Dataset("SYNTHETIC", Map.of("a", "<private>", "b", "Distractor & source"), List.of());
        var snapshot = new RerankEvaluation.Dataset("RRF_SNAPSHOT", Map.of(), List.of(example));
        var report = new ObjectMapper().readTree("{\"rows\":[{\"id\":\"q1\",\"status\":\"APPLIED\",\"outputOrder\":[\"q1__b\"]}]}");
        String html = AnnotationReview.render(fixture, snapshot, report, Map.of("q1", "Review <this>"));
        assertThat(html).contains("标签尚未经过人工确认", "&lt;script&gt;", "&lt;private&gt;", "Distractor &amp; source",
                "当前 0 分", "未召回", "本页没有模型生成的答案").doesNotContain("<script>", "<private>");
    }
}
