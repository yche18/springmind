package com.yche.springmind.ingestion.service;

import com.yche.springmind.common.exception.BusinessException;
import com.yche.springmind.document.mapper.DocumentMapper;
import com.yche.springmind.document.model.entity.DocumentEntity;
import com.yche.springmind.ingestion.chunk.ChunkService;
import com.yche.springmind.ingestion.model.entity.DocumentChunkEntity;
import com.yche.springmind.ingestion.parser.factory.DocumentParserFactory;
import com.yche.springmind.ingestion.reader.StoredObjectDocumentReader;
import com.yche.springmind.ingestion.transformer.StructureAwareChunkTransformer;
import com.yche.springmind.ingestion.transformer.TextCleanupTransformer;
import com.yche.springmind.ingestion.vector.VectorIngestionService;
import com.yche.springmind.storage.service.ObjectStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;

import java.util.List;

/**
 * 执行读取、解析、清洗、结构化切片、向量化和关键词索引的完整 ETL 流程。
 *
 * <p>位于业务服务层：编排领域操作和基础设施调用，并集中维护事务、权限校验及失败处理边界。</p>
 */
public class EtlDocumentIngestionProcessor implements DocumentIngestionProcessor {

    private static final Logger log = LoggerFactory.getLogger(EtlDocumentIngestionProcessor.class);
    private static final String DOCUMENT_NOT_FOUND_MESSAGE = "待入库文档不存在";
    private static final int PREVIEW_MAX_LENGTH = 200;

    private final DocumentMapper documentMapper;
    private final ObjectStorageService storageService;
    private final DocumentParserFactory parserFactory;
    private final TextCleanupTransformer textCleanupTransformer;
    private final StructureAwareChunkTransformer chunkTransformer;
    private final ChunkService chunkService;
    private final VectorIngestionService vectorService;

    /**
     * 创建并初始化 {@link EtlDocumentIngestionProcessor}，保存该组件运行所需的依赖与配置。
     * <p>
     * 实现要点：按文档类型解析正文内容；清洗并规范化解析后的文本；将正文切分并保存为可检索片段。
     *
     * @param documentMapper 方法参数 {@code documentMapper}
     * @param storageService 方法参数 {@code storageService}
     * @param parserFactory 方法参数 {@code parserFactory}
     * @param textCleanupTransformer 方法参数 {@code textCleanupTransformer}
     * @param chunkTransformer 方法参数 {@code chunkTransformer}
     * @param chunkService 方法参数 {@code chunkService}
     * @param vectorService 方法参数 {@code vectorService}
     */
    public EtlDocumentIngestionProcessor(
            DocumentMapper documentMapper,
            ObjectStorageService storageService,
            DocumentParserFactory parserFactory,
            TextCleanupTransformer textCleanupTransformer,
            StructureAwareChunkTransformer chunkTransformer,
            ChunkService chunkService,
            VectorIngestionService vectorService
    ) {
        this.documentMapper = documentMapper;
        this.storageService = storageService;
        this.parserFactory = parserFactory;
        this.textCleanupTransformer = textCleanupTransformer;
        this.chunkTransformer = chunkTransformer;
        this.chunkService = chunkService;
        this.vectorService = vectorService;
    }

    /**
     * 执行完整文档 ETL：读取原文、解析清洗、切片持久化，并写入检索索引。
     * <p>
     * 实现要点：按文档类型解析正文内容；清洗并规范化解析后的文本；将正文切分并保存为可检索片段。
     *
     * @param documentId 文档唯一标识
     * @param groupId 群组唯一标识
     */
    @Override
    public void process(Long documentId, Long groupId) {
        log.info("开始执行文档ETL: documentId={}, groupId={}", documentId, groupId);
        DocumentEntity documentEntity = findDocument(documentId, groupId);
        StoredObjectDocumentReader reader =
                new StoredObjectDocumentReader(storageService, parserFactory, documentEntity);
        List<Document> rawDocuments = reader.get();
        log.info("文档读取完成: documentId={}, groupId={}, rawDocuments={}",
                documentId, groupId, rawDocuments.size());
        List<Document> cleanedDocuments = textCleanupTransformer.apply(rawDocuments);
        log.info("文本清洗完成: documentId={}, groupId={}, cleanedDocuments={}",
                documentId, groupId, cleanedDocuments.size());
        persistPreviewText(documentId, groupId, cleanedDocuments);
        List<Document> chunkDocuments = chunkTransformer.apply(cleanedDocuments);
        log.info("文档切片完成: documentId={}, groupId={}, chunkDocuments={}",
                documentId, groupId, chunkDocuments.size());
        List<DocumentChunkEntity> chunks =
                chunkService.saveChunkDocuments(documentId, groupId, chunkDocuments);
        log.info("切片落库完成: documentId={}, groupId={}, persistedChunks={}",
                documentId, groupId, chunks.size());
        vectorService.ingestChunks(chunks);
        log.info("向量写入完成: documentId={}, groupId={}, vectorChunks={}",
                documentId, groupId, chunks.size());
    }

    /**
     * 执行 {@code findDocument} 对应的业务步骤。
     * <p>
     * 实现要点：读取数据库中的当前状态。
     *
     * @param documentId 文档唯一标识
     * @param groupId 群组唯一标识
     * @return 查询得到的文档结果
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    private DocumentEntity findDocument(Long documentId, Long groupId) {
        DocumentEntity documentEntity = documentMapper.selectByIdAndGroupId(documentId, groupId);
        if (documentEntity == null) {
            throw new BusinessException(DOCUMENT_NOT_FOUND_MESSAGE);
        }
        return documentEntity;
    }

    /**
     * 执行 {@code persistPreviewText} 对应的业务步骤。
     * <p>
     * 实现要点：清洗并规范化解析后的文本。
     *
     * @param documentId 文档唯一标识
     * @param groupId 群组唯一标识
     * @param cleanedDocuments 方法参数 {@code cleanedDocuments}
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    private void persistPreviewText(Long documentId, Long groupId, List<Document> cleanedDocuments) {
        String previewText = cleanedDocuments.stream()
                .map(Document::getText)
                .filter(text -> text != null && !text.isBlank())
                .reduce((left, right) -> left + "\n" + right)
                .map(String::trim)
                .map(this::truncatePreviewText)
                .orElse(null);
        int updated = documentMapper.updatePreviewText(documentId, groupId, previewText);
        if (updated == 0) {
            throw new BusinessException("文档预览写入失败");
        }
    }

    /**
     * 执行 {@code truncatePreviewText} 对应的业务步骤。
     *
     * @param previewText 方法参数 {@code previewText}
     * @return 处理后得到的字符串结果
     */
    private String truncatePreviewText(String previewText) {
        if (previewText.length() <= PREVIEW_MAX_LENGTH) {
            return previewText;
        }
        return previewText.substring(0, PREVIEW_MAX_LENGTH);
    }
}
