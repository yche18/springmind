package com.yche.springmind.qa.evaluation;

import com.fasterxml.jackson.databind.*;
import com.yche.springmind.ingestion.transformer.*;
import org.springframework.ai.document.Document;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.*;
import static com.yche.springmind.qa.evaluation.RetrievalAblation.require;

/** Official rows and deterministic, outcome-independent sample/label preparation. */
final class MultiHopData {
    static final Path ROOT = Path.of("evaluation/retrieval/multihop");
    static final Path RAW = Path.of("target/multihop-downloads");
    static final Path OUT = Path.of("evaluation/retrieval/results/multihop-2026-10-11");
    static final String REVISION = "71ac0d0bd1f951d2d6b70311f7d2ae404e1ffa82";
    static final ObjectMapper M = RerankEvaluationRun.datasetMapper();
    record Chunk(long id, long documentId, int index, String title, String body, String text) {}
    record Fact(String id, long documentId, String quote, List<List<Long>> alternatives) {}
    record Question(String id, int sourceRow, String split, String type, String query, String answer, List<Fact> facts) {}
    record Sample(String revision, String annotationStatus, List<Question> questions, List<String> exclusions) {}
    record Corpus(Map<Long, Chunk> chunks, Map<Long, JsonNode> documents) {}

    static String norm(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFKC).replace('’', '\'').replace('‘', '\'')
                .replace('“', '"').replace('”', '"').replaceAll("\\s+", " ").strip();
    }
    static String sha(String text) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    }
    static Corpus corpus() throws Exception {
        var raw = M.readTree(RAW.resolve("corpus.json").toFile());
        require(raw.size() == 609, "Expected official full corpus");
        var transformer = new StructureAwareChunkTransformer(new ChunkingProperties(240, 320, 32));
        Map<Long, Chunk> chunks = new LinkedHashMap<>();
        Map<Long, JsonNode> docs = new LinkedHashMap<>();
        long id = 0, docId = 0;
        for (var d : raw) {
            docs.put(++docId, d);
            var parts = transformer.apply(List.of(new Document(d.path("body").asText())));
            require(!parts.isEmpty(), "Empty article");
            String title = d.path("title").asText();
            String prefix = "Title: " + title + "\nSource: " + d.path("source").asText()
                    + "\nPublished: " + d.path("published_at").asText() + "\n";
            for (int i = 0; i < parts.size(); i++) {
                String body = parts.get(i).getText();
                chunks.put(++id, new Chunk(id, docId, i, title, body, prefix + body));
            }
        }
        return new Corpus(chunks, docs);
    }
    static Sample prepare(Corpus corpus) throws Exception {
        var queries = M.readTree(RAW.resolve("MultiHopRAG.json").toFile());
        require(queries.size() == 2556, "Expected all official questions");
        Map<String, Long> byUrl = new HashMap<>();
        corpus.documents().forEach((id, d) -> require(byUrl.put(d.path("url").asText(), id) == null, "Duplicate source URL"));
        Map<Integer, String> keys = new HashMap<>();
        for (int i = 0; i < queries.size(); i++) keys.put(i, sha("multihop-20261011-v1:" + i));
        List<Question> selected = new ArrayList<>();
        List<String> exclusions = new ArrayList<>();
        String[] types = {"inference_query", "comparison_query", "temporal_query", "null_query"};
        int[][] quotas = {{16, 17, 11, 6}, {3, 3, 3, 1}};
        for (int split = 0; split < 2; split++) {
            String splitName = split == 0 ? "FORMAL" : "CALIBRATION";
            for (int type = 0; type < types.length; type++) {
                Map<Integer, List<Integer>> strata = new TreeMap<>();
                for (int i = 0; i < queries.size(); i++) if (queries.get(i).path("question_type").asText().equals(types[type]))
                    strata.computeIfAbsent(queries.get(i).path("evidence_list").size(), ignored -> new ArrayList<>()).add(i);
                int total = strata.values().stream().mapToInt(List::size).sum(), count = quotas[split][type];
                Map<Integer, Integer> allocations = new TreeMap<>();
                strata.forEach((h, rows) -> allocations.put(h, count * rows.size() / total));
                var remainderOrder = strata.keySet().stream().sorted(Comparator.<Integer>comparingInt(h -> count * strata.get(h).size() % total)
                        .reversed().thenComparingInt(h -> h)).toList();
                int left = count - allocations.values().stream().mapToInt(Integer::intValue).sum();
                for (int i = 0; i < left; i++) allocations.merge(remainderOrder.get(i), 1, Integer::sum);
                for (var stratum : strata.entrySet()) {
                    int taken = 0;
                    for (int row : stratum.getValue().stream().sorted(Comparator.comparing(keys::get)).toList()) {
                        if (taken == allocations.get(stratum.getKey())) break;
                        var q = queries.get(row);
                        String query = q.path("query").asText();
                        List<Fact> facts = new ArrayList<>();
                        for (var e : q.path("evidence_list")) {
                            Long doc = byUrl.get(e.path("url").asText());
                            require(doc != null, "Reference source missing for official row " + row);
                            String quote = e.path("fact").asText();
                            facts.add(new Fact("f" + (facts.size() + 1), doc, quote, spans(corpus, doc, quote)));
                        }
                        Set<Long> docs = new HashSet<>(facts.stream().map(Fact::documentId).toList());
                        String reason = null;
                        for (var old : selected) {
                            if (norm(old.query()).equals(norm(query))) { reason = "duplicate-question:" + old.id(); break; }
                            if (!old.split().equals(splitName) && ((!docs.isEmpty() && docs.equals(new HashSet<>(old.facts().stream().map(Fact::documentId).toList())))
                                    || similar(old.query(), query))) { reason = "cross-split-related:" + old.id(); break; }
                        }
                        if (reason != null) { exclusions.add(splitName + ":row-" + row + ":" + reason); continue; }
                        selected.add(new Question(String.format(Locale.ROOT, "m%04d", row), row, splitName, types[type], query,
                                q.path("answer").asText(), facts));
                        taken++;
                    }
                    require(taken == allocations.get(stratum.getKey()), "Insufficient independent samples");
                }
            }
        }
        return new Sample(REVISION, "OFFICIAL_REFERENCE_PENDING_LOCAL_REVIEW", selected, exclusions);
    }
    static boolean similar(String a, String b) {
        Set<String> x = new HashSet<>(List.of(norm(a).toLowerCase(Locale.ROOT).split("\\W+")));
        Set<String> y = new HashSet<>(List.of(norm(b).toLowerCase(Locale.ROOT).split("\\W+")));
        Set<String> intersection = new HashSet<>(x); intersection.retainAll(y); x.addAll(y);
        return (double) intersection.size() / x.size() >= .8;
    }
    static List<List<Long>> spans(Corpus corpus, long document, String quote) {
        var chunks = corpus.chunks().values().stream().filter(c -> c.documentId() == document).toList();
        List<List<Long>> found = new ArrayList<>();
        for (int start = 0; start < chunks.size(); start++) {
            String merged = "";
            List<Long> ids = new ArrayList<>();
            for (int end = start; end < Math.min(chunks.size(), start + 3); end++) {
                merged = join(merged, chunks.get(end).body()); ids.add(chunks.get(end).id());
                if (norm(merged).contains(norm(quote))) { found.add(List.copyOf(ids)); break; }
            }
        }
        return found.stream().filter(a -> found.stream().noneMatch(b -> b.size() < a.size() && a.containsAll(b))).toList();
    }
    static String join(String a, String b) {
        for (int n = Math.min(512, Math.min(a.length(), b.length())); n >= 16; n--)
            if (a.endsWith(b.substring(0, n))) return a + b.substring(n);
        return a.isEmpty() ? b : a + "\n" + b;
    }
    static Map<String, String> hashes() throws Exception {
        Map<String, String> h = new TreeMap<>();
        for (Path path : List.of(RAW.resolve("corpus.json"), RAW.resolve("MultiHopRAG.json"), RAW.resolve("README.md"),
                ROOT.resolve("README.md"), ROOT.resolve("sample.json"), EmbeddingComparison.LOCK,
                Path.of("src/test/java/com/yche/springmind/qa/evaluation/MultiHopData.java"),
                Path.of("src/main/resources/application-dev.yml"))) h.put(path.toString().replace('\\', '/'), RetrievalAblation.hash(path));
        for (String name : List.of("ingestion/transformer/StructureAwareChunkTransformer.java", "ingestion/transformer/ChunkingProperties.java",
                "retrieval/elasticsearch/ElasticsearchChunkIndexService.java", "qa/rag/HybridEvidenceRetriever.java", "qa/rag/EvidenceSelector.java")) {
            Path path = Path.of("src/main/java/com/yche/springmind", name);
            h.put(path.toString().replace('\\', '/'), RetrievalAblation.hash(path));
        }
        return h;
    }
    static Sample sample() throws Exception { return M.readValue(ROOT.resolve("sample.json").toFile(), Sample.class); }
    static void verify(Corpus corpus) throws Exception {
        var frozen = M.readTree(ROOT.resolve("freeze.json").toFile());
        require(frozen.path("hashes").equals(M.valueToTree(hashes())), "Frozen inputs changed");
        require(frozen.path("chunksSha256").asText().equals(sha(M.writeValueAsString(corpus))), "Chunk drift");
        require(M.valueToTree(sample()).equals(M.valueToTree(prepare(corpus))), "Sampling/label drift");
    }
}
