package com.yche.springmind.document.service;

import com.yche.springmind.common.exception.BusinessException;
import com.yche.springmind.document.mapper.DocumentUploadChunkMapper;
import com.yche.springmind.document.mapper.DocumentMapper;
import com.yche.springmind.document.mapper.DocumentUploadSessionMapper;
import com.yche.springmind.document.model.dto.UploadInitRequest;
import com.yche.springmind.document.model.dto.UploadChunkRequest;
import com.yche.springmind.document.model.entity.DocumentUploadChunkEntity;
import com.yche.springmind.document.model.entity.DocumentEntity;
import com.yche.springmind.document.model.entity.DocumentUploadSessionEntity;
import com.yche.springmind.document.model.vo.UploadInitResponse;
import com.yche.springmind.document.model.vo.UploadStatusResponse;
import com.yche.springmind.groupmembership.service.GroupMembershipService;
import com.yche.springmind.identity.service.CurrentUserService;
import com.yche.springmind.storage.service.ObjectStorageService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 编排普通上传和大文件分片上传，负责会话、分片校验、合并及最终确认。
 *
 * <p>位于业务服务层：编排领域操作和基础设施调用，并集中维护事务、权限校验及失败处理边界。</p>
 */
@Service
public class DocumentUploadService {

    private static final int MAX_FILE_NAME_LENGTH = 255;
    private static final int MAX_CONTENT_TYPE_LENGTH = 128;
    private static final int MAX_FILE_HASH_LENGTH = 128;
    private static final int MAX_FILE_EXT_LENGTH = 16;
    private static final long MAX_FILE_SIZE = 256L * 1024 * 1024;
    private static final long MAX_CHUNK_SIZE = 10L * 1024 * 1024;
    private static final long SESSION_EXPIRE_HOURS = 24L;
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("txt", "md", "pdf", "docx");
    private static final String UPLOAD_STATUS_INIT = "INIT";
    private static final String UPLOAD_STATUS_UPLOADING = "UPLOADING";
    private static final String UPLOAD_STATUS_COMPLETING = "COMPLETING";
    private static final String UPLOAD_STATUS_COMPLETED = "COMPLETED";
    private static final String OCTET_STREAM = "application/octet-stream";

    private final DocumentMapper documentMapper;
    private final DocumentUploadSessionMapper documentUploadSessionMapper;
    private final DocumentUploadChunkMapper documentUploadChunkMapper;
    private final GroupMembershipService groupMembershipService;
    private final DocumentService documentService;
    private final ObjectStorageService objectStorageService;

    /**
     * 创建并初始化 {@link DocumentUploadService}，保存该组件运行所需的依赖与配置。
     * <p>
     * 实现要点：校验群组成员关系和角色权限；读写 MinIO 对象存储中的原始文件。
     *
     * @param documentMapper 方法参数 {@code documentMapper}
     * @param documentUploadSessionMapper 方法参数 {@code documentUploadSessionMapper}
     * @param documentUploadChunkMapper 方法参数 {@code documentUploadChunkMapper}
     * @param groupMembershipService 方法参数 {@code groupMembershipService}
     * @param documentService 方法参数 {@code documentService}
     * @param objectStorageService 方法参数 {@code objectStorageService}
     */
    public DocumentUploadService(
            DocumentMapper documentMapper,
            DocumentUploadSessionMapper documentUploadSessionMapper,
            DocumentUploadChunkMapper documentUploadChunkMapper,
            GroupMembershipService groupMembershipService,
            DocumentService documentService,
            ObjectStorageService objectStorageService
    ) {
        this.documentMapper = documentMapper;
        this.documentUploadSessionMapper = documentUploadSessionMapper;
        this.documentUploadChunkMapper = documentUploadChunkMapper;
        this.groupMembershipService = groupMembershipService;
        this.documentService = documentService;
        this.objectStorageService = objectStorageService;
    }

