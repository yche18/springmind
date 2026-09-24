package com.yche.springmind.document.model.entity;

import java.time.LocalDateTime;

/**
 * 表示大文件分片上传会话及其合并进度的数据库记录。
 *
 * <p>这是数据库持久化模型，字段与表结构相对应；业务层通过 Mapper 装载或保存其实例。</p>
 */
public class DocumentUploadSessionEntity {

    private Long id;
    private String uploadId;
    private Long groupId;
    private Long uploaderUserId;
    private String fileName;
    private String fileExt;
    private String contentType;
    private Long fileSize;
    private String fileHash;
    private Long chunkSize;
    private Integer chunkCount;
    private String status;
    private String storageBucket;
    private String mergedObjectKey;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 返回 {@code id} 对应的配置或状态值。
     *
     * @return 计算或处理得到的数值结果
     */
    public Long getId() {
        return id;
    }

    /**
     * 更新 {@code id} 对应的配置或状态值。
     *
     * @param id 方法参数 {@code id}
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * 返回 {@code uploadId} 对应的配置或状态值。
     *
     * @return 处理后得到的字符串结果
     */
    public String getUploadId() {
        return uploadId;
    }

    /**
     * 更新 {@code uploadId} 对应的配置或状态值。
     *
     * @param uploadId 分片上传任务唯一标识
     */
    public void setUploadId(String uploadId) {
        this.uploadId = uploadId;
    }

    /**
     * 返回 {@code groupId} 对应的配置或状态值。
     *
     * @return 计算或处理得到的数值结果
     */
    public Long getGroupId() {
        return groupId;
    }

    /**
     * 更新 {@code groupId} 对应的配置或状态值。
     *
     * @param groupId 群组唯一标识
     */
    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }

    /**
     * 返回 {@code uploaderUserId} 对应的配置或状态值。
     *
     * @return 计算或处理得到的数值结果
     */
    public Long getUploaderUserId() {
        return uploaderUserId;
    }

    /**
     * 更新 {@code uploaderUserId} 对应的配置或状态值。
     *
     * @param uploaderUserId uploader用户唯一标识
     */
    public void setUploaderUserId(Long uploaderUserId) {
        this.uploaderUserId = uploaderUserId;
    }

    /**
     * 返回 {@code fileName} 对应的配置或状态值。
     *
     * @return 处理后得到的字符串结果
     */
    public String getFileName() {
        return fileName;
    }

    /**
     * 更新 {@code fileName} 对应的配置或状态值。
     *
     * @param fileName 原始文件名
     */
    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    /**
     * 返回 {@code fileExt} 对应的配置或状态值。
     *
     * @return 处理后得到的字符串结果
     */
    public String getFileExt() {
        return fileExt;
    }

    /**
     * 更新 {@code fileExt} 对应的配置或状态值。
     *
     * @param fileExt 方法参数 {@code fileExt}
     */
    public void setFileExt(String fileExt) {
        this.fileExt = fileExt;
    }

    /**
     * 返回 {@code contentType} 对应的配置或状态值。
     *
     * @return 处理后得到的字符串结果
     */
    public String getContentType() {
        return contentType;
    }

    /**
     * 更新 {@code contentType} 对应的配置或状态值。
     *
     * @param contentType 文件的 MIME 类型
     */
    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    /**
     * 返回 {@code fileSize} 对应的配置或状态值。
     *
     * @return 计算或处理得到的数值结果
     */
    public Long getFileSize() {
        return fileSize;
    }

    /**
     * 更新 {@code fileSize} 对应的配置或状态值。
     *
     * @param fileSize 方法参数 {@code fileSize}
     */
    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    /**
     * 返回 {@code fileHash} 对应的配置或状态值。
     *
     * @return 处理后得到的字符串结果
     */
    public String getFileHash() {
        return fileHash;
    }

    /**
     * 更新 {@code fileHash} 对应的配置或状态值。
     *
     * @param fileHash 方法参数 {@code fileHash}
     */
    public void setFileHash(String fileHash) {
        this.fileHash = fileHash;
    }

    /**
     * 返回 {@code chunkSize} 对应的配置或状态值。
     *
     * @return 计算或处理得到的数值结果
     */
    public Long getChunkSize() {
        return chunkSize;
    }

    /**
     * 更新 {@code chunkSize} 对应的配置或状态值。
     *
     * @param chunkSize 方法参数 {@code chunkSize}
     */
    public void setChunkSize(Long chunkSize) {
        this.chunkSize = chunkSize;
    }

    /**
     * 返回 {@code chunkCount} 对应的配置或状态值。
     *
     * @return 计算或处理得到的数值结果
     */
    public Integer getChunkCount() {
        return chunkCount;
    }

    /**
     * 更新 {@code chunkCount} 对应的配置或状态值。
     *
     * @param chunkCount 方法参数 {@code chunkCount}
     */
    public void setChunkCount(Integer chunkCount) {
        this.chunkCount = chunkCount;
    }

    /**
     * 返回 {@code status} 对应的配置或状态值。
     *
     * @return 处理后得到的字符串结果
     */
    public String getStatus() {
        return status;
    }

    /**
     * 更新 {@code status} 对应的配置或状态值。
     *
     * @param status 目标业务状态
     */
    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * 返回 {@code storageBucket} 对应的配置或状态值。
     *
     * @return 处理后得到的字符串结果
     */
    public String getStorageBucket() {
        return storageBucket;
    }

    /**
     * 更新 {@code storageBucket} 对应的配置或状态值。
     *
     * @param storageBucket 方法参数 {@code storageBucket}
     */
    public void setStorageBucket(String storageBucket) {
        this.storageBucket = storageBucket;
    }

    /**
     * 返回 {@code mergedObjectKey} 对应的配置或状态值。
     *
     * @return 处理后得到的字符串结果
     */
    public String getMergedObjectKey() {
        return mergedObjectKey;
    }

    /**
     * 更新 {@code mergedObjectKey} 对应的配置或状态值。
     *
     * @param mergedObjectKey 方法参数 {@code mergedObjectKey}
     */
    public void setMergedObjectKey(String mergedObjectKey) {
        this.mergedObjectKey = mergedObjectKey;
    }

    /**
     * 返回 {@code expiresAt} 对应的配置或状态值。
     *
     * @return 查询得到的ExpiresAt结果
     */
    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    /**
     * 更新 {@code expiresAt} 对应的配置或状态值。
     *
     * @param expiresAt 方法参数 {@code expiresAt}
     */
    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    /**
     * 返回 {@code createdAt} 对应的配置或状态值。
     *
     * @return 查询得到的CreatedAt结果
     */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * 更新 {@code createdAt} 对应的配置或状态值。
     *
     * @param createdAt 方法参数 {@code createdAt}
     */
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * 返回 {@code updatedAt} 对应的配置或状态值。
     *
     * @return 查询得到的UpdatedAt结果
     */
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    /**
     * 更新 {@code updatedAt} 对应的配置或状态值。
     *
     * @param updatedAt 方法参数 {@code updatedAt}
     */
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
