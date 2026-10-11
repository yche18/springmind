package com.yche.springmind.qa.evaluation;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.*;

class ReviewApprovalTest {
    @TempDir Path directory;

    private ObjectNode report() throws Exception {
        return (ObjectNode) RerankEvaluationRun.datasetMapper().readTree(
                "{\"datasetSha256\":\"fixed-hash\",\"annotationRevision\":\"v2\",\"summary\":{\"ndcg\":0.9}}");
    }

    private ObjectNode approval() throws Exception {
        return report().put("status", "HUMAN_REVIEWED");
    }

    @Test
    void explicitMatchingApprovalChangesOnlyReviewMetadata() throws Exception {
        var report = report();
        var before = report.deepCopy();
        assertThat(ReviewApproval.apply(approval(), report)).isTrue();
        assertThat(report.path("annotationStatus").asText()).isEqualTo("HUMAN_REVIEWED");
        report.remove("annotationStatus");
        report.remove("humanReview");
        assertThat(report).isEqualTo(before);
    }

    @Test
    void changedDatasetOrRevisionCannotInheritApproval() throws Exception {
        var report = report();
        var before = report.deepCopy();
        assertThat(ReviewApproval.apply(approval().put("datasetSha256", "changed"), report)).isFalse();
        assertThat(ReviewApproval.apply(approval().put("annotationRevision", "v3"), report)).isFalse();
        assertThat(ReviewApproval.apply(approval().put("annotationRevision", ""), report)).isFalse();
        assertThat(ReviewApproval.apply(approval().put("status", "PENDING_HUMAN_REVIEW"), report)).isFalse();
        assertThat(report).isEqualTo(before);
    }

    @Test
    void missingConfirmationDoesNotApprove() throws Exception {
        var report = report();
        assertThat(ReviewApproval.apply(directory, report)).isFalse();
        assertThat(report.has("annotationStatus")).isFalse();
    }
}
