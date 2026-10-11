package com.yche.springmind.qa.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;
import static com.yche.springmind.qa.evaluation.RetrievalAblation.require;

/** Original BEIR document IDs and official test qrels; no application label projection. */
final class BeirData {
    static final List<String> DATASETS = List.of("scifact", "nfcorpus");
    static final Path PROTOCOL = Path.of("evaluation/retrieval/beir");
    static final Path OUTPUT = Path.of("evaluation/retrieval/results/beir-2026-10-11");
    record Dataset(Map<String, String> documents, Map<String, String> queries, Map<String, Map<String, Integer>> qrels) {}
    private BeirData() {}
    static Path capturePath(String name) { return OUTPUT.resolve(name + "-capture.json.gz"); }
    static BeirEvaluation.Capture capture(String name) throws Exception {
        try (var in = new GZIPInputStream(Files.newInputStream(capturePath(name)))) {
            return RerankEvaluationRun.datasetMapper().readValue(in, BeirEvaluation.Capture.class);
        }
    }
    static void writeCapture(String name, BeirEvaluation.Capture value) throws Exception {
        require(!Files.exists(capturePath(name)), "Refuse to overwrite capture");
        Path temporary = capturePath(name).resolveSibling(name + "-capture.partial");
        try (var out = new GZIPOutputStream(Files.newOutputStream(temporary))) {
            RerankEvaluationRun.datasetMapper().writeValue(out, value);
        }
        Files.move(temporary, capturePath(name));
    }
    static Path source(String name) { require(DATASETS.contains(name), "Unknown BEIR dataset"); return Path.of("target/beir-downloads", name); }
    static Dataset read(String name) throws Exception {
        var root = source(name);
        var mapper = RerankEvaluationRun.datasetMapper();
        Map<String, String> documents = new TreeMap<>(), queries = new TreeMap<>();
        for (String line : Files.readAllLines(root.resolve("corpus.jsonl"))) {
            JsonNode doc = mapper.readTree(line);
            require(documents.put(doc.path("_id").asText(), (doc.path("title").asText("") + "\n" + doc.path("text").asText()).strip()) == null, "Duplicate corpus ID");
        }
        for (String line : Files.readAllLines(root.resolve("queries.jsonl"))) {
            var q = mapper.readTree(line); require(queries.put(q.path("_id").asText(), q.path("text").asText()) == null, "Duplicate query ID");
        }
        Map<String, Map<String, Integer>> qrels = new TreeMap<>();
        for (String line : Files.readAllLines(root.resolve("qrels/test.tsv")).stream().skip(1).toList()) {
            String[] cells = line.split("\t");
            require(cells.length == 3 && documents.containsKey(cells[1]) && queries.containsKey(cells[0]), "Foreign qrel");
            int grade = Integer.parseInt(cells[2]); require(grade >= 0, "Negative qrel");
            require(qrels.computeIfAbsent(cells[0], ignored -> new TreeMap<>()).put(cells[1], grade) == null, "Duplicate qrel");
        }
        require(qrels.values().stream().allMatch(labels -> labels.values().stream().anyMatch(g -> g > 0)), "Test query without positive qrels");
        queries.keySet().retainAll(qrels.keySet());
        require(documents.size() == (name.equals("scifact") ? 5183 : 3633) && queries.size() == (name.equals("scifact") ? 300 : 323), "Official corpus/test size mismatch");
        require(documents.values().stream().noneMatch(String::isBlank) && queries.values().stream().noneMatch(String::isBlank), "Empty text");
        return new Dataset(documents, queries, qrels);
    }
    static Map<String, String> hashes() throws Exception {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("protocol", RetrievalAblation.hash(PROTOCOL.resolve("README.md")));
        result.put("model-lock", RetrievalAblation.hash(EmbeddingComparison.LOCK));
        for (String name : DATASETS) {
            String md5 = HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(Files.readAllBytes(Path.of("target/beir-downloads", name + ".zip"))));
            require(md5.equals(name.equals("scifact") ? "5f7d1de60b170fc8027bb7898e2efca1" : "a89dba18a62ef92f7d323ec890a0d38d"), "Official archive MD5 mismatch");
            result.put(name + "/archive-md5", md5);
            for (String file : List.of("corpus.jsonl", "queries.jsonl", "qrels/test.tsv")) result.put(name + "/" + file, RetrievalAblation.hash(source(name).resolve(file)));
            read(name);
        }
        return result;
    }
    static void verify() throws Exception {
        var mapper = RerankEvaluationRun.datasetMapper();
        require(mapper.readTree(PROTOCOL.resolve("freeze.json").toFile()).equals(mapper.valueToTree(hashes())), "BEIR frozen inputs changed");
    }
}
