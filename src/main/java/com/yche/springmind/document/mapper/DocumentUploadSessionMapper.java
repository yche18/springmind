package com.yche.springmind.document.mapper;

import com.yche.springmind.document.model.entity.DocumentUploadSessionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 定义大文件上传会话及其状态的持久化操作。
 *
 * <p>位于数据访问层：由 MyBatis 映射数据库操作，上层服务通过该接口读写持久化数据。</p>
 */
@Mapper
public interface DocumentUploadSessionMapper {

    /**
     * 访问数据库，完成 {@code insert} 对应的持久化操作。
     *
     * @param session 方法参数 {@code session}
     * @return 计算或处理得到的数值结果
     */
    int insert(DocumentUploadSessionEntity session);

    /**
     * 访问数据库，完成 {@code selectByUploadId} 对应的持久化操作。
     *
     * @param uploadId 分片上传任务唯一标识
     * @return 方法执行结果，具体结构由返回类型 {@code DocumentUploadSessionEntity} 表示
     */
    DocumentUploadSessionEntity selectByUploadId(@Param("uploadId") String uploadId);

    /**
     * 访问数据库，完成 {@code selectLatestReusableSession} 对应的持久化操作。
     *
     * @param groupId 群组唯一标识
     * @param uploaderUserId uploader用户唯一标识
     * @param fileHash 方法参数 {@code fileHash}
     * @return 方法执行结果，具体结构由返回类型 {@code DocumentUploadSessionEntity} 表示
     */
    DocumentUploadSessionEntity selectLatestReusableSession(
            @Param("groupId") Long groupId,
            @Param("uploaderUserId") Long uploaderUserId,
            @Param("fileHash") String fileHash
    );

    /**
     * 访问数据库，完成 {@code updateStatusAndMergedObjectKey} 对应的持久化操作。
     *
     * @param uploadId 分片上传任务唯一标识
     * @param status 目标业务状态
     * @param mergedObjectKey 方法参数 {@code mergedObjectKey}
     * @param updatedAt 方法参数 {@code updatedAt}
     * @return 计算或处理得到的数值结果
     */
    int updateStatusAndMergedObjectKey(
            @Param("uploadId") String uploadId,
            @Param("status") String status,
            @Param("mergedObjectKey") String mergedObjectKey,
            @Param("updatedAt") java.time.LocalDateTime updatedAt
    );
}
