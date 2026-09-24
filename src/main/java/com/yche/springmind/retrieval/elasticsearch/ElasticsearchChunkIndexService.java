package com.yche.springmind.retrieval.elasticsearch;

import com.yche.springmind.common.exception.BusinessException;
import com.yche.springmind.ingestion.model.entity.DocumentChunkEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理文档切片的 Elasticsearch 索引、批量写入、删除和 BM25 关键词检索。
 *
 * <p>封装 Elasticsearch 关键词索引与检索细节，为混合检索提供 BM25 召回结果。</p>
 */
@Service
public class ElasticsearchChunkIndexService {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchChunkIndexService.class);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);
    private static final double KEYWORD_SCORE_REFERENCE = 100D;
    private static final String READY_STATUS = "READY";

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String baseUrl;
    private final String indexName;
    private volatile boolean indexInitialized;

    /**
     * 创建并初始化 {@link ElasticsearchChunkIndexService}，保存该组件运行所需的依赖与配置。
     * <p>
     * 实现要点：维护或查询 Elasticsearch 关键词索引。
     *
     * @param objectMapper 方法参数 {@code objectMapper}
     * @param host 方法参数 {@code host}
     * @param port 方法参数 {@code port}
     * @param scheme 方法参数 {@code scheme}
     * @param indexName 方法参数 {@code indexName}
     */
    @Autowired
    public ElasticsearchChunkIndexService(
            ObjectMapper objectMapper,
            @Value("${elasticsearch.host:localhost}") String host,
            @Value("${elasticsearch.port:9200}") int port,
            @Value("${elasticsearch.scheme:http}") String scheme,
            @Value("${elasticsearch.index-name:springmind_document_chunks}") String indexName
    ) {
        this(
                objectMapper,
                HttpClient.newBuilder()
                        .connectTimeout(REQUEST_TIMEOUT)
                        .build(),
                host,
                port,
                scheme,
                indexName
        );
    }

    /**
     * 创建并初始化 {@link ElasticsearchChunkIndexService}，保存该组件运行所需的依赖与配置。
     *
     * @param objectMapper 方法参数 {@code objectMapper}
     * @param httpClient 方法参数 {@code httpClient}
     * @param host 方法参数 {@code host}
     * @param port 方法参数 {@code port}
     * @param scheme 方法参数 {@code scheme}
     * @param indexName 方法参数 {@code indexName}
     */
    ElasticsearchChunkIndexService(
            ObjectMapper objectMapper,
            HttpClient httpClient,
            String host,
            int port,
            String scheme,
            String indexName
    ) {
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.baseUrl = "%s://%s:%d".formatted(scheme, host, port);
        this.indexName = indexName;
    }

    /**
     * 校验就绪切片后逐条写入 Elasticsearch 关键词索引。
     * <p>
     * 实现要点：先校验输入、状态或业务边界。
     *
     * @param fileName 原始文件名
     * @param chunks 待处理的文档切片集合
     */
    public void indexReadyChunks(String fileName, List<DocumentChunkEntity> chunks) {
        if (!StringUtils.hasText(fileName) || chunks == null || chunks.isEmpty()) {
            return;
        }
        ensureIndexInitialized();
        for (DocumentChunkEntity chunk : chunks) {
            indexChunk(fileName, chunk);
        }
    }

    /**
     * 按文档标识删除 Elasticsearch 中的全部切片；索引不可用时记录告警而不阻断主流程。
     * <p>
     * 实现要点：先校验输入、状态或业务边界；捕获依赖异常并转换、记录或执行降级策略。
     *
     * @param documentId 文档唯一标识
     */
    public void deleteDocumentChunks(Long documentId) {
        if (documentId == null || documentId <= 0) {
            return;
        }
        try {
            ensureIndexInitialized();
            String path = "/%s/_delete_by_query".formatted(indexName);
            Map<String, Object> requestBody = Map.of(
                    "query", Map.of("term", Map.of("documentId", documentId))
            );
            sendJsonRequest("POST", path, requestBody, true);
            log.info("ES 文档切片索引删除完成: documentId={}", documentId);
        } catch (RuntimeException exception) {
            log.warn("ES 文档切片索引删除失败: documentId={}, reason={}", documentId, exception.getMessage());
        }
    }

    /**
     * 在限定群组范围内执行关键词检索，并返回归一化后的候选切片。
     * <p>
     * 实现要点：先校验输入、状态或业务边界；捕获依赖异常并转换、记录或执行降级策略。
     *
     * @param groupId 群组唯一标识
     * @param question 用户提交的自然语言问题
     * @param topK 最多返回的候选结果数量
     * @return 符合条件的结果集合；无结果时返回空集合
     */
    public List<KeywordHit> search(Long groupId, String question, int topK) {
        if (groupId == null || groupId <= 0 || !StringUtils.hasText(question) || topK <= 0) {
            return List.of();
        }
        ensureIndexInitialized();
        Map<String, Object> requestBody = buildKeywordSearchRequestBody(groupId, question, topK);
        try {
            JsonNode root = sendJsonRequest("POST", "/%s/_search".formatted(indexName), requestBody, true);
            JsonNode hitsNode = root.path("hits").path("hits");
            if (!hitsNode.isArray() || hitsNode.isEmpty()) {
                return List.of();
            }
            List<KeywordHit> hits = new ArrayList<>();
            for (JsonNode hitNode : hitsNode) {
                JsonNode sourceNode = hitNode.path("_source");
                double rawScore = hitNode.path("_score").asDouble(0D);
                hits.add(new KeywordHit(
                        sourceNode.path("documentId").asLong(),
                        sourceNode.path("chunkId").asLong(),
                        sourceNode.path("chunkIndex").asInt(),
                        sourceNode.path("fileName").asText(""),
                        sourceNode.path("chunkText").asText(""),
                        rawScore,
                        normalizeKeywordScore(rawScore)
                ));
            }
            return List.copyOf(hits);
        } catch (RuntimeException exception) {
            log.warn(
                    "ES 关键词检索失败，降级为空结果: groupId={}, question='{}', reason={}",
                    groupId,
                    abbreviate(question),
                    exception.getMessage()
            );
            return List.of();
        }
    }

    /**
     * 完成 {@code indexChunk} 对应的处理。
     * <p>
     * 实现要点：先校验输入、状态或业务边界。
     *
     * @param fileName 原始文件名
     * @param chunk 方法参数 {@code chunk}
     */
    private void indexChunk(String fileName, DocumentChunkEntity chunk) {
        validateChunk(chunk);
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("chunkId", chunk.getId());
        requestBody.put("groupId", chunk.getGroupId());
        requestBody.put("documentId", chunk.getDocumentId());
        requestBody.put("chunkIndex", chunk.getChunkIndex());
        requestBody.put("fileName", fileName);
        requestBody.put("chunkText", chunk.getChunkText());
        requestBody.put("status", READY_STATUS);
        requestBody.put("deleted", false);
        sendJsonRequest(
                "PUT",
                "/%s/_doc/%s".formatted(indexName, URLEncoder.encode(String.valueOf(chunk.getId()), StandardCharsets.UTF_8)),
                requestBody,
                false
        );
    }

    /**
     * 完成 {@code validateChunk} 对应的处理。
     * <p>
     * 实现要点：先校验输入、状态或业务边界。
     *
     * @param chunk 方法参数 {@code chunk}
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    private void validateChunk(DocumentChunkEntity chunk) {
        if (chunk == null
                || chunk.getId() == null
                || chunk.getGroupId() == null
                || chunk.getDocumentId() == null
                || chunk.getChunkIndex() == null
                || !StringUtils.hasText(chunk.getChunkText())) {
            throw new BusinessException("ES 索引写入缺少必要 chunk 字段");
        }
    }

    /**
     * 以线程安全方式检查并初始化 Elasticsearch 索引、分词器和字段映射。
     * <p>
     * 实现要点：先校验输入、状态或业务边界。
     */
    private void ensureIndexInitialized() {
        if (indexInitialized) {
            return;
        }
        synchronized (this) {
            if (indexInitialized) {
                return;
            }
            if (!indexExists()) {
                createIndex();
            }
            indexInitialized = true;
        }
    }

    /**
     * 完成 {@code indexExists} 对应的处理。
     * <p>
     * 实现要点：捕获依赖异常并转换、记录或执行降级策略。
     *
     * @return 满足条件时返回 {@code true}，否则返回 {@code false}
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    private boolean indexExists() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/%s".formatted(indexName)))
                    .timeout(REQUEST_TIMEOUT)
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() == 200) {
                return true;
            }
            if (response.statusCode() == 404) {
                return false;
            }
            throw new BusinessException("ES 索引检查失败: " + response.statusCode());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException("ES 索引检查失败", exception);
        } catch (IOException exception) {
            throw new BusinessException("ES 索引检查失败", exception);
        }
    }

    /**
     * 完成 {@code createIndex} 对应的处理。
     */
    private void createIndex() {
        sendJsonRequest("PUT", "/%s".formatted(indexName), buildCreateIndexRequestBody(), false);
        log.info("ES 索引初始化完成: {}", indexName);
    }

    /**
     * 完成 {@code buildCreateIndexRequestBody} 对应的处理。
     *
     * @return 方法执行结果，具体结构由返回类型 {@code Map&lt;String, Object&gt;} 表示
     */
    Map<String, Object> buildCreateIndexRequestBody() {
        return Map.of(
                "settings", Map.of(
                        "analysis", Map.of(
                                "analyzer", Map.of(
                                        "springmind_ik_index", Map.of(
                                                "type", "custom",
                                                "tokenizer", "ik_max_word"
                                        ),
                                        "springmind_ik_search", Map.of(
                                                "type", "custom",
                                                "tokenizer", "ik_smart"
                                        )
                                )
                        )
                ),
                "mappings", Map.of(
                        "properties", Map.of(
                                "groupId", Map.of("type", "long"),
                                "documentId", Map.of("type", "long"),
                                "chunkId", Map.of("type", "long"),
                                "chunkIndex", Map.of("type", "integer"),
                                "status", Map.of("type", "keyword"),
                                "deleted", Map.of("type", "boolean"),
                                "fileName", Map.of(
                                        "type", "text",
                                        "analyzer", "springmind_ik_index",
                                        "search_analyzer", "springmind_ik_search",
                                        "fields", Map.of(
                                                "keyword", Map.of(
                                                        "type", "keyword",
                                                        "ignore_above", 256
                                                )
                                        )
                                ),
                                "chunkText", Map.of(
                                        "type", "text",
                                        "analyzer", "springmind_ik_index",
                                        "search_analyzer", "springmind_ik_search"
                                )
                        )
                )
        );
    }

    /**
     * 完成 {@code buildKeywordSearchRequestBody} 对应的处理。
     *
     * @param groupId 群组唯一标识
     * @param question 用户提交的自然语言问题
     * @param topK 最多返回的候选结果数量
     * @return 方法执行结果，具体结构由返回类型 {@code Map&lt;String, Object&gt;} 表示
     */
    Map<String, Object> buildKeywordSearchRequestBody(Long groupId, String question, int topK) {
        Map<String, Object> boolQuery = Map.of(
                "filter", List.of(
                        Map.of("term", Map.of("groupId", groupId)),
                        Map.of("term", Map.of("status", READY_STATUS)),
                        Map.of("term", Map.of("deleted", false))
                ),
                "should", buildKeywordShouldClauses(question),
                "minimum_should_match", 1
        );
        return Map.of(
                "size", topK,
                "_source", List.of("groupId", "documentId", "chunkId", "chunkIndex", "fileName", "chunkText"),
                "query", Map.of("bool", boolQuery),
                "rescore", Map.of(
                        "window_size", topK,
                        "query", Map.of(
                                "query_weight", 0.2D,
                                "rescore_query_weight", 1.0D,
                                "score_mode", "total",
                                "rescore_query", Map.of(
                                        "bool", Map.of(
                                                "should", buildKeywordRescoreShouldClauses(question),
                                                "minimum_should_match", 1
                                        )
                                )
                        )
                )
        );
    }

    /**
     * 完成 {@code buildKeywordShouldClauses} 对应的处理。
     *
     * @param question 用户提交的自然语言问题
     * @return 符合条件的结果集合；无结果时返回空集合
     */
    private List<Map<String, Object>> buildKeywordShouldClauses(String question) {
        return List.of(
                Map.of("match_phrase", Map.of("fileName", Map.of("query", question, "boost", 8))),
                Map.of("match", Map.of("fileName", Map.of("query", question, "boost", 4))),
                Map.of("match_phrase", Map.of("chunkText", Map.of("query", question, "boost", 6))),
                Map.of("match", Map.of("chunkText", Map.of("query", question, "boost", 3)))
        );
    }

    /**
     * 完成 {@code buildKeywordRescoreShouldClauses} 对应的处理。
     *
     * @param question 用户提交的自然语言问题
     * @return 符合条件的结果集合；无结果时返回空集合
     */
    private List<Map<String, Object>> buildKeywordRescoreShouldClauses(String question) {
        return List.of(
                Map.of("match_phrase", Map.of("fileName", Map.of("query", question, "boost", 8))),
                Map.of("match", Map.of("fileName", Map.of("query", question, "operator", "and", "boost", 5))),
                Map.of("match_phrase", Map.of("chunkText", Map.of("query", question, "boost", 7))),
                Map.of("match", Map.of("chunkText", Map.of("query", question, "operator", "and", "boost", 4)))
        );
    }

    /**
     * 完成 {@code sendJsonRequest} 对应的处理。
     * <p>
     * 实现要点：捕获依赖异常并转换、记录或执行降级策略。
     *
     * @param method 方法参数 {@code method}
     * @param path 方法参数 {@code path}
     * @param requestBody 方法参数 {@code requestBody}
     * @param ignoreMissingIndex 方法参数 {@code ignoreMissingIndex}
     * @return 方法执行结果，具体结构由返回类型 {@code JsonNode} 表示
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    private JsonNode sendJsonRequest(
            String method,
            String path,
            Map<String, Object> requestBody,
            boolean ignoreMissingIndex
    ) {
        try {
            String body = objectMapper.writeValueAsString(requestBody);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (ignoreMissingIndex && response.statusCode() == 404) {
                return objectMapper.createObjectNode();
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessException("ES 请求失败: " + response.statusCode() + ", body=" + response.body());
            }
            return objectMapper.readTree(response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException("ES 请求失败", exception);
        } catch (IOException exception) {
            throw new BusinessException("ES 请求失败", exception);
        }
    }

    /**
     * 完成 {@code abbreviate} 对应的处理。
     * <p>
     * 实现要点：先校验输入、状态或业务边界。
     *
     * @param text 方法参数 {@code text}
     * @return 处理后得到的字符串结果
     */
    private String abbreviate(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 120 ? normalized : normalized.substring(0, 120) + "...";
    }

    /**
     * 完成 {@code normalizeKeywordScore} 对应的处理。
     * <p>
     * 实现要点：先校验输入、状态或业务边界。
     *
     * @param rawScore 方法参数 {@code rawScore}
     * @return 计算或处理得到的数值结果
     */
    private double normalizeKeywordScore(double rawScore) {
        if (rawScore <= 0D) {
            return 0D;
        }
        return Math.min(1D, Math.log1p(rawScore) / Math.log1p(KEYWORD_SCORE_REFERENCE));
    }

    /**
     * 表示一次 BM25 关键词召回命中的切片及其相关性分数。
     *
     * <p>仅在 {@code ElasticsearchChunkIndexService} 的实现过程中使用，用不可变数据结构收拢中间结果，避免参数和值的含义混淆。</p>
     */
    public record KeywordHit(
            Long documentId,
            Long chunkId,
            Integer chunkIndex,
            String fileName,
            String chunkText,
            double rawScore,
            double normalizedScore
    ) {
    }
}