    /**
     * 初始化分片上传会话，计算分片数量并返回客户端续传所需的信息。
     * <p>
     * 实现要点：使用事务保证多次数据库操作的一致性；先校验输入、状态或业务边界；校验群组成员关系和角色权限；读取数据库中的当前状态；持久化数据库状态变更。
     *
     * @param request 已经通过控制器基础校验的请求对象
     * @param uploadRequest 上传请求参数
     * @return 方法执行结果，具体结构由返回类型 {@code UploadInitResponse} 表示
     */
    @Transactional
    public UploadInitResponse initUpload(HttpServletRequest request, UploadInitRequest uploadRequest) {
        NormalizedInitRequest normalizedRequest = validateInitRequest(uploadRequest);
        Long groupId = normalizedRequest.groupId();
        CurrentUserService.CurrentUser currentUser = groupMembershipService.requireGroupOwner(request, groupId);
        DocumentEntity existingDocument = documentMapper.selectByGroupIdAndFileHash(groupId, normalizedRequest.fileHash());
        if (existingDocument != null && "READY".equals(existingDocument.getStatus())) {
            Long documentId = documentService.createInstantUploadedDocument(
                    groupId,
                    currentUser.userId(),
                    existingDocument,
                    normalizedRequest.fileName()
            );
            return UploadInitResponse.instant(documentId);
        }
        DocumentUploadSessionEntity existingSession = documentUploadSessionMapper.selectLatestReusableSession(
                groupId,
                currentUser.userId(),
                normalizedRequest.fileHash()
        );
        if (existingSession != null) {
            List<Integer> uploadedChunks = documentUploadChunkMapper.selectByUploadId(existingSession.getUploadId()).stream()
                    .map(DocumentUploadChunkEntity::getChunkIndex)
                    .toList();
            return UploadInitResponse.uploadSession(
                    existingSession.getUploadId(),
                    uploadedChunks,
                    existingSession.getChunkSize(),
                    existingSession.getChunkCount()
            );
        }
        DocumentUploadSessionEntity session = buildUploadSession(groupId, currentUser.userId(), normalizedRequest);
        documentUploadSessionMapper.insert(session);
        return UploadInitResponse.uploadSession(session.getUploadId(), session.getChunkSize(), session.getChunkCount());
    }

    /**
     * 校验上传会话归属和分片范围后，将单个分片写入对象存储并记录状态。
     * <p>
     * 实现要点：使用事务保证多次数据库操作的一致性；先校验输入、状态或业务边界；读写 MinIO 对象存储中的原始文件；捕获依赖异常并转换、记录或执行降级策略；读取数据库中的当前状态。
     *
     * @param request 已经通过控制器基础校验的请求对象
     * @param uploadRequest 上传请求参数
     * @return 符合条件的结果集合；无结果时返回空集合
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    @Transactional
    public List<Integer> uploadChunk(HttpServletRequest request, UploadChunkRequest uploadRequest) {
        DocumentUploadSessionEntity session = requireOwnedActiveSession(request, uploadRequest.uploadId());
        MultipartFile chunk = requireChunk(uploadRequest, session);
        String chunkHash = normalizeFileHash(uploadRequest.chunkHash());
        String objectKey = buildChunkObjectKey(session.getGroupId(), session.getUploadId(), uploadRequest.chunkIndex());
        LocalDateTime now = LocalDateTime.now();
        try {
            objectStorageService.putObject(
                    session.getStorageBucket(),
                    objectKey,
                    chunk.getInputStream(),
                    chunk.getSize(),
                    OCTET_STREAM
            );
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException("分片上传失败");
        }

        DocumentUploadChunkEntity uploadChunk = new DocumentUploadChunkEntity();
        uploadChunk.setUploadId(session.getUploadId());
        uploadChunk.setChunkIndex(uploadRequest.chunkIndex());
        uploadChunk.setChunkSize(chunk.getSize());
        uploadChunk.setChunkHash(chunkHash);
        uploadChunk.setStorageBucket(session.getStorageBucket());
        uploadChunk.setStorageObjectKey(objectKey);
        uploadChunk.setUploadedAt(now);
        uploadChunk.setCreatedAt(now);
        uploadChunk.setUpdatedAt(now);
        documentUploadChunkMapper.upsert(uploadChunk);
        documentUploadSessionMapper.updateStatusAndMergedObjectKey(
                session.getUploadId(),
                UPLOAD_STATUS_UPLOADING,
                null,
                now
        );
        return documentUploadChunkMapper.selectByUploadId(session.getUploadId()).stream()
                .map(DocumentUploadChunkEntity::getChunkIndex)
                .toList();
    }

    /**
     * 校验所有分片齐备后合并对象，创建文档记录并触发异步入库。
     * <p>
     * 实现要点：使用事务保证多次数据库操作的一致性；先校验输入、状态或业务边界；读取数据库中的当前状态；读写 MinIO 对象存储中的原始文件；捕获依赖异常并转换、记录或执行降级策略。
     *
     * @param request 已经通过控制器基础校验的请求对象
     * @param uploadId 分片上传任务唯一标识
     * @return 计算或处理得到的数值结果
     */
    @Transactional
    public Long completeUpload(HttpServletRequest request, String uploadId) {
        DocumentUploadSessionEntity session = requireOwnedActiveSession(request, uploadId);
        List<DocumentUploadChunkEntity> chunks = documentUploadChunkMapper.selectByUploadId(uploadId).stream()
                .sorted(Comparator.comparing(DocumentUploadChunkEntity::getChunkIndex))
                .toList();
        ensureAllChunksPresent(session, chunks);
        String objectKey = buildFinalObjectKey(session);
        LocalDateTime now = LocalDateTime.now();
        documentUploadSessionMapper.updateStatusAndMergedObjectKey(
                uploadId,
                UPLOAD_STATUS_COMPLETING,
                null,
                now
        );
        try {
            objectStorageService.composeObject(
                    session.getStorageBucket(),
                    objectKey,
                    chunks.stream().map(DocumentUploadChunkEntity::getStorageObjectKey).toList(),
                    session.getContentType()
            );
            Long documentId = documentService.finalizeUploadedDocument(
                    session.getGroupId(),
                    session.getUploaderUserId(),
                    session.getFileName(),
                    session.getFileExt(),
                    session.getContentType(),
                    session.getFileSize(),
                    session.getFileHash(),
                    session.getStorageBucket(),
                    objectKey
            );
            documentUploadSessionMapper.updateStatusAndMergedObjectKey(
                    uploadId,
                    UPLOAD_STATUS_COMPLETED,
                    objectKey,
                    LocalDateTime.now()
            );
            return documentId;
        } catch (RuntimeException exception) {
            try {
                objectStorageService.deleteObject(session.getStorageBucket(), objectKey);
            } catch (RuntimeException ignored) {
            }
            throw exception;
        }
    }

