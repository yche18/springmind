package com.yche.springmind.ingestion.vector;

import com.yche.springmind.common.exception.BusinessException;
import com.yche.springmind.ingestion.model.entity.DocumentChunkEntity;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 将文档切片写入 pgvector，并在重建或失败时维护旧向量数据的一致性。
 *
 * <p>位于向量入库阶段：把文档切片转换为向量并写入向量存储，同时维护索引一致性。</p>
 */
@Service
public class VectorIngestionService {

    private static final Logger log = LoggerFactory.getLogger(VectorIngestionService.class);
    private static final int DEFAULT_ADD_BATCH_SIZE = 9;
    private static final TypeReference<Map<String, Object>> MAP_TYPE_REFERENCE = new TypeReference<>() { };

    private final VectorStore vectorStore;
    private final int addBatchSize;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 创建并初始化 {@link VectorIngestionService}，保存该组件运行所需的依赖与配置。
     * <p>
     * 实现要点：写入或查询 pgvector 向量索引；先校验输入、状态或业务边界。
     *
     * @param vectorStore 方法参数 {@code vectorStore}
     * @param addBatchSize 方法参数 {@code addBatchSize}
     */
    @Autowired
    public VectorIngestionService(
            VectorStore vectorStore,
            @Value("${ingestion.vector.add-batch-size:${spring.ai.vectorstore.pgvector.max-document-batch-size:9}}")
            int addBatchSize
    ) {
        this.vectorStore = vectorStore;
        this.addBatchSize = normalizeBatchSize(addBatchSize);
    }

    // 保留单测和手工构造入口，生产环境走带配置的构造器。
    /**
     * 创建并初始化 {@link VectorIngestionService}，保存该组件运行所需的依赖与配置。
     * <p>
     * 实现要点：写入或查询 pgvector 向量索引。
     *
     * @param vectorStore 方法参数 {@code vectorStore}
     */
    public VectorIngestionService(VectorStore vectorStore) {
        this(vectorStore, DEFAULT_ADD_BATCH_SIZE);
    }

    /**
     * 完成 {@code ingestChunks} 对应的处理。
     *
     * @param chunks 待处理的文档切片集合
     */
    public void ingestChunks(List<DocumentChunkEntity> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        log.info("开始写入向量: chunkCount={}, addBatchSize={}", chunks.size(), addBatchSize);
        List<Document> documents = chunks.stream().map(this::toVectorDocument).toList();
        deleteExistingVectors(extractDocumentIds(chunks));
        embedAndStore(documents);
        log.info("向量写入结束: chunkCount={}", documents.size());
    }

    /**
     * 完成 {@code deleteDocumentVectors} 对应的处理。
     * <p>
     * 实现要点：写入或查询 pgvector 向量索引；持久化数据库状态变更。
     *
     * @param documentId 文档唯一标识
     */
    public void deleteDocumentVectors(Long documentId) {
        if (documentId == null || documentId <= 0) {
            return;
        }
        Filter.Expression filter = new FilterExpressionBuilder().eq("documentId", documentId).build();
        vectorStore.delete(filter);
        log.info("文档向量删除完成: documentId={}", documentId);
    }

    /**
     * 完成 {@code embedAndStore} 对应的处理。
     * <p>
     * 实现要点：写入或查询 pgvector 向量索引。
     *
     * @param documents 方法参数 {@code documents}
     */
    public void embedAndStore(List<Document> documents) {
        if (CollectionUtils.isEmpty(documents)) {
            return;
        }
        for (int i = 0; i < documents.size(); i += addBatchSize) {
            List<Document> subList = documents.subList(i, Math.min(i + addBatchSize, documents.size()));
            log.info("执行向量批次写入: batchStart={}, batchSize={}", i, subList.size());
            vectorStore.add(subList);
        }
    }

    /**
     * 完成 {@code normalizeBatchSize} 对应的处理。
     * <p>
     * 实现要点：先校验输入、状态或业务边界。
     *
     * @param configuredBatchSize 方法参数 {@code configuredBatchSize}
     * @return 计算或处理得到的数值结果
     */
    private int normalizeBatchSize(int configuredBatchSize) {
        return configuredBatchSize > 0 ? configuredBatchSize : DEFAULT_ADD_BATCH_SIZE;
    }

