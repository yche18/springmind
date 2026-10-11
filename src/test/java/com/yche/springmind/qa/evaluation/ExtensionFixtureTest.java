package com.yche.springmind.qa.evaluation;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class ExtensionFixtureTest {
    @Test
    void corpusAndQuestionsRespectThePredeclaredBalance() throws Exception {
        var fixture = ExtensionFixture.read();
        assertThat(fixture.documents().keySet().stream().filter(id -> id.endsWith("-zh"))).hasSize(33);
        assertThat(fixture.documents().keySet().stream().filter(id -> id.endsWith("-en"))).hasSize(33);
        var categories = new TreeMap<String, Integer>();
        fixture.cases().forEach(q -> categories.merge(q.category(), 1, Integer::sum));
        assertThat(categories).isEqualTo(Map.of("exact", 6, "semantic", 6, "cross-language", 4,
                "multi-evidence", 2, "long-document", 2, "unanswerable", 4));
        assertThat(fixture.documents().get("restore-guide-en").length()).isGreaterThan(3000);
        assertThat(fixture.documents().get("release-guide-zh").length()).isGreaterThan(1000);
        assertThat(new HashSet<>(fixture.documents().values())).hasSize(66);
    }
}
