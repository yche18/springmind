package com.yche.springmind.qa.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.yche.springmind.qa.rag.EvidenceSelectionProperties;
import com.yche.springmind.qa.rag.HttpEvidenceReranker;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/** Explicit Maven entry point; deliberately outside Surefire's default *Test naming. */
class RerankEvaluationRun {
    @Test
    void replay() throws Exception {
        boolean live = Boolean.getBoolean("eval.live");
        if (live && !Boolean.getBoolean("eval.confirmExternal")) {
            throw new IllegalArgumentException("Live replay sends dataset text externally and may incur charges; set eval.confirmExternal=true");
        }
        Path input = Path.of(System.getProperty("eval.dataset", "evaluation/rerank/sample.json"));
        if (Files.size(input) > 10_000_000) throw new IllegalArgumentException("Dataset must not exceed 10 MB");
        byte[] bytes = Files.readAllBytes(input);
        ObjectMapper mapper = datasetMapper();
        RerankEvaluation.Dataset data = mapper.readValue(bytes, RerankEvaluation.Dataset.class);
        RerankEvaluation.validate(data);
        int k = Integer.parseInt(System.getProperty("eval.k", "5"));
        int maxRequests = Integer.parseInt(System.getProperty("eval.maxRequests", "30"));
        long requestCount = data.cases().stream().filter(example -> example.candidates().size() >= 2).count();
        if (maxRequests < 1 || maxRequests > 500 || (live && requestCount > maxRequests)) {
            throw new IllegalArgumentException("Request budget exceeded; explicitly configure eval.maxRequests (1..500)");
        }
        EvidenceSelectionProperties properties = live
                ? new EvidenceSelectionProperties(4096, 30, true, requiredEnv("RERANK_URL"),
                    System.getenv().getOrDefault("RERANK_API_KEY", ""), requiredEnv("RERANK_MODEL"),
                    Duration.ofMillis(Long.parseLong(System.getProperty("eval.timeoutMs", "2000"))))
                : EvidenceSelectionProperties.defaults();
        RerankEvaluation.Result result;
        try (var reranker = new HttpEvidenceReranker(properties, mapper)) {
            result = RerankEvaluation.run(data, k, reranker, live);
        }
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("schemaVersion", 1);
        report.put("createdAt", Instant.now().toString());
        report.put("datasetSha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        report.put("provenance", data.provenance());
        report.put("baseline", data.provenance().equals("RRF_SNAPSHOT") ? "supplied RRF snapshot order" : "synthetic supplied order; NOT measured RRF retrieval");
        report.put("mode", live ? "LIVE_FIXED_POOL" : "OFFLINE_BASELINE_ONLY");
        report.put("k", k);
        report.put("model", live ? properties.rerankModel() : null);
        report.put("timeoutMs", live ? properties.rerankTimeout().toMillis() : null);
        report.put("plannedProviderRequests", live ? requestCount : 0);
        report.put("previewCodePointLimit", 4096);
        report.put("billedCost", null);
        report.put("limitations", "Ranking replay only; no generation, refusal, evidence-budget or end-to-end QA measurement. "
                + "Unanswerable cases excluded from ranking averages. Fallbacks included. Cost unknown: consult provider billing; "
                + "local token estimates are not billed usage. All relevant labels must be supplied, including missed candidates.");
        report.put("summary", result.summary());
        report.put("rows", result.rows());
        Path output = Path.of("target", "rerank-evaluation", "report-" + System.currentTimeMillis() + ".json");
        Files.createDirectories(output.getParent());
        mapper.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), report);
        System.out.println("Rerank evaluation: " + report.get("mode") + "; cases=" + data.cases().size()
                + "; report=" + output.toAbsolutePath());
    }

    static ObjectMapper datasetMapper() {
        return JsonMapper.builder().disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS).build();
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Set " + name + " explicitly for live replay");
        return value;
    }
}
