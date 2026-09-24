package com.yche.springmind.document.service;

import com.yche.springmind.common.enums.DocumentStatus;
import com.yche.springmind.common.exception.BusinessException;
import com.yche.springmind.document.mapper.DocumentMapper;
import com.yche.springmind.document.model.entity.DocumentEntity;
import com.yche.springmind.ingestion.mapper.DocumentChunkMapper;
import com.yche.springmind.ingestion.model.entity.DocumentChunkEntity;
import com.yche.springmind.ingestion.service.DocumentIngestionProcessor;
import com.yche.springmind.ingestion.vector.VectorIngestionService;
import com.yche.springmind.retrieval.elasticsearch.ElasticsearchChunkIndexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 在独立异步线程中执行文档入库，并记录成功或失败状态。
 *
 * <p>位于业务服务层：编排领域操作和基础设施调用，并集中维护事务、权限校验及失败处理边界。</p>
 */
@Service
public class DocumentIngestionAsyncService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionAsyncService.class);
    private static final int FAILURE_REASON_MAX_LENGTH = 512;

    private final DocumentMapper documentMapper;
    private final DocumentIngestionProcessor documentIngestionProcessor;
    private final DocumentChunkMapper documentChunkMapper;
    private final VectorIngestionService vectorIngestionService;
    private final ElasticsearchChunkIndexService elasticsearchChunkIndexService;

    /**
     * 创建并初始化 {@link DocumentIngestionAsyncService}，保存该组件运行所需的依赖与配置。
     * <p>
     * 实现要点：写入或查询 pgvector 向量索引；维护或查询 Elasticsearch 关键词索引。
     *
     * @param documentMapper 方法参数 {@code documentMapper}
     * @param documentIngestionProcessor 方法参数 {@code documentIngestionProcessor}
     * @param documentChunkMapper 方法参数 {@code documentChunkMapper}
     * @param vectorIngestionService 方法参数 {@code vectorIngestionService}
     * @param elasticsearchChunkIndexService 方法参数 {@code elasticsearchChunkIndexService}
     */
    public DocumentIngestionAsyncService(
            DocumentMapper documentMapper,
            DocumentIngestionProcessor documentIngestionProcessor,
            DocumentChunkMapper documentChunkMapper,
            VectorIngestionService vectorIngestionService,
            ElasticsearchChunkIndexService elasticsearchChunkIndexService
    ) {
        this.documentMapper = documentMapper;
        this.documentIngestionProcessor = documentIngestionProcessor;
        this.documentChunkMapper = documentChunkMapper;
        this.vectorIngestionService = vectorIngestionService;
        this.elasticsearchChunkIndexService = elasticsearchChunkIndexService;
    }

    /**
     * 执行 {@code ingestDocument} 对应的业务步骤。
     * <p>
     * 实现要点：对可恢复失败执行有限次数重试；使用事务保证多次数据库操作的一致性；先校验输入、状态或业务边界；清洗并规范化解析后的文本。
     *
     * @param documentId 文档唯一标识
     * @param groupId 群组唯一标识
     */
    @Retryable(
            retryFor = RuntimeException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 2000, multiplier = 2.0)
    )
    @Transactional
    public void ingestDocument(Long documentId, Long groupId) {
        DocumentEntity document = requireDocument(documentId, groupId);
        log.info("开始异步执行文档ETL: documentId={}, groupId={}", documentId, groupId);
        cleanupProcessingArtifacts(documentId);
        documentIngestionProcessor.process(documentId, groupId);
        syncSearchIndex(document);
        markDocumentStatus(documentId, groupId, DocumentStatus.READY.name(), null, LocalDateTime.now());
        log.info("异步文档ETL完成: documentId={}, groupId={}, status={}", documentId, groupId, DocumentStatus.READY.name());
    }

    /**
     * 执行 {@code recover} 对应的业务步骤。
     * <p>
     * 实现要点：使用事务保证多次数据库操作的一致性；清洗并规范化解析后的文本。
     *
     * @param exception 方法参数 {@code exception}
     * @param documentId 文档唯一标识
     * @param groupId 群组唯一标识
     */
    @Recover
    @Transactional
    public void recover(RuntimeException exception, Long documentId, Long groupId) {
        log.error("异步文档ETL最终失败: documentId={}, groupId={}, reason={}", documentId, groupId, exception.getMessage(), exception);
        cleanupProcessingArtifacts(documentId);
        markDocumentStatus(
                documentId,
                groupId,
                DocumentStatus.FAILED.name(),
                truncateFailureReason(exception.getMessage()),
                LocalDateTime.now()
        );
    }

    /**
     * 执行 {@code requireDocument} 对应的业务步骤。
     * <p>
     * 实现要点：先校验输入、状态或业务边界；读取数据库中的当前状态。
     *
     * @param documentId 文档唯一标识
     * @param groupId 群组唯一标识
     * @return 方法执行结果，具体结构由返回类型 {@code DocumentEntity} 表示
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    private DocumentEntity requireDocument(Long documentId, Long groupId) {
        DocumentEntity document = documentMapper.selectByIdAndGroupId(documentId, groupId);
        if (document == null) {
            throw new BusinessException("待处理文档不存在");
        }
        return document;
    }

    /**
     * 执行 {@code cleanupProcessingArtifacts} 对应的业务步骤。
     * <p>
     * 实现要点：清洗并规范化解析后的文本；捕获依赖异常并转换、记录或执行降级策略；写入或查询 pgvector 向量索引；维护或查询 Elasticsearch 关键词索引。
     *
     * @param documentId 文档唯一标识
     */
    private void cleanupProcessingArtifacts(Long documentId) {
        try {
            documentChunkMapper.deleteByDocumentId(documentId);
        } catch (RuntimeException exception) {
            log.warn("清理旧 chunk 失败: documentId={}, reason={}", documentId, exception.getMessage());
        }
        try {
            vectorIngestionService.deleteDocumentVectors(documentId);
        } catch (RuntimeException exception) {
            log.warn("清理旧向量失败: documentId={}, reason={}", documentId, exception.getMessage());
        }
        try {
            elasticsearchChunkIndexService.deleteDocumentChunks(documentId);
        } catch (RuntimeException exception) {
            log.warn("清理旧 ES 索引失败: documentId={}, reason={}", documentId, exception.getMessage());
        }
    }

    /**
     * 执行 {@code syncSearchIndex} 对应的业务步骤。
     * <p>
     * 实现要点：读取数据库中的当前状态；维护或查询 Elasticsearch 关键词索引。
     *
     * @param document 当前处理的文档实体
     */
    private void syncSearchIndex(DocumentEntity document) {
        List<DocumentChunkEntity> chunks = documentChunkMapper.selectByDocumentId(document.getId());
        elasticsearchChunkIndexService.indexReadyChunks(document.getFileName(), chunks);
    }

    /**
     * 执行 {@code markDocumentStatus} 对应的业务步骤。
     *
     * @param documentId 文档唯一标识
     * @param groupId 群组唯一标识
     * @param status 目标业务状态
     * @param failureReason 方法参数 {@code failureReason}
     * @param processedAt 方法参数 {@code processedAt}
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    private void markDocumentStatus(
            Long documentId,
            Long groupId,
            String status,
            String failureReason,
            LocalDateTime processedAt
    ) {
        int updated = documentMapper.updateStatus(documentId, groupId, status, failureReason, processedAt);
        if (updated == 0) {
            throw new BusinessException("文档状态更新失败");
        }
    }

    /**
     * 执行 {@code truncateFailureReason} 对应的业务步骤。
     *
     * @param failureReason 方法参数 {@code failureReason}
     * @return 处理后得到的字符串结果
     */
    private String truncateFailureReason(String failureReason) {
        if (failureReason == null || failureReason.isBlank()) {
            return "文档处理失败";
        }
        return failureReason.length() <= FAILURE_REASON_MAX_LENGTH
                ? failureReason
                : failureReason.substring(0, FAILURE_REASON_MAX_LENGTH);
    }
}
