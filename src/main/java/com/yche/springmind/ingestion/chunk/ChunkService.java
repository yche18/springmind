package com.yche.springmind.ingestion.chunk;

import com.yche.springmind.common.exception.BusinessException;
import com.yche.springmind.ingestion.mapper.DocumentChunkMapper;
import com.yche.springmind.ingestion.model.entity.DocumentChunkEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 负责文档切片的批量保存、查询、清理和相邻切片扩展。
 *
 * <p>位于切片管理层：维护文档切片的持久化、查询和生命周期操作。</p>
 */
@Service
public class ChunkService {

    private static final Logger log = LoggerFactory.getLogger(ChunkService.class);
    private static final int CHUNK_SUMMARY_LENGTH = 120;
    private static final String DEFAULT_CHUNK_STRATEGY = "spring-ai-document";
    private static final String EMPTY_CHUNK_DOCUMENTS_MESSAGE = "文档切片结果为空，无法持久化";
    private static final int INSERT_BATCH_PARAMETER_COUNT = 10;
    private static final int POSTGRES_PARAMETER_LIMIT = 65_535;
    private static final int MAX_INSERT_BATCH_SIZE = POSTGRES_PARAMETER_LIMIT / INSERT_BATCH_PARAMETER_COUNT;

    private final DocumentChunkMapper documentChunkMapper;
    private final ObjectMapper objectMapper;

