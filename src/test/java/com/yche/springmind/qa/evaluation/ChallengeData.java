package com.yche.springmind.qa.evaluation;

import com.yche.springmind.ingestion.transformer.*;
import org.springframework.ai.document.Document;
import java.nio.file.*;
import java.util.*;
import static com.yche.springmind.qa.evaluation.RetrievalAblation.require;

/** Frozen synthetic challenge and pre-retrieval judgments; never loads model clients. */
final class ChallengeData {
    static final Path ROOT = Path.of("evaluation/retrieval/challenge");
    static final Path OUTPUT = Path.of("evaluation/retrieval/results/meridian-2026-10-11");
    static final String INDEX = "springmind_eval_meridian_20261011";
    static final long GROUP = 1;
    static final List<String> CATEGORIES = List.of("identifiers", "paraphrase", "scope", "temporal", "complementary-evidence", "unanswerable");
    record Source(String filename, String text) {}
    record Fixture(String version, String annotationStatus, Map<String, Source> documents, List<ExtensionFixture.Case> cases) {}
    record Corpus(String annotationStatus, Map<String, ExtensionEvaluation.Chunk> chunks, Map<String, Map<String, Integer>> grades) {}

    private ChallengeData() {}
    static Fixture fixture() throws Exception {
        var f = RerankEvaluationRun.datasetMapper().readValue(ROOT.resolve("fixture.json").toFile(), Fixture.class);
        require(f.version().equals("meridian-challenge-v1") && f.annotationStatus().equals("PENDING_HUMAN_REVIEW"), "Unexpected challenge version/status");
        require(f.documents().size() == 96 && f.cases().size() == 36, "Frozen fixture size changed");
        Set<String> ids = new HashSet<>();
        for (var q : f.cases()) {
            require(ids.add(q.id()) && q.id().matches("c[0-9]{2}") && !q.query().isBlank() && !q.expected().isBlank()
                    && Set.of("zh", "en").contains(q.language()) && CATEGORIES.contains(q.category()), "Invalid challenge question");
            require(q.evidence().isEmpty() == q.category().equals("unanswerable"), "Unanswerable mismatch");
            for (var e : q.evidence()) require(f.documents().containsKey(e.document()) && !e.id().isBlank() && !e.quote().isBlank()
                    && contains(f.documents().get(e.document()).text(), e.quote()) && e.grade() >= 1 && e.grade() <= 3, "Invalid evidence: " + q.id());
        }
        for (String category : CATEGORIES) require(f.cases().stream().filter(q -> q.category().equals(category)).count() == 6, "Category count changed");
        f.documents().forEach((id, doc) -> require(id.matches("d[0-9]{2}") && !doc.filename().isBlank() && !doc.text().isBlank(), "Invalid source"));
        return f;
    }
    static Corpus build(Fixture f) {
        var transformer = new StructureAwareChunkTransformer(new ChunkingProperties());
        Map<String, ExtensionEvaluation.Chunk> chunks = new LinkedHashMap<>();
        long chunkId = 0, documentId = 0;
        for (var source : f.documents().entrySet()) {
            documentId++;
            var parts = transformer.apply(List.of(new Document(source.getValue().text())));
            for (int i = 0; i < parts.size(); i++) {
                String id = source.getKey() + "--c" + String.format(Locale.ROOT, "%03d", i);
                chunks.put(id, new ExtensionEvaluation.Chunk(++chunkId, documentId, source.getKey(), i, parts.get(i).getText()));
            }
        }
        Map<String, Map<String, Integer>> grades = new LinkedHashMap<>();
        for (var q : f.cases()) {
            Map<String, Integer> labels = new LinkedHashMap<>();
            chunks.keySet().forEach(id -> labels.put(id, 0));
            for (var e : q.evidence()) {
                List<String> matches = chunks.entrySet().stream().filter(c -> c.getValue().document().equals(e.document())
                        && contains(c.getValue().text(), e.quote())).map(Map.Entry::getKey).toList();
                require(!matches.isEmpty(), "Evidence lost at chunk boundary: " + q.id() + "/" + e.id());
                matches.forEach(id -> labels.merge(id, e.grade(), Math::max));
            }
            grades.put(q.id(), labels);
        }
        return new Corpus(f.annotationStatus(), chunks, grades);
    }
    static Corpus corpus() throws Exception {
        return RerankEvaluationRun.datasetMapper().readValue(ROOT.resolve("corpus.json").toFile(), Corpus.class);
    }
    static Map<String, String> hashes() throws Exception {
        Map<String, String> hashes = new LinkedHashMap<>();
        for (String name : List.of("fixture.json", "corpus.json", "README.md")) hashes.put(name, RetrievalAblation.hash(ROOT.resolve(name)));
        hashes.put("model-lock", RetrievalAblation.hash(EmbeddingComparison.LOCK));
        for (String path : List.of("ingestion/transformer/StructureAwareChunkTransformer.java", "ingestion/transformer/ChunkingProperties.java",
                "retrieval/elasticsearch/ElasticsearchChunkIndexService.java"))
            hashes.put(path, RetrievalAblation.hash(Path.of("src/main/java/com/yche/springmind", path)));
        return hashes;
    }
    static void verifyFreeze() throws Exception {
        var mapper = RerankEvaluationRun.datasetMapper();
        require(mapper.readTree(ROOT.resolve("freeze.json").toFile()).equals(mapper.valueToTree(hashes())), "Frozen inputs changed");
        require(mapper.valueToTree(build(fixture())).equals(mapper.valueToTree(corpus())), "Frozen chunk/label projection changed");
    }
    static double coverage(ExtensionFixture.Case q, Collection<String> ids, Corpus corpus) {
        Set<String> facts = new HashSet<>(), found = new HashSet<>();
        for (var e : q.evidence()) {
            facts.add(e.id());
            if (ids.stream().map(corpus.chunks()::get).anyMatch(c -> c.document().equals(e.document()) && contains(c.text(), e.quote()))) found.add(e.id());
        }
        return facts.isEmpty() ? Double.NaN : (double) found.size() / facts.size();
    }
    static boolean contains(String text, String quote) { return ExtensionFixture.normalize(text).contains(ExtensionFixture.normalize(quote)); }
    static void write(Path path, Object value) throws Exception {
        require(!Files.exists(path), "Refuse to overwrite: " + path);
        RerankEvaluationRun.datasetMapper().writerWithDefaultPrettyPrinter().writeValue(path.toFile(), value);
    }
}
