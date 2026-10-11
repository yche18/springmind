package com.yche.springmind.qa.evaluation;

import com.yche.springmind.qa.rag.EvidenceReranker;
import com.yche.springmind.qa.rag.EvidenceSelectionProperties;
import com.yche.springmind.qa.rag.HttpEvidenceReranker;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.ai.document.Document;

import java.nio.file.Path;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

class RerankEvaluationTest {
    private final Map<String, String> corpus = Map.of("a", "Alpha", "b", "Beta", "c", "Gamma", "d", "Delta");

    private RerankEvaluation.Example example(String id, List<String> pool, Map<String, Integer> labels) {
        return new RerankEvaluation.Example(id, "test", "Question?", pool, labels);
    }

    private RerankEvaluation.Dataset dataset(RerankEvaluation.Example... examples) {
        return new RerankEvaluation.Dataset("SYNTHETIC", corpus, List.of(examples));
    }

    @Test
    void gradedMetricsUseAllRelevantLabelsIncludingThoseOutsideCandidatePool() {
        var score = RerankEvaluation.metrics(List.of("a", "b", "c"), Map.of("b", 3, "c", 1, "d", 2), 2);
        double discount = Math.log(3) / Math.log(2);
        assertThat(score.hit()).isEqualTo(1);
        assertThat(score.recall()).isCloseTo(1.0 / 3, within(1e-12));
        assertThat(score.mrr()).isEqualTo(0.5);
        assertThat(score.ndcg()).isCloseTo((7 / discount) / (7 + 3 / discount), within(1e-12));
    }

    @Test
    void perfectRankingAndMissesHaveExpectedScores() {
        var perfect = RerankEvaluation.metrics(List.of("b", "a"), Map.of("b", 3), 5);
        assertThat(perfect).isEqualTo(new RerankEvaluation.Metrics(1, 1, 1, 1));
        var miss = RerankEvaluation.metrics(List.of("a"), Map.of("b", 3), 5);
        assertThat(miss).isEqualTo(new RerankEvaluation.Metrics(0, 0, 0, 0));
        assertThat(RerankEvaluation.metrics(List.of("a"), Map.of(), 5)).isNull();
        assertThat(RerankEvaluation.metrics(List.of(), Map.of("b", 1), 5)).isEqualTo(miss);
    }

    @Test
    void offlineProductionAdapterMakesNoComparisonOrLatencyClaims() throws Exception {
        var data = RerankEvaluationRun.datasetMapper().readValue(Path.of("evaluation/rerank/sample.json").toFile(),
                RerankEvaluation.Dataset.class);
        try (var reranker = new HttpEvidenceReranker(EvidenceSelectionProperties.defaults(), RerankEvaluationRun.datasetMapper())) {
            var result = RerankEvaluation.run(data, 5, reranker, false);
            assertThat(result.rows()).hasSize(30);
            assertThat(result.summary().judgedAnswerableCases()).isEqualTo(24);
            assertThat(result.summary().unanswerableCases()).isEqualTo(6);
            assertThat(result.summary().statuses()).containsOnly(entry("DISABLED", 30L));
            assertThat(result.summary().reranked()).isNull();
            assertThat(result.summary().delta()).isNull();
            assertThat(result.summary().latencyP95Ms()).isNull();
            assertThat(result.rows()).allSatisfy(row -> {
                assertThat(row.outputOrder()).isNull();
                assertThat(row.latencyMs()).isNull();
            });
        }
    }

    @Test
    void comparesIdenticalPoolsAndExcludesUnanswerableCasesFromAverages() {
        var calls = new AtomicInteger();
        EvidenceReranker stub = (question, documents) -> {
            calls.incrementAndGet();
            assertThat(documents).extracting(Document::getText).containsExactly("Alpha", "Beta");
            return new EvidenceReranker.Result(documents.reversed(), "APPLIED");
        };
        var data = dataset(example("q1", List.of("a", "b"), Map.of("b", 3)),
                example("q2", List.of("a", "b"), Map.of()));
        var result = RerankEvaluation.run(data, 1, stub, true);
        assertThat(calls).hasValue(2);
        assertThat(result.summary().baseline().recall()).isZero();
        assertThat(result.summary().reranked().recall()).isEqualTo(1);
        assertThat(result.summary().delta().recall()).isEqualTo(1);
        assertThat(result.summary().candidateRecall()).isEqualTo(1);
        assertThat(result.summary().statuses()).containsOnly(entry("APPLIED", 2L));
        assertThat(result.rows().get(1).reranked()).isNull();
        assertThat(result.summary().latencyP95Ms()).isGreaterThanOrEqualTo(result.summary().latencyP50Ms());
    }

    @Test
    void failuresRemainInComparisonAndKeepBaselineRatherThanDisappearing() {
        var result = RerankEvaluation.run(dataset(example("q", List.of("a", "b"), Map.of("b", 3))), 1,
                (query, documents) -> new EvidenceReranker.Result(documents, "FALLBACK"), true);
        assertThat(result.summary().statuses()).containsOnly(entry("FALLBACK", 1L));
        assertThat(result.summary().baseline()).isEqualTo(result.summary().reranked());
        assertThat(result.summary().delta()).isEqualTo(new RerankEvaluation.Metrics(0, 0, 0, 0));
        assertThat(result.rows().getFirst().latencyMs()).isNotNull();
    }