    /**
     * 完成 {@code toVectorDocument} 对应的处理。
     * <p>
     * 实现要点：先校验输入、状态或业务边界。
     *
     * @param chunk 方法参数 {@code chunk}
     * @return 方法执行结果，具体结构由返回类型 {@code Document} 表示
     */
    private Document toVectorDocument(DocumentChunkEntity chunk) {
        validateChunk(chunk);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("groupId", chunk.getGroupId());
        metadata.put("documentId", chunk.getDocumentId());
        metadata.put("chunkId", chunk.getId());
        metadata.put("chunkIndex", chunk.getChunkIndex());
        metadata.putAll(extractOptionalMetadata(chunk.getMetadataJson()));
        return Document.builder()
                .id(buildStableDocumentId(chunk))
                .text(chunk.getChunkText())
                .metadata(metadata)
                .build();
    }

    /**
     * 完成 {@code deleteExistingVectors} 对应的处理。
     * <p>
     * 实现要点：写入或查询 pgvector 向量索引；持久化数据库状态变更。
     *
     * @param documentIds 文档标识集合
     */
    private void deleteExistingVectors(Set<Long> documentIds) {
        FilterExpressionBuilder builder = new FilterExpressionBuilder();
        for (Long documentId : documentIds) {
            Filter.Expression filter = builder.eq("documentId", documentId).build();
            vectorStore.delete(filter);
        }
    }

    /**
     * 完成 {@code extractDocumentIds} 对应的处理。
     * <p>
     * 实现要点：先校验输入、状态或业务边界。
     *
     * @param chunks 待处理的文档切片集合
     * @return 符合条件的结果集合；无结果时返回空集合
     */
    private Set<Long> extractDocumentIds(List<DocumentChunkEntity> chunks) {
        Set<Long> documentIds = new LinkedHashSet<>();
        for (DocumentChunkEntity chunk : chunks) {
            validateChunk(chunk);
            documentIds.add(chunk.getDocumentId());
        }
        return documentIds;
    }

    /**
     * 完成 {@code buildStableDocumentId} 对应的处理。
     *
     * @param chunk 方法参数 {@code chunk}
     * @return 处理后得到的字符串结果
     */
    private String buildStableDocumentId(DocumentChunkEntity chunk) {
        String rawId = chunk.getDocumentId() + ":" + chunk.getChunkIndex();
        return UUID.nameUUIDFromBytes(rawId.getBytes(StandardCharsets.UTF_8)).toString();
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
        if (chunk == null || chunk.getId() == null) {
            throw new BusinessException("向量写入前必须先完成 chunk 落库");
        }
        if (chunk.getDocumentId() == null || chunk.getChunkIndex() == null) {
            throw new BusinessException("向量写入前必须提供 documentId 和 chunkIndex");
        }
        if (chunk.getChunkText() == null || chunk.getChunkText().isBlank()) {
            throw new BusinessException("空切片不能写入向量库");
        }
    }

    /**
     * 完成 {@code extractOptionalMetadata} 对应的处理。
     * <p>
     * 实现要点：捕获依赖异常并转换、记录或执行降级策略。
     *
     * @param metadataJson 方法参数 {@code metadataJson}
     * @return 方法执行结果，具体结构由返回类型 {@code Map&lt;String, Object&gt;} 表示
     */
    private Map<String, Object> extractOptionalMetadata(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> sourceMetadata = objectMapper.readValue(metadataJson, MAP_TYPE_REFERENCE);
            Map<String, Object> optionalMetadata = new LinkedHashMap<>();
            String fileName = readLegacyCompatibleFileName(sourceMetadata);
            if (fileName != null) {
                optionalMetadata.put("fileName", fileName);
            }
            return optionalMetadata;
        } catch (Exception exception) {
            log.warn("解析 chunk metadataJson 失败，忽略扩展元数据。", exception);
            return Map.of();
        }
    }

    /**
     * 完成 {@code readLegacyCompatibleFileName} 对应的处理。
     *
     * @param sourceMetadata 方法参数 {@code sourceMetadata}
     * @return 处理后得到的字符串结果
     */
    private String readLegacyCompatibleFileName(Map<String, Object> sourceMetadata) {
        Object fileName = sourceMetadata.get("fileName");
        if (fileName instanceof String text && !text.isBlank()) {
            return text.trim();
        }
        Object documentName = sourceMetadata.get("documentName");
        if (documentName instanceof String text && !text.isBlank()) {
            return text.trim();
        }
        return null;
    }
}
