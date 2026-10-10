package com.yche.springmind.qa.evaluation;

import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class FilenameAblationTest {
    @Test
    void removesOnlyFilenameClausesFromBothStages() {
        ObjectNode full = FilenameAblation.request(3, "Shanghai backups", true);
        ObjectNode body = FilenameAblation.request(3, "Shanghai backups", false);
        for (String path : java.util.List.of("/query/bool/should", "/rescore/query/rescore_query/bool/should")) {
            var original = (ArrayNode) full.at(path);
            assertThat(original).hasSize(4);
            assertThat(body.at(path)).hasSize(2);
            original.remove(0);
            original.remove(0);
            assertThat(body.at(path)).isEqualTo(original);
            assertThat(body.at(path).toString()).contains("chunkText").doesNotContain("fileName");
        }
        assertThat(body).isEqualTo(full);
        assertThat(body.at("/rescore/window_size").asInt()).isEqualTo(50);
        assertThat(body.at("/query/bool/filter/0/term/groupId").asInt()).isEqualTo(3);
        assertThat(body.at("/query/bool/filter/1/term/status").asText()).isEqualTo("READY");
        assertThat(body.at("/query/bool/filter/2/term/deleted").asBoolean()).isFalse();
    }

    @Test
    void frozenInputsHaveOwnerApproval() throws Exception {
        FilenameAblation.verifyApproval();
    }
}