    /**
     * 返回 {@code uploadStatus} 对应的配置或状态值。
     *
     * @param request 已经通过控制器基础校验的请求对象
     * @param uploadId 分片上传任务唯一标识
     * @return 查询得到的上传状态结果
     */
    public UploadStatusResponse getUploadStatus(HttpServletRequest request, String uploadId) {
        DocumentUploadSessionEntity session = requireOwnedActiveSession(request, uploadId);
        List<Integer> uploadedChunks = documentUploadChunkMapper.selectByUploadId(uploadId).stream()
                .map(DocumentUploadChunkEntity::getChunkIndex)
                .toList();
        return new UploadStatusResponse(
                session.getStatus(),
                uploadedChunks,
                uploadedChunks.size(),
                session.getChunkCount()
        );
    }

    /**
     * 执行 {@code validateInitRequest} 对应的业务步骤。
     * <p>
     * 实现要点：先校验输入、状态或业务边界。
     *
     * @param uploadRequest 上传请求参数
     * @return 方法执行结果，具体结构由返回类型 {@code NormalizedInitRequest} 表示
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    private NormalizedInitRequest validateInitRequest(UploadInitRequest uploadRequest) {
        if (uploadRequest == null) {
            throw new BusinessException("上传初始化请求不能为空");
        }
        Long groupId = requireGroupId(uploadRequest.groupId());
        String fileName = sanitizeFileName(uploadRequest.fileName());
        String fileExt = extractFileExt(fileName);
        long fileSize = requirePositive(uploadRequest.fileSize(), "fileSize 非法");
        if (fileSize > MAX_FILE_SIZE) {
            throw new BusinessException("上传文件超过大小限制");
        }
        String contentType = normalizeContentType(uploadRequest.contentType());
        String fileHash = normalizeFileHash(uploadRequest.fileHash());
        long chunkSize = requirePositive(uploadRequest.chunkSize(), "chunkSize 非法");
        if (chunkSize > MAX_CHUNK_SIZE) {
            throw new BusinessException("chunkSize 超过限制");
        }
        int chunkCount = requirePositive(uploadRequest.chunkCount(), "chunkCount 非法");
        long expectedChunkCount = (fileSize + chunkSize - 1) / chunkSize;
        if (chunkCount != expectedChunkCount) {
            throw new BusinessException("chunkCount 与文件大小不匹配");
        }
        return new NormalizedInitRequest(groupId, fileName, fileExt, fileSize, contentType, fileHash, chunkSize, chunkCount);
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
     * 执行 {@code sanitizeFileName} 对应的业务步骤。
     * <p>
     * 实现要点：先校验输入、状态或业务边界。
     *
     * @param fileName 原始文件名
     * @return 处理后得到的字符串结果
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    private String sanitizeFileName(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            throw new BusinessException("文件名非法");
        }
        String normalizedFileName = StringUtils.cleanPath(fileName.trim());
        String sanitizedFileName = normalizedFileName.substring(normalizedFileName.lastIndexOf('/') + 1);
        if (!StringUtils.hasText(sanitizedFileName) || sanitizedFileName.length() > MAX_FILE_NAME_LENGTH) {
            throw new BusinessException("文件名非法");
        }
        return sanitizedFileName;
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
        String normalizedContentType = contentType.trim();
        if (normalizedContentType.length() > MAX_CONTENT_TYPE_LENGTH) {
            throw new BusinessException("文件类型描述过长");
        }
        return normalizedContentType;
    }

    /**
     * 执行 {@code normalizeFileHash} 对应的业务步骤。
     * <p>
     * 实现要点：先校验输入、状态或业务边界。
     *
     * @param fileHash 方法参数 {@code fileHash}
     * @return 处理后得到的字符串结果
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    private String normalizeFileHash(String fileHash) {
        if (!StringUtils.hasText(fileHash)) {
            throw new BusinessException("fileHash 非法");
        }
        String normalizedFileHash = fileHash.trim();
        if (normalizedFileHash.length() > MAX_FILE_HASH_LENGTH) {
            throw new BusinessException("fileHash 非法");
        }
        return normalizedFileHash;
    }

    /**
     * 执行 {@code requirePositive} 对应的业务步骤。
     * <p>
     * 实现要点：先校验输入、状态或业务边界。
     *
     * @param value 方法参数 {@code value}
     * @param message 方法参数 {@code message}
     * @return 计算或处理得到的数值结果
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    private long requirePositive(Long value, String message) {
        if (value == null || value <= 0) {
            throw new BusinessException(message);
        }
        return value;
    }

    /**
     * 执行 {@code requirePositive} 对应的业务步骤。
     * <p>
     * 实现要点：先校验输入、状态或业务边界。
     *
     * @param value 方法参数 {@code value}
     * @param message 方法参数 {@code message}
     * @return 计算或处理得到的数值结果
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    private int requirePositive(Integer value, String message) {
        if (value == null || value <= 0) {
            throw new BusinessException(message);
        }
        return value;
    }

    /**
     * 执行 {@code requireOwnedActiveSession} 对应的业务步骤。
     * <p>
     * 实现要点：先校验输入、状态或业务边界；读取数据库中的当前状态；校验群组成员关系和角色权限。
     *
     * @param request 已经通过控制器基础校验的请求对象
     * @param uploadId 分片上传任务唯一标识
     * @return 方法执行结果，具体结构由返回类型 {@code DocumentUploadSessionEntity} 表示
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    private DocumentUploadSessionEntity requireOwnedActiveSession(HttpServletRequest request, String uploadId) {
        if (!StringUtils.hasText(uploadId)) {
            throw new BusinessException("uploadId 非法");
        }
        DocumentUploadSessionEntity session = documentUploadSessionMapper.selectByUploadId(uploadId.trim());
        if (session == null) {
            throw new BusinessException("上传会话不存在");
        }
        CurrentUserService.CurrentUser currentUser = groupMembershipService.requireGroupOwner(request, session.getGroupId());
        if (!currentUser.userId().equals(session.getUploaderUserId())) {
            throw new BusinessException("上传会话不属于当前用户");
        }
        if (session.getExpiresAt() != null && session.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException("上传会话已过期");
        }
        if (UPLOAD_STATUS_COMPLETED.equals(session.getStatus())) {
            throw new BusinessException("上传会话已完成");
        }
        return session;
    }

    /**
     * 执行 {@code requireChunk} 对应的业务步骤。
     * <p>
     * 实现要点：先校验输入、状态或业务边界。
     *
     * @param uploadRequest 上传请求参数
     * @param session 方法参数 {@code session}
     * @return 方法执行结果，具体结构由返回类型 {@code MultipartFile} 表示
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    private MultipartFile requireChunk(UploadChunkRequest uploadRequest, DocumentUploadSessionEntity session) {
        if (uploadRequest == null) {
            throw new BusinessException("分片上传请求不能为空");
        }
        if (uploadRequest.chunkIndex() == null
                || uploadRequest.chunkIndex() < 0
                || uploadRequest.chunkIndex() >= session.getChunkCount()) {
            throw new BusinessException("chunkIndex 非法");
        }
        MultipartFile chunk = uploadRequest.chunk();
        if (chunk == null || chunk.isEmpty()) {
            throw new BusinessException("上传分片不能为空");
        }
        if (chunk.getSize() > session.getChunkSize()) {
            throw new BusinessException("上传分片超过大小限制");
        }
        return chunk;
    }

    /**
     * 执行 {@code ensureAllChunksPresent} 对应的业务步骤。
     * <p>
     * 实现要点：先校验输入、状态或业务边界。
     *
     * @param session 方法参数 {@code session}
     * @param chunks 待处理的文档切片集合
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    private void ensureAllChunksPresent(DocumentUploadSessionEntity session, List<DocumentUploadChunkEntity> chunks) {
        if (chunks.size() != session.getChunkCount()) {
            throw new BusinessException("缺少分片，无法完成上传");
        }
        for (int index = 0; index < session.getChunkCount(); index++) {
            if (!Integer.valueOf(index).equals(chunks.get(index).getChunkIndex())) {
                throw new BusinessException("缺少分片，无法完成上传");
            }
        }
    }

    /**
     * 执行 {@code buildChunkObjectKey} 对应的业务步骤。
     *
     * @param groupId 群组唯一标识
     * @param uploadId 分片上传任务唯一标识
     * @param chunkIndex 方法参数 {@code chunkIndex}
     * @return 处理后得到的字符串结果
     */
    private String buildChunkObjectKey(Long groupId, String uploadId, Integer chunkIndex) {
        return "uploads/%d/%s/chunks/%d".formatted(groupId, uploadId, chunkIndex);
    }

