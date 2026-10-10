package com.yche.springmind.qa;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.yche.springmind.qa.rag.EvidenceSelectionProperties;
import com.yche.springmind.qa.rag.HttpEvidenceReranker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.ai.document.Document;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(OutputCaptureExtension.class)
class HttpEvidenceRerankerTest {
    private HttpServer server;
    private ExecutorService executor;
    private final AtomicReference<String> response = new AtomicReference<>("""
            {"results":[{"index":1,"relevance_score":0.9,"document":{"text":"untrusted text"}},
                         {"index":0,"relevance_score":0.1}]}
            """);
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private final AtomicInteger requests = new AtomicInteger();
    private volatile int status = 200;
    private volatile long delay = 0;
    private volatile long bodyDelay = 0;
    private final List<Document> documents = List.of(
            Document.builder().id("a").text("Authorized first source").metadata(Map.of("documentId", 1L)).build(),
            Document.builder().id("b").text("Authorized second source").metadata(Map.of("documentId", 2L)).build());

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(executor);
        server.createContext("/rerank", exchange -> {
            requests.incrementAndGet();
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            try {
                if (delay > 0) Thread.sleep(delay);
                byte[] body = response.get().getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(status, body.length);
                if (bodyDelay > 0) Thread.sleep(bodyDelay);
                exchange.getResponseBody().write(body);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
        executor.shutdownNow();
    }

    private HttpEvidenceReranker reranker(boolean enabled, Duration timeout) {
        return new HttpEvidenceReranker(new EvidenceSelectionProperties(4096, 30, enabled,
                "http://127.0.0.1:" + server.getAddress().getPort() + "/rerank", "", "test-model", timeout), new ObjectMapper());
    }

    @Test
    void shouldRerankOnlyByValidatedIndicesAndKeepAuthorizedSourceText() {
        try (var reranker = reranker(true, Duration.ofSeconds(2))) {
            var result = reranker.rerank("question", documents);
            assertThat(result.status()).isEqualTo("APPLIED");
            assertThat(result.documents()).extracting(Document::getId).containsExactly("b", "a");
            assertThat(result.documents().getFirst().getText()).isEqualTo("Authorized second source");
            assertThat(result.documents().getFirst().getMetadata()).containsEntry("documentId", 2L).containsEntry("rerankScore", 0.9);
            assertThat(requestBody.get()).contains("test-model", "question", "Authorized first source");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "not-json", "{}", "{\"results\":[]}",
            "{\"results\":[{\"index\":0,\"relevance_score\":0.2},{\"index\":0,\"relevance_score\":0.9}]}",
            "{\"results\":[{\"index\":2,\"relevance_score\":0.2},{\"index\":0,\"relevance_score\":0.9}]}",
            "{\"results\":[{\"index\":0.5,\"relevance_score\":0.2},{\"index\":1,\"relevance_score\":0.9}]}"
    })
    void invalidProviderOutputMustFallBackWithoutDroppingCandidates(String invalid) {
        response.set(invalid);
        try (var reranker = reranker(true, Duration.ofSeconds(2))) {
            var result = reranker.rerank("question", documents);
            assertThat(result.status()).isEqualTo("FALLBACK");
            assertThat(result.documents()).containsExactlyElementsOf(documents);
        }
    }

    @Test
    void timeoutAndServerFailureMustFallBack() {
        status = 503;
        try (var reranker = reranker(true, Duration.ofSeconds(2))) {
            assertThat(reranker.rerank("question", documents).status()).isEqualTo("FALLBACK");
        }
        status = 200;
        delay = 500;
        try (var reranker = reranker(true, Duration.ofMillis(100))) {
            long start = System.nanoTime();
            var result = reranker.rerank("question", documents);
            assertThat(result.status()).isEqualTo("FALLBACK");
            assertThat(result.documents()).containsExactlyElementsOf(documents);
            assertThat((System.nanoTime() - start) / 1_000_000).isLessThan(2000);
        }
    }

    @Test
    void disabledAndSingleCandidateModesMustNotCallProvider() {
        try (var reranker = reranker(false, Duration.ofSeconds(2))) {
            assertThat(reranker.rerank("question", documents).status()).isEqualTo("DISABLED");
        }
        try (var reranker = reranker(true, Duration.ofSeconds(2))) {
            assertThat(reranker.rerank("question", documents.subList(0, 1)).status()).isEqualTo("SKIPPED");
        }
        assertThat(requests).hasValue(0);
    }

    @ParameterizedTest
    @CsvSource({"401,AUTHENTICATION", "403,PERMISSION", "404,ENDPOINT", "405,ENDPOINT",
            "429,RATE_LIMIT", "503,PROVIDER", "400,HTTP_ERROR"})
    void logsSafeActionableHttpDiagnostics(int code, String reason, CapturedOutput output) {
        status = code;
        response.set("private-provider-response");
        try (var reranker = new HttpEvidenceReranker(new EvidenceSelectionProperties(4096, 30, true,
                "http://127.0.0.1:" + server.getAddress().getPort() + "/rerank?secret=private-query",
                "private-api-key", "test-model", Duration.ofSeconds(2)), new ObjectMapper())) {
            assertThat(reranker.rerank("private-question", documents).documents()).containsExactlyElementsOf(documents);
            assertThat(output.getOut()).contains("reason=" + reason, "httpStatus=" + code)
                    .doesNotContain("private-provider-response", "private-query", "private-api-key",
                            "private-question", "Authorized first source");
        }
    }

    @Test
    void distinguishesMalformedResponseFromTimeout(CapturedOutput output) {
        response.set("not-json");
        try (var reranker = reranker(true, Duration.ofSeconds(2))) {
            reranker.rerank("question", documents);
        }
        assertThat(output.getOut()).contains("reason=INVALID_RESPONSE");
        delay = 500;
        try (var reranker = reranker(true, Duration.ofMillis(100))) {
            reranker.rerank("question", documents);
        }
        assertThat(output.getOut()).contains("reason=TIMEOUT");
    }

    @Test
    void slowResponseBodyMustAlsoRespectTimeout() {
        bodyDelay = 500;
        try (var reranker = reranker(true, Duration.ofMillis(100))) {
            long start = System.nanoTime();
            assertThat(reranker.rerank("question", documents).status()).isEqualTo("FALLBACK");
            assertThat((System.nanoTime() - start) / 1_000_000).isLessThan(1500);
        }
    }
}