    /**
     * 创建并初始化 {@link ChunkService}，保存该组件运行所需的依赖与配置。
     *
     * @param documentChunkMapper 方法参数 {@code documentChunkMapper}
     * @param objectMapper 方法参数 {@code objectMapper}
     */
    public ChunkService(DocumentChunkMapper documentChunkMapper, ObjectMapper objectMapper) {
        this.documentChunkMapper = documentChunkMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 完成 {@code saveChunkDocuments} 对应的处理。
     * <p>
     * 实现要点：使用事务保证多次数据库操作的一致性；先校验输入、状态或业务边界。
     *
     * @param documentId 文档唯一标识
     * @param groupId 群组唯一标识
     * @param documents 方法参数 {@code documents}
     * @return 符合条件的结果集合；无结果时返回空集合
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    @Transactional
    public List<DocumentChunkEntity> saveChunkDocuments(Long documentId, Long groupId, List<Document> documents) {
        validateIdentifiers(documentId, groupId);
        List<DocumentChunkEntity> chunks = buildChunkDocuments(documentId, groupId, documents);
        if (chunks.isEmpty()) {
            throw new BusinessException(EMPTY_CHUNK_DOCUMENTS_MESSAGE);
        }
        log.info("开始保存切片: documentId={}, groupId={}, chunkCount={}", documentId, groupId, chunks.size());
        documentChunkMapper.deleteByDocumentId(documentId);
        persistChunkBatches(chunks);
        backfillChunkIds(documentId, chunks);
        log.info("切片保存完成: documentId={}, groupId={}, chunkCount={}", documentId, groupId, chunks.size());
        return chunks;
    }

    /**
     * 完成 {@code persistChunkBatches} 对应的处理。
     *
     * @param chunks 待处理的文档切片集合
     */
    private void persistChunkBatches(List<DocumentChunkEntity> chunks) {
        if (chunks.size() <= MAX_INSERT_BATCH_SIZE) {
            documentChunkMapper.insertBatch(chunks);
            return;
        }
        for (int start = 0; start < chunks.size(); start += MAX_INSERT_BATCH_SIZE) {
            int end = Math.min(start + MAX_INSERT_BATCH_SIZE, chunks.size());
            documentChunkMapper.insertBatch(chunks.subList(start, end));
        }
    }

    /**
     * 完成 {@code backfillChunkIds} 对应的处理。
     * <p>
     * 实现要点：读取数据库中的当前状态。
     *
     * @param documentId 文档唯一标识
     * @param chunks 待处理的文档切片集合
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    private void backfillChunkIds(Long documentId, List<DocumentChunkEntity> chunks) {
        if (chunks.stream().allMatch(chunk -> chunk.getId() != null)) {
            return;
        }
        List<DocumentChunkEntity> persistedChunks = documentChunkMapper.selectByDocumentId(documentId);
        if (persistedChunks.size() != chunks.size()) {
            throw new BusinessException("批量保存切片后回填主键失败");
        }
        Map<Integer, Long> chunkIdByIndex = new HashMap<>();
        for (DocumentChunkEntity persistedChunk : persistedChunks) {
            if (persistedChunk.getChunkIndex() == null || persistedChunk.getId() == null) {
                throw new BusinessException("批量保存切片后返回的主键数据不完整");
            }
            chunkIdByIndex.put(persistedChunk.getChunkIndex(), persistedChunk.getId());
        }
        for (DocumentChunkEntity chunk : chunks) {
            Long chunkId = chunkIdByIndex.get(chunk.getChunkIndex());
            if (chunkId == null) {
                throw new BusinessException("批量保存切片后无法匹配 chunk 主键");
            }
            chunk.setId(chunkId);
        }
    }

    /**
     * 完成 {@code validateIdentifiers} 对应的处理。
     * <p>
     * 实现要点：先校验输入、状态或业务边界。
     *
     * @param documentId 文档唯一标识
     * @param groupId 群组唯一标识
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    private void validateIdentifiers(Long documentId, Long groupId) {
        if (documentId == null || groupId == null) {
            throw new BusinessException("切片前必须提供 documentId 和 groupId");
        }
    }

    /**
     * 完成 {@code buildChunkDocuments} 对应的处理。
     * <p>
     * 实现要点：先校验输入、状态或业务边界。
     *
     * @param documentId 文档唯一标识
     * @param groupId 群组唯一标识
     * @param documents 方法参数 {@code documents}
     * @return 符合条件的结果集合；无结果时返回空集合
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    private List<DocumentChunkEntity> buildChunkDocuments(Long documentId, Long groupId, List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            throw new BusinessException(EMPTY_CHUNK_DOCUMENTS_MESSAGE);
        }
        List<DocumentChunkEntity> chunks = new ArrayList<>();
        int fallbackStart = 0;
        for (Document document : documents) {
            String chunkText = normalizeChunkDocumentText(document);
            if (chunkText.isBlank()) {
                continue;
            }
            ChunkRange range = resolveChunkRange(document.getMetadata(), chunkText, fallbackStart);
            chunks.add(buildChunk(documentId, groupId, chunks.size(), chunkText, range.charStart(),
                    range.charEnd(), document.getMetadata()));
            fallbackStart = range.charEnd();
        }
        return chunks;
    }

    /**
     * 完成 {@code normalizeChunkDocumentText} 对应的处理。
     * <p>
     * 实现要点：先校验输入、状态或业务边界。
     *
     * @param document 当前处理的文档实体
     * @return 处理后得到的字符串结果
     */
    private String normalizeChunkDocumentText(Document document) {
        if (document == null || document.getText() == null) {
            return "";
        }
        return document.getText()
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .trim();
    }

    /**
     * 完成 {@code resolveChunkRange} 对应的处理。
     *
     * @param metadata 方法参数 {@code metadata}
     * @param chunkText 方法参数 {@code chunkText}
     * @param fallbackStart 方法参数 {@code fallbackStart}
     * @return 方法执行结果，具体结构由返回类型 {@code ChunkRange} 表示
     */
    private ChunkRange resolveChunkRange(Map<String, Object> metadata, String chunkText, int fallbackStart) {
        ChunkRange fallbackRange = fallbackRange(fallbackStart, chunkText.length());
        Integer charStart = readMetadataInt(metadata, "charStart");
        Integer charEnd = readMetadataInt(metadata, "charEnd");
        int chunkLength = chunkText.length();
        if (charStart != null && charEnd != null) {
            return isTrustedRange(charStart, charEnd, chunkLength)
                    ? new ChunkRange(charStart, charEnd)
                    : fallbackRange;
        }
        if (charStart != null) {
            return isTrustedStart(charStart, chunkLength)
                    ? new ChunkRange(charStart, safeAdd(charStart, chunkLength))
                    : fallbackRange;
        }
        if (charEnd != null) {
            return isTrustedEnd(charEnd, chunkLength)
                    ? new ChunkRange(charEnd - chunkLength, charEnd)
                    : fallbackRange;
        }
        return fallbackRange;
    }

    /**
     * 完成 {@code fallbackRange} 对应的处理。
     *
     * @param fallbackStart 方法参数 {@code fallbackStart}
     * @param chunkLength 方法参数 {@code chunkLength}
     * @return 方法执行结果，具体结构由返回类型 {@code ChunkRange} 表示
     */
    private ChunkRange fallbackRange(int fallbackStart, int chunkLength) {
        int safeStart = Math.max(0, fallbackStart);
        return new ChunkRange(safeStart, safeAdd(safeStart, chunkLength));
    }

    /**
     * 判断当前数据是否满足 {@code trustedRange} 条件。
     *
     * @param charStart 方法参数 {@code charStart}
     * @param charEnd 方法参数 {@code charEnd}
     * @param chunkLength 方法参数 {@code chunkLength}
     * @return 满足条件时返回 {@code true}，否则返回 {@code false}
     */
    private boolean isTrustedRange(int charStart, int charEnd, int chunkLength) {
        return isTrustedStart(charStart, chunkLength) && charEnd >= charStart + chunkLength;
    }

    /**
     * 判断当前数据是否满足 {@code trustedStart} 条件。
     *
     * @param charStart 方法参数 {@code charStart}
     * @param chunkLength 方法参数 {@code chunkLength}
     * @return 满足条件时返回 {@code true}，否则返回 {@code false}
     */
    private boolean isTrustedStart(int charStart, int chunkLength) {
        return (long) charStart + chunkLength <= Integer.MAX_VALUE;
    }

    /**
     * 判断当前数据是否满足 {@code trustedEnd} 条件。
     *
     * @param charEnd 方法参数 {@code charEnd}
     * @param chunkLength 方法参数 {@code chunkLength}
     * @return 满足条件时返回 {@code true}，否则返回 {@code false}
     */
    private boolean isTrustedEnd(int charEnd, int chunkLength) {
        return charEnd >= chunkLength;
    }

    /**
     * 完成 {@code safeAdd} 对应的处理。
     *
     * @param value 方法参数 {@code value}
     * @param delta 方法参数 {@code delta}
     * @return 计算或处理得到的数值结果
     */
    private int safeAdd(int value, int delta) {
        return (int) Math.min(Integer.MAX_VALUE, (long) Math.max(0, value) + delta);
    }

    /**
     * 完成 {@code buildChunk} 对应的处理。
     *
     * @param documentId 文档唯一标识
     * @param groupId 群组唯一标识
     * @param chunkIndex 方法参数 {@code chunkIndex}
     * @param chunkText 方法参数 {@code chunkText}
     * @param charStart 方法参数 {@code charStart}
     * @param charEnd 方法参数 {@code charEnd}
     * @param metadata 方法参数 {@code metadata}
     * @return 方法执行结果，具体结构由返回类型 {@code DocumentChunkEntity} 表示
     */
    private DocumentChunkEntity buildChunk(Long documentId, Long groupId, int chunkIndex, String chunkText,
                                           int charStart, int charEnd, Map<String, Object> metadata) {
        LocalDateTime now = LocalDateTime.now();
        DocumentChunkEntity chunk = new DocumentChunkEntity();
        chunk.setDocumentId(documentId);
        chunk.setGroupId(groupId);
        chunk.setChunkIndex(chunkIndex);
        chunk.setChunkText(chunkText);
        chunk.setChunkSummary(buildSummary(chunkText));
        chunk.setCharStart(charStart);
        chunk.setCharEnd(charEnd);
        chunk.setMetadataJson(buildMetadataJson(documentId, groupId, chunkIndex, charStart, charEnd, metadata));
        chunk.setCreatedAt(now);
        chunk.setUpdatedAt(now);
        return chunk;
    }

    /**
     * 完成 {@code buildSummary} 对应的处理。
     *
     * @param chunkText 方法参数 {@code chunkText}
     * @return 处理后得到的字符串结果
     */
    private String buildSummary(String chunkText) {
        if (chunkText.length() <= CHUNK_SUMMARY_LENGTH) {
            return chunkText;
        }
        return chunkText.substring(0, CHUNK_SUMMARY_LENGTH) + "...";
    }

    /**
     * 完成 {@code buildMetadataJson} 对应的处理。
     *
     * @param documentId 文档唯一标识
     * @param groupId 群组唯一标识
     * @param chunkIndex 方法参数 {@code chunkIndex}
     * @param charStart 方法参数 {@code charStart}
     * @param charEnd 方法参数 {@code charEnd}
     * @return 处理后得到的字符串结果
     */
    private String buildMetadataJson(Long documentId, Long groupId, int chunkIndex, int charStart, int charEnd) {
        return buildMetadataJson(documentId, groupId, chunkIndex, charStart, charEnd, Map.of());
    }

    /**
     * 完成 {@code buildMetadataJson} 对应的处理。
     * <p>
     * 实现要点：捕获依赖异常并转换、记录或执行降级策略。
     *
     * @param documentId 文档唯一标识
     * @param groupId 群组唯一标识
     * @param chunkIndex 方法参数 {@code chunkIndex}
     * @param charStart 方法参数 {@code charStart}
     * @param charEnd 方法参数 {@code charEnd}
     * @param sourceMetadata 方法参数 {@code sourceMetadata}
     * @return 处理后得到的字符串结果
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    private String buildMetadataJson(Long documentId, Long groupId, int chunkIndex, int charStart, int charEnd,
                                     Map<String, Object> sourceMetadata) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (sourceMetadata != null && !sourceMetadata.isEmpty()) {
            metadata.putAll(sourceMetadata);
        }
        metadata.put("documentId", documentId);
        metadata.put("groupId", groupId);
        metadata.put("chunkIndex", chunkIndex);
        metadata.put("charStart", charStart);
        metadata.put("charEnd", charEnd);
        metadata.put("sectionPath", readMetadataString(sourceMetadata, "sectionPath"));
        metadata.put("chunkStrategy", readMetadataString(sourceMetadata, "chunkStrategy", DEFAULT_CHUNK_STRATEGY));
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("文档切片元数据序列化失败", exception);
        }
    }

    /**
     * 完成 {@code readMetadataInt} 对应的处理。
     * <p>
     * 实现要点：捕获依赖异常并转换、记录或执行降级策略。
     *
     * @param metadata 方法参数 {@code metadata}
     * @param key 方法参数 {@code key}
     * @return 计算或处理得到的数值结果
     */
    private Integer readMetadataInt(Map<String, Object> metadata, String key) {
        if (metadata == null || !metadata.containsKey(key) || metadata.get(key) == null) {
            return null;
        }
        Object value = metadata.get(key);
        if (value instanceof Number || value instanceof String text && !text.isBlank()) {
            try {
                BigDecimal decimal = new BigDecimal(String.valueOf(value).trim());
                if (decimal.signum() < 0 || decimal.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) > 0) {
                    return null;
                }
                return decimal.stripTrailingZeros().scale() <= 0 ? decimal.intValueExact() : null;
            } catch (NumberFormatException | ArithmeticException ignored) {
                return null;
            }
        }
        return null;
    }

    /**
     * 完成 {@code readMetadataString} 对应的处理。
     *
     * @param metadata 方法参数 {@code metadata}
     * @param key 方法参数 {@code key}
     * @return 处理后得到的字符串结果
     */
    private String readMetadataString(Map<String, Object> metadata, String key) {
        return readMetadataString(metadata, key, null);
    }

    /**
     * 完成 {@code readMetadataString} 对应的处理。
     *
     * @param metadata 方法参数 {@code metadata}
     * @param key 方法参数 {@code key}
     * @param defaultValue 方法参数 {@code defaultValue}
     * @return 处理后得到的字符串结果
     */
    private String readMetadataString(Map<String, Object> metadata, String key, String defaultValue) {
        if (metadata == null || !metadata.containsKey(key)) {
            return defaultValue;
        }
        Object value = metadata.get(key);
        return value == null ? defaultValue : String.valueOf(value);
    }

    /**
     * 表示需要批量查询或扩展的一段连续切片序号范围。
     *
     * <p>仅在 {@code ChunkService} 的实现过程中使用，用不可变数据结构收拢中间结果，避免参数和值的含义混淆。</p>
     */
    private record ChunkRange(int charStart, int charEnd) {}
}