    @Test
    void replayUsesProductionHttpProtocolWithFixedCandidateText() throws Exception {
        var mapper = RerankEvaluationRun.datasetMapper();
        var requests = new java.util.concurrent.atomic.AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/rerank", exchange -> {
            requests.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"results\":[{\"index\":1,\"relevance_score\":0.9},{\"index\":0,\"relevance_score\":0.1}]}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (var output = exchange.getResponseBody()) { output.write(response); }
        });
        server.start();
        try (var reranker = new HttpEvidenceReranker(new EvidenceSelectionProperties(4096, 30, true,
                "http://127.0.0.1:" + server.getAddress().getPort() + "/rerank", "", "fixture", Duration.ofSeconds(2)), mapper)) {
            var result = RerankEvaluation.run(dataset(example("q", List.of("a", "b"), Map.of("b", 3))), 1, reranker, true);
            var request = mapper.readTree(requests.get());
            assertThat(request.path("documents").get(0).asText()).isEqualTo("Alpha");
            assertThat(request.path("documents").get(1).asText()).isEqualTo("Beta");
            assertThat(request.path("top_n").asInt()).isEqualTo(2);
            assertThat(result.summary().statuses()).containsOnly(entry("APPLIED", 1L));
            assertThat(result.rows().getFirst().outputOrder()).containsExactly("b", "a");
            assertThat(result.summary().delta().mrr()).isEqualTo(1);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void skippedEmptyPoolIsStillAMissForAnAnswerableQuery() {
        var result = RerankEvaluation.run(dataset(example("q", List.of(), Map.of("b", 3))), 5,
                (query, documents) -> new EvidenceReranker.Result(documents, "SKIPPED"), true);
        assertThat(result.summary().baseline().recall()).isZero();
        assertThat(result.summary().candidateRecall()).isZero();
        assertThat(result.summary().latencyP50Ms()).isNull();
    }

    @Test
    void rejectsLostOrForeignCandidatesAndDisabledLiveMode() {
        var data = dataset(example("q", List.of("a", "b"), Map.of("b", 3)));
        assertThatThrownBy(() -> RerankEvaluation.run(data, 5,
                (query, docs) -> new EvidenceReranker.Result(List.of(docs.getFirst()), "APPLIED"), true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RerankEvaluation.run(data, 5,
                (query, docs) -> new EvidenceReranker.Result(docs, "DISABLED"), true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsInvalidDatasetBeforeAnyProviderCall() {
        List<RerankEvaluation.Dataset> invalid = new ArrayList<>();
        invalid.add(new RerankEvaluation.Dataset(null, corpus, List.of()));
        invalid.add(new RerankEvaluation.Dataset("SYNTHETIC", Map.of("a", " "), List.of()));
        invalid.add(dataset(example("q", List.of("missing"), Map.of())));
        invalid.add(dataset(example("q", List.of("a", "a"), Map.of())));
        invalid.add(dataset(example("q", List.of("a"), Map.of("missing", 3))));
        invalid.add(dataset(example("q", List.of("a"), Map.of("a", 4))));
        invalid.add(dataset(example("q", List.of("a"), Map.of("a", -1))));
        invalid.add(dataset(example("q", List.of("a"), Map.of()), example("q", List.of("b"), Map.of())));
        invalid.add(dataset(new RerankEvaluation.Example("q", "test", " ", List.of("a"), Map.of())));
        invalid.add(dataset(new RerankEvaluation.Example("q", "test", "Question", null, Map.of())));
        EvidenceReranker mustNotCall = (query, docs) -> { throw new AssertionError("Unexpected provider call"); };
        for (var data : invalid) {
            assertThatThrownBy(() -> RerankEvaluation.run(data, 5, mustNotCall, true))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> RerankEvaluation.run(dataset(example("q", List.of("a"), Map.of())), 0,
                mustNotCall, true)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void datasetParserRejectsFractionalOrStringGrades() {
        for (String grade : List.of("1.5", "\"3\"")) {
            String json = "{\"provenance\":\"SYNTHETIC\",\"documents\":{\"a\":\"Text\"},\"cases\":["
                    + "{\"id\":\"q\",\"category\":\"test\",\"query\":\"Question\",\"candidates\":[\"a\"],\"relevance\":{\"a\":" + grade + "}}]}";
            assertThatThrownBy(() -> RerankEvaluationRun.datasetMapper().readValue(json, RerankEvaluation.Dataset.class))
                    .isInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class);
        }
    }

    @Test
    @ResourceLock("java.lang.System.properties")
    void liveRunnerRequiresExplicitConsentAndEnforcesBudgetBeforeReadingCredentials() {
        var names = List.of("eval.live", "eval.confirmExternal", "eval.dataset", "eval.maxRequests");
        Map<String, String> saved = new HashMap<>();
        names.forEach(name -> { saved.put(name, System.getProperty(name)); System.clearProperty(name); });
        try {
            System.setProperty("eval.live", "true");
            assertThatThrownBy(() -> new RerankEvaluationRun().replay())
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("eval.confirmExternal");
            System.setProperty("eval.confirmExternal", "true");
            System.setProperty("eval.maxRequests", "1");
            assertThatThrownBy(() -> new RerankEvaluationRun().replay())
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Request budget exceeded");
        } finally {
            saved.forEach((name, value) -> { if (value == null) System.clearProperty(name); else System.setProperty(name, value); });
        }
    }
}