    /**
     * 执行 {@code buildFinalObjectKey} 对应的业务步骤。
     *
     * @param session 方法参数 {@code session}
     * @return 处理后得到的字符串结果
     */
    private String buildFinalObjectKey(DocumentUploadSessionEntity session) {
        String fileId = UUID.randomUUID().toString().replace("-", "");
        return "groups/%d/users/%d/%s.%s".formatted(
                session.getGroupId(),
                session.getUploaderUserId(),
                fileId,
                session.getFileExt()
        );
    }

    /**
     * 执行 {@code buildUploadSession} 对应的业务步骤。
     * <p>
     * 实现要点：读写 MinIO 对象存储中的原始文件。
     *
     * @param groupId 群组唯一标识
     * @param userId 用户唯一标识
     * @param uploadRequest 上传请求参数
     * @return 方法执行结果，具体结构由返回类型 {@code DocumentUploadSessionEntity} 表示
     */
    private DocumentUploadSessionEntity buildUploadSession(Long groupId, Long userId, NormalizedInitRequest uploadRequest) {
        LocalDateTime now = LocalDateTime.now();
        DocumentUploadSessionEntity session = new DocumentUploadSessionEntity();
        session.setUploadId(UUID.randomUUID().toString().replace("-", ""));
        session.setGroupId(groupId);
        session.setUploaderUserId(userId);
        session.setFileName(uploadRequest.fileName());
        session.setFileExt(uploadRequest.fileExt());
        session.setContentType(uploadRequest.contentType());
        session.setFileSize(uploadRequest.fileSize());
        session.setFileHash(uploadRequest.fileHash());
        session.setChunkSize(uploadRequest.chunkSize());
        session.setChunkCount(uploadRequest.chunkCount());
        session.setStatus(UPLOAD_STATUS_INIT);
        session.setStorageBucket(objectStorageService.getDefaultBucket());
        session.setMergedObjectKey(null);
        session.setExpiresAt(now.plusHours(SESSION_EXPIRE_HOURS));
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        return session;
    }

    /**
     * 保存分片上传初始化请求经过规范化和校验后的数据。
     *
     * <p>仅在 {@code DocumentUploadService} 的实现过程中使用，用不可变数据结构收拢中间结果，避免参数和值的含义混淆。</p>
     */
    private record NormalizedInitRequest(
            Long groupId,
            String fileName,
            String fileExt,
            Long fileSize,
            String contentType,
            String fileHash,
            Long chunkSize,
            Integer chunkCount
    ) {
    }
}
