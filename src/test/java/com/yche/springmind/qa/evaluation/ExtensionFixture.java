package com.yche.springmind.qa.evaluation;

import java.nio.file.Path;
import java.util.*;
import static com.yche.springmind.qa.evaluation.RetrievalAblation.require;

record ExtensionFixture(String version, String annotationStatus, Map<String, String> documents, List<Case> cases) {
    static final Path PATH = Path.of("evaluation/retrieval/extension/fixture.json");
    record Evidence(String id, String document, String quote, int grade) {}
    record Case(String id, String category, String language, String query, String expected, List<Evidence> evidence) {}

    static ExtensionFixture read() throws Exception {
        var fixture = RerankEvaluationRun.datasetMapper().readValue(PATH.toFile(), ExtensionFixture.class);
        fixture.validate();
        return fixture;
    }

    void validate() {
        require("harborview-extension-v1".equals(version) && "PENDING_HUMAN_REVIEW".equals(annotationStatus), "Unexpected fixture revision/status");
        require(documents.size() == 66 && cases.size() == 24, "Frozen extension must have 66 documents and 24 questions");
        Set<String> ids = new HashSet<>();
        documents.forEach((id, text) -> require(id.matches("[a-z0-9-]+") && !text.isBlank(), "Invalid document"));
        for (Case question : cases) {
            require(ids.add(question.id()) && question.id().matches("e[0-9]{2}") && !question.query().isBlank()
                    && !question.expected().isBlank() && Set.of("zh", "en").contains(question.language()), "Invalid case");
            require(question.category().equals("unanswerable") == question.evidence().isEmpty(), "No-answer status mismatch");
            Set<String> facts = new HashSet<>();
            for (Evidence evidence : question.evidence()) {
                require(facts.add(evidence.id()) && documents.containsKey(evidence.document()) && !evidence.quote().isBlank()
                        && normalize(documents.get(evidence.document())).contains(normalize(evidence.quote()))
                        && evidence.grade() == (question.evidence().size() == 1 ? 3 : 2), "Invalid evidence fact in " + question.id());
            }
        }
        require(cases.stream().filter(q -> q.language().equals("zh")).count() == 12, "Expected balanced query languages");
        require(cases.stream().filter(q -> q.evidence().size() > 1).count() == 6, "Expected six complementary-fact cases");
        require(cases.stream().filter(q -> q.evidence().isEmpty()).count() == 4, "Expected four no-answer cases");
    }

    static String normalize(String text) { return text.strip().replaceAll("\\s+", " "); }
}
