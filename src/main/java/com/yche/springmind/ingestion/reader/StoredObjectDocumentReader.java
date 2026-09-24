package com.yche.springmind.ingestion.reader;

import com.yche.springmind.common.exception.BusinessException;
import com.yche.springmind.document.model.entity.DocumentEntity;
import com.yche.springmind.ingestion.parser.factory.DocumentParserFactory;
import com.yche.springmind.ingestion.parser.strategy.DocumentParser;
import com.yche.springmind.storage.service.ObjectStorageService;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 从对象存储读取文档原文件，并包装为 Spring AI Document 输入。
 *
 * <p>位于文档读取阶段：从对象存储取得原始文件，并向 Spring AI 文档处理流水线提供输入。</p>
 */
public class StoredObjectDocumentReader implements DocumentReader {

    private static final String MINIO_SOURCE_PREFIX = "minio://";
    private final ObjectStorageService storageService;
    private final DocumentParserFactory parserFactory;
    private final DocumentEntity documentEntity;

    /**
     * 创建并初始化 {@link StoredObjectDocumentReader}，保存该组件运行所需的依赖与配置。
     * <p>
     * 实现要点：按文档类型解析正文内容。
     *
     * @param storageService 方法参数 {@code storageService}
     * @param parserFactory 方法参数 {@code parserFactory}
     * @param documentEntity 方法参数 {@code documentEntity}
     */
    public StoredObjectDocumentReader(
            ObjectStorageService storageService,
            DocumentParserFactory parserFactory,
            DocumentEntity documentEntity
    ) {
        this.storageService = storageService;
        this.parserFactory = parserFactory;
        this.documentEntity = documentEntity;
    }

    /**
     * 在文档处理链路中执行 {@code get}。
     *
     * @return 符合条件的结果集合；无结果时返回空集合
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    @Override
    public List<Document> get() {
        validateDocumentEntity();
        String bucket = resolveBucket();
        String objectKey = documentEntity.getStorageObjectKey();
        DocumentParser parser = parserFactory.getParser(documentEntity.getFileExt());
        try (InputStream inputStream = storageService.getObject(bucket, objectKey)) {
            String content = parser.parse(inputStream);
            return List.of(buildDocument(content, bucket, objectKey));
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException("读取存储文档失败", exception);
        }
    }

    /**
     * 在文档处理链路中执行 {@code validateDocumentEntity}。
     * <p>
     * 实现要点：先校验输入、状态或业务边界。
     *
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    private void validateDocumentEntity() {
        if (documentEntity == null || documentEntity.getId() == null || documentEntity.getGroupId() == null) {
            throw new BusinessException("读取文档前必须提供 documentId 和 groupId");
        }
    }

    /**
     * 在文档处理链路中执行 {@code resolveBucket}。
     *
     * @return 处理后得到的字符串结果
     */
    private String resolveBucket() {
        if (StringUtils.hasText(documentEntity.getStorageBucket())) {
            return documentEntity.getStorageBucket();
        }
        return storageService.getDefaultBucket();
    }

    /**
     * 在文档处理链路中执行 {@code buildDocument}。
     *
     * @param content 方法参数 {@code content}
     * @param bucket 对象存储桶名称
     * @param objectKey 对象存储中的对象键
     * @return 方法执行结果，具体结构由返回类型 {@code Document} 表示
     */
    private Document buildDocument(String content, String bucket, String objectKey) {
        return Document.builder()
                .id(String.valueOf(documentEntity.getId()))
                .text(content)
                .metadata(buildMetadata(bucket, objectKey))
                .build();
    }

    /**
     * 在文档处理链路中执行 {@code buildMetadata}。
     *
     * @param bucket 对象存储桶名称
     * @param objectKey 对象存储中的对象键
     * @return 方法执行结果，具体结构由返回类型 {@code Map&lt;String, Object&gt;} 表示
     */
    private Map<String, Object> buildMetadata(String bucket, String objectKey) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("groupId", documentEntity.getGroupId());
        metadata.put("documentId", documentEntity.getId());
        metadata.put("fileName", documentEntity.getFileName());
        metadata.put("source", MINIO_SOURCE_PREFIX + bucket + "/" + objectKey);
        return metadata;
    }
}
