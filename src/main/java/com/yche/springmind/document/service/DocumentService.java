package com.yche.springmind.document.service;

import com.yche.springmind.common.enums.DocumentStatus;
import com.yche.springmind.common.exception.BusinessException;
import com.yche.springmind.document.mapper.DocumentMapper;
import com.yche.springmind.document.model.dto.DocumentQuery;
import com.yche.springmind.document.model.dto.UploadDocumentRequest;
import com.yche.springmind.document.model.entity.DocumentEntity;
import com.yche.springmind.document.model.vo.DocumentListItemVO;
import com.yche.springmind.document.model.vo.DocumentPreviewVO;
import com.yche.springmind.groupmembership.service.GroupMembershipService;
import com.yche.springmind.identity.service.CurrentUserService;
import com.yche.springmind.ingestion.vector.VectorIngestionService;
import com.yche.springmind.retrieval.elasticsearch.ElasticsearchChunkIndexService;
import com.yche.springmind.storage.service.ObjectStorageService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 管理文档查询、预览、删除和上传完成后的持久化，并维护权限与生命周期规则。
 *
 * <p>位于业务服务层：编排领域操作和基础设施调用，并集中维护事务、权限校验及失败处理边界。</p>
 */
@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);
    private static final int PREVIEW_MAX_LENGTH = 200;
    private static final int MAX_FILE_NAME_LENGTH = 255;
    private static final int MAX_CONTENT_TYPE_LENGTH = 128;
    private static final int MAX_FILE_EXT_LENGTH = 16;
    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("txt", "md", "pdf", "docx");

    private final DocumentMapper documentMapper;
    private final GroupMembershipService groupMembershipService;
    private final CurrentUserService currentUserService;
    private final ObjectStorageService objectStorageService;
    private final VectorIngestionService vectorIngestionService;
    private final ElasticsearchChunkIndexService elasticsearchChunkIndexService;
    private final ApplicationEventPublisher applicationEventPublisher;

    /**
     * 创建并初始化 {@link DocumentService}，保存该组件运行所需的依赖与配置。
     * <p>
     * 实现要点：校验群组成员关系和角色权限；解析并确认当前登录用户；读写 MinIO 对象存储中的原始文件；写入或查询 pgvector 向量索引；维护或查询 Elasticsearch 关键词索引。
     *
     * @param documentMapper 方法参数 {@code documentMapper}
     * @param groupMembershipService 方法参数 {@code groupMembershipService}
     * @param currentUserService 方法参数 {@code currentUserService}
     * @param objectStorageService 方法参数 {@code objectStorageService}
     * @param vectorIngestionService 方法参数 {@code vectorIngestionService}
     * @param elasticsearchChunkIndexService 方法参数 {@code elasticsearchChunkIndexService}
     * @param applicationEventPublisher 方法参数 {@code applicationEventPublisher}
     */
    public DocumentService(
            DocumentMapper documentMapper,
            GroupMembershipService groupMembershipService,
            CurrentUserService currentUserService,
            ObjectStorageService objectStorageService,
            VectorIngestionService vectorIngestionService,
            ElasticsearchChunkIndexService elasticsearchChunkIndexService,
            ApplicationEventPublisher applicationEventPublisher
    ) {
        this.documentMapper = documentMapper;
        this.groupMembershipService = groupMembershipService;
        this.currentUserService = currentUserService;
        this.objectStorageService = objectStorageService;
        this.vectorIngestionService = vectorIngestionService;
        this.elasticsearchChunkIndexService = elasticsearchChunkIndexService;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    /**
     * 接收并保存文档原文件与元数据，随后发布异步 ETL 入库事件。
     * <p>
     * 实现要点：使用事务保证多次数据库操作的一致性；先校验输入、状态或业务边界；读写 MinIO 对象存储中的原始文件；捕获依赖异常并转换、记录或执行降级策略。
     *
     * @param request 已经通过控制器基础校验的请求对象
     * @param uploadRequest 上传请求参数
     * @return 计算或处理得到的数值结果
     */
    @Transactional
    public Long uploadDocument(HttpServletRequest request, UploadDocumentRequest uploadRequest) {
        Long groupId = requireGroupId(uploadRequest.getGroupId());
        CurrentUserService.CurrentUser currentUser = requireGroupOwner(request, groupId);
        MultipartFile file = requireValidFile(uploadRequest.getFile());
        String fileName = extractFileName(file);
        String fileExt = extractFileExt(fileName);
        String bucket = objectStorageService.getDefaultBucket();
        String objectKey = buildObjectKey(groupId, currentUser.userId(), fileExt);
        DocumentEntity document = null;
        log.info("开始上传文档: groupId={}, userId={}, fileName={}, size={}, objectKey={}",
                groupId, currentUser.userId(), fileName, file.getSize(), objectKey);
        uploadFile(bucket, objectKey, file);
        log.info("对象存储上传完成: groupId={}, objectKey={}", groupId, objectKey);
        try {
            document = persistAndFinalizeUploadedDocument(new FinalizedUploadCommand(
                    groupId,
                    currentUser.userId(),
                    fileName,
                    fileExt,
                    normalizeContentType(file.getContentType()),
                    file.getSize(),
                    null,
                    bucket,
                    objectKey
            ));
            return document.getId();
        } catch (RuntimeException exception) {
            log.error("文档上传链路失败: groupId={}, objectKey={}, reason={}",
                    groupId, objectKey, exception.getMessage(), exception);
            compensateExternalIndexes(document);
            compensateUploadedObject(bucket, objectKey, exception);
            throw exception;
        }
    }

    /**
     * 执行 {@code createInstantUploadedDocument} 对应的业务步骤。
     * <p>
     * 实现要点：使用事务保证多次数据库操作的一致性；先校验输入、状态或业务边界。
     *
     * @param groupId 群组唯一标识
     * @param userId 用户唯一标识
     * @param existingDocument 方法参数 {@code existingDocument}
     * @param fileName 原始文件名
     * @return 计算或处理得到的数值结果
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    @Transactional
    public Long createInstantUploadedDocument(
            Long groupId,
            Long userId,
            DocumentEntity existingDocument,
            String fileName
    ) {
        if (existingDocument == null) {
            throw new BusinessException("复用文档不存在");
        }
        DocumentEntity document = persistAndFinalizeUploadedDocument(new FinalizedUploadCommand(
                requireGroupId(groupId),
                requirePositiveUserId(userId),
                validateReusableFileName(fileName),
                requireText(existingDocument.getFileExt(), "文件扩展名非法"),
                normalizeContentType(existingDocument.getContentType()),
                requirePositiveFileSize(existingDocument.getFileSize()),
                existingDocument.getFileHash(),
                requireText(existingDocument.getStorageBucket(), "对象存储桶非法"),
                requireText(existingDocument.getStorageObjectKey(), "对象存储路径非法")
        ));
        return document.getId();
    }

    /**
     * 执行 {@code finalizeUploadedDocument} 对应的业务步骤。
     * <p>
     * 实现要点：使用事务保证多次数据库操作的一致性；先校验输入、状态或业务边界。
     *
     * @param groupId 群组唯一标识
     * @param userId 用户唯一标识
     * @param fileName 原始文件名
     * @param fileExt 方法参数 {@code fileExt}
     * @param contentType 文件的 MIME 类型
     * @param fileSize 方法参数 {@code fileSize}
     * @param fileHash 方法参数 {@code fileHash}
     * @param bucket 对象存储桶名称
     * @param objectKey 对象存储中的对象键
     * @return 计算或处理得到的数值结果
     */
    @Transactional
    public Long finalizeUploadedDocument(
            Long groupId,
            Long userId,
            String fileName,
            String fileExt,
            String contentType,
            Long fileSize,
            String fileHash,
            String bucket,
            String objectKey
    ) {
        DocumentEntity document = persistAndFinalizeUploadedDocument(new FinalizedUploadCommand(
                requireGroupId(groupId),
                requirePositiveUserId(userId),
                normalizeFileName(fileName),
                requireText(fileExt, "文件扩展名非法"),
                normalizeContentType(contentType),
                requirePositiveFileSize(fileSize),
                fileHash,
                requireText(bucket, "对象存储桶非法"),
                requireText(objectKey, "对象存储路径非法")
        ));
        return document.getId();
    }

    /**
     * 执行 {@code listDocuments} 对应的业务步骤。
     * <p>
     * 实现要点：先校验输入、状态或业务边界。
     *
     * @param request 已经通过控制器基础校验的请求对象
     * @param query 用于检索或筛选的查询条件
     * @return 符合条件的结果集合；无结果时返回空集合
     */
    public List<DocumentListItemVO> listDocuments(HttpServletRequest request, DocumentQuery query) {
        DocumentQuery validatedQuery = normalizeQuery(request, query);
        return documentMapper.selectReadableDocuments(validatedQuery);
    }

    /**
     * 校验群组所有者权限后软删除文档，并清理向量与关键词索引。
     * <p>
     * 实现要点：先校验输入、状态或业务边界；写入或查询 pgvector 向量索引；维护或查询 Elasticsearch 关键词索引。
     *
     * @param request 已经通过控制器基础校验的请求对象
     * @param groupId 群组唯一标识
     * @param documentId 文档唯一标识
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    public void softDeleteDocument(HttpServletRequest request, Long groupId, Long documentId) {
        requireGroupOwner(request, requireGroupId(groupId));
        if (documentId == null || documentId <= 0) {
            throw new BusinessException("文档ID非法");
        }
        if (documentMapper.markDeleted(documentId, groupId) == 0) {
            throw new BusinessException("文档不存在或已删除");
        }
        vectorIngestionService.deleteDocumentVectors(documentId);
        elasticsearchChunkIndexService.deleteDocumentChunks(documentId);
    }

    /**
     * 仅允许失败文档重新进入处理状态，并重新发布异步入库事件。
     * <p>
     * 实现要点：使用事务保证多次数据库操作的一致性；先校验输入、状态或业务边界；读取数据库中的当前状态。
     *
     * @param request 已经通过控制器基础校验的请求对象
     * @param groupId 群组唯一标识
     * @param documentId 文档唯一标识
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    @Transactional
    public void retryFailedDocumentIngestion(HttpServletRequest request, Long groupId, Long documentId) {
        Long requiredGroupId = requireGroupId(groupId);
        requireGroupOwner(request, requiredGroupId);
        if (documentId == null || documentId <= 0) {
            throw new BusinessException("文档ID非法");
        }
        DocumentEntity document = documentMapper.selectByIdAndGroupId(documentId, requiredGroupId);
        if (document == null) {
            throw new BusinessException("文档不存在或已删除");
        }
        if (!DocumentStatus.FAILED.name().equals(document.getStatus())) {
            throw new BusinessException("仅失败文档支持重新处理");
        }
        int updated = documentMapper.updateStatus(
                documentId,
                requiredGroupId,
                DocumentStatus.PROCESSING.name(),
                null,
                null
        );
        if (updated == 0) {
            throw new BusinessException("重置文档状态失败");
        }
        publishIngestionRequestedEvent(documentId, requiredGroupId);
    }

    /**
     * 执行 {@code previewDocument} 对应的业务步骤。
     * <p>
     * 实现要点：先校验输入、状态或业务边界；校验群组成员关系和角色权限；读取数据库中的当前状态。
     *
     * @param request 已经通过控制器基础校验的请求对象
     * @param groupId 群组唯一标识
     * @param documentId 文档唯一标识
     * @return 方法执行结果，具体结构由返回类型 {@code DocumentPreviewVO} 表示
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    public DocumentPreviewVO previewDocument(HttpServletRequest request, Long groupId, Long documentId) {
        Long requiredGroupId = requireGroupId(groupId);
        groupMembershipService.requireGroupReadable(request, requiredGroupId);
        if (documentId == null || documentId <= 0) {
            throw new BusinessException("文档ID非法");
        }
        DocumentEntity document = documentMapper.selectByIdAndGroupId(documentId, requiredGroupId);
        if (document == null) {
            throw new BusinessException("文档不存在或已删除");
        }
        if (!DocumentStatus.READY.name().equals(document.getStatus())) {
            throw new BusinessException("文档尚未就绪，暂不可预览");
        }
        if (!StringUtils.hasText(document.getPreviewText())) {
            throw new BusinessException("文档暂无可预览内容");
        }
        DocumentPreviewVO preview = new DocumentPreviewVO();
        preview.setDocumentId(document.getId());
        preview.setFileName(document.getFileName());
        preview.setPreviewText(trimPreviewText(document.getPreviewText()));
        return preview;
    }

    /**
     * 执行 {@code requireGroupId} 对应的业务步骤。
     * <p>
     * 实现要点：先校验输入、状态或业务边界。
     *
     * @param groupId 群组唯一标识
     * @return 计算或处理得到的数值结果
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    private Long requireGroupId(Long groupId) {
        if (groupId == null || groupId <= 0) {
            throw new BusinessException("groupId 非法");
        }
        return groupId;
    }

    /**
     * 执行 {@code requireGroupOwner} 对应的业务步骤。
     * <p>
     * 实现要点：先校验输入、状态或业务边界；校验群组成员关系和角色权限。
     *
     * @param request 已经通过控制器基础校验的请求对象
     * @param groupId 群组唯一标识
     * @return 方法执行结果，具体结构由返回类型 {@code CurrentUserService.CurrentUser} 表示
     */
    private CurrentUserService.CurrentUser requireGroupOwner(HttpServletRequest request, Long groupId) {
        CurrentUserService.CurrentUser currentUser = groupMembershipService.requireGroupReadable(request, groupId);
        groupMembershipService.requireGroupOwner(request, groupId);
        return currentUser;
    }

    /**
     * 执行 {@code requireValidFile} 对应的业务步骤。
     * <p>
     * 实现要点：先校验输入、状态或业务边界。
     *
     * @param file 用户上传的文件
     * @return 方法执行结果，具体结构由返回类型 {@code MultipartFile} 表示
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    private MultipartFile requireValidFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传文件不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException("上传文件超过大小限制");
        }
        return file;
    }

    /**
     * 执行 {@code extractFileName} 对应的业务步骤。
     * <p>
     * 实现要点：先校验输入、状态或业务边界。
     *
     * @param file 用户上传的文件
     * @return 处理后得到的字符串结果
     */
    private String extractFileName(MultipartFile file) {
        String originalFileName = file.getOriginalFilename();
        return normalizeFileName(originalFileName);
    }

    /**
     * 执行 {@code extractFileExt} 对应的业务步骤。
     *
     * @param fileName 原始文件名
     * @return 处理后得到的字符串结果
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    private String extractFileExt(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex <= 0 || dotIndex == fileName.length() - 1) {
            throw new BusinessException("文件扩展名非法");
        }
        String fileExt = fileName.substring(dotIndex + 1).toLowerCase();
        if (fileExt.length() > MAX_FILE_EXT_LENGTH || !SUPPORTED_EXTENSIONS.contains(fileExt)) {
            throw new BusinessException("文件类型不支持");
        }
        return fileExt;
    }

    /**
     * 执行 {@code buildObjectKey} 对应的业务步骤。
     *
     * @param groupId 群组唯一标识
     * @param userId 用户唯一标识
     * @param fileExt 方法参数 {@code fileExt}
     * @return 处理后得到的字符串结果
     */
    private String buildObjectKey(Long groupId, Long userId, String fileExt) {
        String fileId = UUID.randomUUID().toString().replace("-", "");
        return "groups/%d/users/%d/%s.%s".formatted(groupId, userId, fileId, fileExt);
    }

    /**
     * 执行 {@code uploadFile} 对应的业务步骤。
     * <p>
     * 实现要点：读写 MinIO 对象存储中的原始文件；先校验输入、状态或业务边界；捕获依赖异常并转换、记录或执行降级策略。
     *
     * @param bucket 对象存储桶名称
     * @param objectKey 对象存储中的对象键
     * @param file 用户上传的文件
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    private void uploadFile(String bucket, String objectKey, MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            objectStorageService.putObject(
                    bucket,
                    objectKey,
                    inputStream,
                    file.getSize(),
                    normalizeContentType(file.getContentType())
            );
        } catch (IOException exception) {
            throw new BusinessException("读取上传文件失败");
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new BusinessException("文档上传失败");
        }
    }

    /**
     * 执行 {@code compensateUploadedObject} 对应的业务步骤。
     * <p>
     * 实现要点：读写 MinIO 对象存储中的原始文件；捕获依赖异常并转换、记录或执行降级策略。
     *
     * @param bucket 对象存储桶名称
     * @param objectKey 对象存储中的对象键
     * @param originalException 方法参数 {@code originalException}
     */
    private void compensateUploadedObject(String bucket, String objectKey, RuntimeException originalException) {
        try {
            objectStorageService.deleteObject(bucket, objectKey);
        } catch (RuntimeException compensationException) {
            originalException.addSuppressed(compensationException);
            log.warn(
                    "Failed to compensate uploaded object after metadata persistence failure, bucket={}, objectKey={}, reason={}",
                    bucket,
                    objectKey,
                    compensationException.getMessage()
            );
        }
    }

    /**
     * 执行 {@code compensateExternalIndexes} 对应的业务步骤。
     * <p>
     * 实现要点：写入或查询 pgvector 向量索引；捕获依赖异常并转换、记录或执行降级策略；维护或查询 Elasticsearch 关键词索引。
     *
     * @param document 当前处理的文档实体
     */
    private void compensateExternalIndexes(DocumentEntity document) {
        if (document == null || document.getId() == null) {
            return;
        }
        try {
            vectorIngestionService.deleteDocumentVectors(document.getId());
        } catch (RuntimeException exception) {
            log.warn("文档失败补偿时删除向量失败: documentId={}, reason={}", document.getId(), exception.getMessage());
        }
        try {
            elasticsearchChunkIndexService.deleteDocumentChunks(document.getId());
        } catch (RuntimeException exception) {
            log.warn("文档失败补偿时删除 ES 索引失败: documentId={}, reason={}", document.getId(), exception.getMessage());
        }
    }

    /**
     * 执行 {@code persistAndFinalizeUploadedDocument} 对应的业务步骤。
     * <p>
     * 实现要点：持久化数据库状态变更。
     *
     * @param command 方法参数 {@code command}
     * @return 方法执行结果，具体结构由返回类型 {@code DocumentEntity} 表示
     */
    private DocumentEntity persistAndFinalizeUploadedDocument(FinalizedUploadCommand command) {
        DocumentEntity document = buildDocument(command);
        documentMapper.insert(document);
        log.info("文档元数据入库完成: documentId={}, groupId={}, status={}",
                document.getId(), command.groupId(), document.getStatus());
        publishIngestionRequestedEvent(document.getId(), command.groupId());
        log.info("已发布文档异步ETL事件: documentId={}, groupId={}", document.getId(), command.groupId());
        return document;
    }

    /**
     * 执行 {@code publishIngestionRequestedEvent} 对应的业务步骤。
     * <p>
     * 实现要点：发布事件触发后续异步处理。
     *
     * @param documentId 文档唯一标识
     * @param groupId 群组唯一标识
     */
    private void publishIngestionRequestedEvent(Long documentId, Long groupId) {
        applicationEventPublisher.publishEvent(new DocumentIngestionRequestedEvent(documentId, groupId));
    }

    /**
     * 执行 {@code normalizeContentType} 对应的业务步骤。
     * <p>
     * 实现要点：先校验输入、状态或业务边界。
     *
     * @param contentType 文件的 MIME 类型
     * @return 处理后得到的字符串结果
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    private String normalizeContentType(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return "application/octet-stream";
        }
        if (contentType.length() > MAX_CONTENT_TYPE_LENGTH) {
            throw new BusinessException("文件类型描述过长");
        }
        return contentType;
    }

    /**
     * 执行 {@code normalizeQuery} 对应的业务步骤。
     * <p>
     * 实现要点：先校验输入、状态或业务边界；解析并确认当前登录用户；校验群组成员关系和角色权限。
     *
     * @param request 已经通过控制器基础校验的请求对象
     * @param query 用于检索或筛选的查询条件
     * @return 方法执行结果，具体结构由返回类型 {@code DocumentQuery} 表示
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    private DocumentQuery normalizeQuery(HttpServletRequest request, DocumentQuery query) {
        DocumentQuery safeQuery = query == null ? new DocumentQuery() : query;
        CurrentUserService.CurrentUser currentUser = currentUserService.requireBusinessUser(request);
        safeQuery.setCurrentUserId(currentUser.userId());
        if (safeQuery.getGroupId() != null) {
            groupMembershipService.requireGroupReadable(request, requireGroupId(safeQuery.getGroupId()));
        }
        if (safeQuery.getUploaderUserId() != null && safeQuery.getUploaderUserId() <= 0) {
            throw new BusinessException("uploaderUserId 非法");
        }
        if (safeQuery.getUploadedFrom() != null
                && safeQuery.getUploadedTo() != null
                && safeQuery.getUploadedFrom().isAfter(safeQuery.getUploadedTo())) {
            throw new BusinessException("uploadedFrom 不能晚于 uploadedTo");
        }
        if (StringUtils.hasText(safeQuery.getGroupRelation())) {
            safeQuery.setGroupRelation(normalizeGroupRelation(safeQuery.getGroupRelation()));
        }
        if (StringUtils.hasText(safeQuery.getStatus())) {
            safeQuery.setStatus(normalizeStatus(safeQuery.getStatus()));
        }
        if (StringUtils.hasText(safeQuery.getFileName())) {
            safeQuery.setFileName(safeQuery.getFileName().trim());
        }
        return safeQuery;
    }

    /**
     * 执行 {@code normalizeGroupRelation} 对应的业务步骤。
     * <p>
     * 实现要点：先校验输入、状态或业务边界。
     *
     * @param groupRelation 方法参数 {@code groupRelation}
     * @return 处理后得到的字符串结果
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    private String normalizeGroupRelation(String groupRelation) {
        String normalized = groupRelation.trim().toUpperCase();
        return switch (normalized) {
            case "OWNER", "OWNED" -> "OWNED";
            case "MEMBER", "JOINED" -> "JOINED";
            default -> throw new BusinessException("groupRelation 非法");
        };
    }

    /**
     * 执行 {@code normalizeStatus} 对应的业务步骤。
     * <p>
     * 实现要点：先校验输入、状态或业务边界；捕获依赖异常并转换、记录或执行降级策略。
     *
     * @param status 目标业务状态
     * @return 处理后得到的字符串结果
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    private String normalizeStatus(String status) {
        try {
            return DocumentStatus.valueOf(status.trim().toUpperCase()).name();
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("status 非法");
        }
    }

    /**
     * 执行 {@code buildDocument} 对应的业务步骤。
     *
     * @param command 方法参数 {@code command}
     * @return 方法执行结果，具体结构由返回类型 {@code DocumentEntity} 表示
     */
    private DocumentEntity buildDocument(FinalizedUploadCommand command) {
        LocalDateTime now = LocalDateTime.now();
        DocumentEntity document = new DocumentEntity();
        document.setGroupId(command.groupId());
        document.setUploaderUserId(command.userId());
        document.setFileName(command.fileName());
        document.setFileExt(command.fileExt());
        document.setContentType(command.contentType());
        document.setFileSize(command.fileSize());
        document.setFileHash(command.fileHash());
        document.setStorageBucket(command.bucket());
        document.setStorageObjectKey(command.objectKey());
        document.setStatus(DocumentStatus.PROCESSING.name());
        document.setDeleted(false);
        document.setUploadedAt(now);
        document.setCreatedAt(now);
        document.setUpdatedAt(now);
        return document;
    }

    /**
     * 执行 {@code requirePositiveUserId} 对应的业务步骤。
     * <p>
     * 实现要点：先校验输入、状态或业务边界。
     *
     * @param userId 用户唯一标识
     * @return 计算或处理得到的数值结果
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    private Long requirePositiveUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BusinessException("userId 非法");
        }
        return userId;
    }

    /**
     * 执行 {@code requirePositiveFileSize} 对应的业务步骤。
     * <p>
     * 实现要点：先校验输入、状态或业务边界。
     *
     * @param fileSize 方法参数 {@code fileSize}
     * @return 计算或处理得到的数值结果
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    private long requirePositiveFileSize(Long fileSize) {
        if (fileSize == null || fileSize <= 0) {
            throw new BusinessException("fileSize 非法");
        }
        return fileSize;
    }

    /**
     * 执行 {@code trimPreviewText} 对应的业务步骤。
     *
     * @param previewText 方法参数 {@code previewText}
     * @return 处理后得到的字符串结果
     */
    private String trimPreviewText(String previewText) {
        if (!StringUtils.hasText(previewText) || previewText.length() <= PREVIEW_MAX_LENGTH) {
            return previewText;
        }
        return previewText.substring(0, PREVIEW_MAX_LENGTH);
    }

    /**
     * 执行 {@code validateReusableFileName} 对应的业务步骤。
     * <p>
     * 实现要点：先校验输入、状态或业务边界。
     *
     * @param fileName 原始文件名
     * @return 处理后得到的字符串结果
     */
    private String validateReusableFileName(String fileName) {
        return normalizeFileName(fileName);
    }

    /**
     * 执行 {@code requireText} 对应的业务步骤。
     * <p>
     * 实现要点：先校验输入、状态或业务边界。
     *
     * @param value 方法参数 {@code value}
     * @param message 方法参数 {@code message}
     * @return 处理后得到的字符串结果
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(message);
        }
        return value.trim();
    }

    /**
     * 执行 {@code normalizeFileName} 对应的业务步骤。
     * <p>
     * 实现要点：先校验输入、状态或业务边界。
     *
     * @param rawFileName 方法参数 {@code rawFileName}
     * @return 处理后得到的字符串结果
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    private String normalizeFileName(String rawFileName) {
        if (!StringUtils.hasText(rawFileName)) {
            throw new BusinessException("文件名非法");
        }
        String normalizedFileName = StringUtils.cleanPath(rawFileName.trim());
        String fileName = normalizedFileName.substring(normalizedFileName.lastIndexOf('/') + 1);
        if (!StringUtils.hasText(fileName) || fileName.length() > MAX_FILE_NAME_LENGTH) {
            throw new BusinessException("文件名非法");
        }
        return fileName;
    }

    /**
     * 封装上传完成后创建文档记录并触发入库所需的规范化参数。
     *
     * <p>仅在 {@code DocumentService} 的实现过程中使用，用不可变数据结构收拢中间结果，避免参数和值的含义混淆。</p>
     */
    record FinalizedUploadCommand(
            Long groupId,
            Long userId,
            String fileName,
            String fileExt,
            String contentType,
            Long fileSize,
            String fileHash,
            String bucket,
            String objectKey
    ) {
    }
}
