package com.yche.springmind.qa.evaluation;

import com.yche.springmind.qa.rag.*;
import org.springframework.ai.document.Document;
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;
import java.nio.file.*;
import java.util.*;
import static com.yche.springmind.qa.evaluation.RetrievalAblation.require;

final class MultiHopRerank {
    static final Path ROOT = Path.of("evaluation/rerank/multihop");
    static final Path OUT = Path.of("evaluation/rerank/results/multihop-2026-10-11");
    static final List<String> MODES = List.of("KEYWORD", "VECTOR", "RRF");
    static final String MODEL = "qwen3-rerank";
    static final String URL = "https://maas.qianwenaiapi.com/compatible-api/v1/reranks";
    record Candidate(String id, long primaryChunkId, List<Long> chunks, String textSha256, String previewSha256, boolean clipped) {}
    record Input(String id, String mode, String requestSha256, List<Candidate> candidates, int estimatedTokens) {}
    record Outcome(String id, String mode, String requestSha256, String status, long elapsedMs,
                   List<String> order, Map<String, Double> scores) {}
    record Row(String id, Map<String, Outcome> outcomes, Map<String, MultiHopReplay.Context> contexts) {}
    record Capture(String completedAt, String freezeSha256, int providerAttempts, int generationCalls, List<Row> rows) {}

    static String preview(String text) { return text.substring(0, text.offsetByCodePoints(0, Math.min(4096, text.codePointCount(0, text.length())))); }
    static Input input(String id, String mode, String query, List<Document> docs) throws Exception {
        List<Candidate> candidates = new ArrayList<>();
        int tokens = 0;
        var tokenizer = new JTokkitTokenCountEstimator();
        for (var d : docs) {
            @SuppressWarnings("unchecked") var windows = (List<EvidenceWindowChunk>) d.getMetadata().get("windowChunks");
            require(windows != null && !windows.isEmpty(), "Missing production windows");
            String text = preview(d.getText());
            candidates.add(new Candidate(d.getId(), ((Number) d.getMetadata().get("primaryChunkId")).longValue(),
                    windows.stream().map(EvidenceWindowChunk::chunkId).toList(), MultiHopData.sha(d.getText()), MultiHopData.sha(text), !text.equals(d.getText())));
            tokens += tokenizer.estimate(query) + tokenizer.estimate(text);
        }
        Map<String, Object> body = new LinkedHashMap<>(); body.put("query", query);
        body.put("documents", docs.stream().map(Document::getText).map(MultiHopRerank::preview).toList());
        body.put("top_n", docs.size()); body.put("model", MODEL);
        return new Input(id, mode, MultiHopData.sha(MultiHopData.M.writeValueAsString(body)), candidates, tokens);
    }
    static List<Input> inputs() throws Exception {
        return MultiHopData.M.readValue(ROOT.resolve("inputs.json").toFile(), MultiHopData.M.getTypeFactory().constructCollectionType(List.class, Input.class));
    }
    static MultiHopEvaluation.Capture baseline() throws Exception {
        return MultiHopData.M.readValue(MultiHopData.OUT.resolve("capture.json").toFile(), MultiHopEvaluation.Capture.class);
    }
    static Map<String, String> hashes() throws Exception {
        Map<String, String> hashes = new TreeMap<>();
        for (var path : List.of(ROOT.resolve("README.md"), ROOT.resolve("inputs.json"),
                MultiHopData.ROOT.resolve("freeze.json"), MultiHopData.ROOT.resolve("sample.json"),
                MultiHopData.OUT.resolve("capture.json"), MultiHopData.OUT.resolve("comparison.json"), MultiHopData.OUT.resolve("review-status.json"),
                Path.of("src/test/java/com/yche/springmind/qa/evaluation/MultiHopReplay.java"),
                Path.of("src/test/java/com/yche/springmind/qa/evaluation/MultiHopRerank.java"),
                Path.of("src/main/java/com/yche/springmind/qa/rag/HttpEvidenceReranker.java")))
            hashes.put(path.toString().replace('\\', '/'), RetrievalAblation.hash(path));
        return hashes;
    }
    static void verify() throws Exception {
        var freeze = MultiHopData.M.readTree(ROOT.resolve("freeze.json").toFile());
        require(freeze.path("hashes").equals(MultiHopData.M.valueToTree(hashes())), "Rerank freeze changed");
        require(inputs().size() == 180, "Must retain all three arms and60 questions");
    }
    static void validate(Input input, Outcome outcome) {
        require(input.id().equals(outcome.id()) && input.mode().equals(outcome.mode()) && input.requestSha256().equals(outcome.requestSha256()), "Mismatched outcome identity");
        var ids = input.candidates().stream().map(Candidate::id).toList();
        require(outcome.order().size() == ids.size() && new HashSet<>(outcome.order()).equals(new HashSet<>(ids)), "Output must be a complete unique permutation");
        require(Set.of("APPLIED", "FALLBACK", "SKIPPED").contains(outcome.status()) && outcome.elapsedMs() >= 0, "Invalid outcome status");
        if (outcome.status().equals("APPLIED")) {
            require(outcome.scores().keySet().equals(new HashSet<>(ids)) && outcome.scores().values().stream().allMatch(Double::isFinite), "Missing/nonfinite model scores");
            var expected = ids.stream().sorted(Comparator.<String>comparingDouble(outcome.scores()::get).reversed().thenComparingInt(ids::indexOf)).toList();
            require(expected.equals(outcome.order()), "Model score order mismatch");
        } else require(outcome.order().equals(ids) && outcome.scores().isEmpty(), "Fallback must preserve baseline order");
    }
    static EvidenceReranker.Result replay(Outcome outcome, List<Document> docs) {
        Map<String, Document> byId = new HashMap<>(); docs.forEach(d -> byId.put(d.getId(), d));
        return new EvidenceReranker.Result(outcome.order().stream().map(byId::get).toList(), outcome.status());
    }
    private MultiHopRerank() {}
}
