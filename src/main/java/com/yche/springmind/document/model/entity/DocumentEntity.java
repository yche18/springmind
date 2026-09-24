package com.yche.springmind.document.model.entity;

import java.time.LocalDateTime;

/**
 * 表示数据库中的文档主记录，保存归属、存储位置和处理状态等信息。
 *
 * <p>这是数据库持久化模型，字段与表结构相对应；业务层通过 Mapper 装载或保存其实例。</p>
 */
public class DocumentEntity {

    private Long id;
    private Long groupId;
    private Long uploaderUserId;
    private String fileName;
    private String fileExt;
    private String contentType;
    private Long fileSize;
    private String fileHash;
    private String storageBucket;
    private String storageObjectKey;
    private String status;
    private Boolean deleted;
    private String failureReason;
    private String previewText;
    private LocalDateTime uploadedAt;
    private LocalDateTime processedAt;
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
     * 返回 {@code storageObjectKey} 对应的配置或状态值。
     *
     * @return 处理后得到的字符串结果
     */
    public String getStorageObjectKey() {
        return storageObjectKey;
    }

    /**
     * 更新 {@code storageObjectKey} 对应的配置或状态值。
     *
     * @param storageObjectKey 方法参数 {@code storageObjectKey}
     */
    public void setStorageObjectKey(String storageObjectKey) {
        this.storageObjectKey = storageObjectKey;
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
     * 返回 {@code deleted} 对应的配置或状态值。
     *
     * @return 满足条件时返回 {@code true}，否则返回 {@code false}
     */
    public Boolean getDeleted() {
        return deleted;
    }

    /**
     * 更新 {@code deleted} 对应的配置或状态值。
     *
     * @param deleted 方法参数 {@code deleted}
     */
    public void setDeleted(Boolean deleted) {
        this.deleted = deleted;
    }

    /**
     * 返回 {@code failureReason} 对应的配置或状态值。
     *
     * @return 处理后得到的字符串结果
     */
    public String getFailureReason() {
        return failureReason;
    }

    /**
     * 更新 {@code failureReason} 对应的配置或状态值。
     *
     * @param failureReason 方法参数 {@code failureReason}
     */
    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    /**
     * 返回 {@code previewText} 对应的配置或状态值。
     *
     * @return 处理后得到的字符串结果
     */
    public String getPreviewText() {
        return previewText;
    }

    /**
     * 更新 {@code previewText} 对应的配置或状态值。
     *
     * @param previewText 方法参数 {@code previewText}
     */
    public void setPreviewText(String previewText) {
        this.previewText = previewText;
    }

    /**
     * 返回 {@code uploadedAt} 对应的配置或状态值。
     *
     * @return 查询得到的UploadedAt结果
     */
    public LocalDateTime getUploadedAt() {
        return uploadedAt;
    }

    /**
     * 更新 {@code uploadedAt} 对应的配置或状态值。
     *
     * @param uploadedAt 方法参数 {@code uploadedAt}
     */
    public void setUploadedAt(LocalDateTime uploadedAt) {
        this.uploadedAt = uploadedAt;
    }

    /**
     * 返回 {@code processedAt} 对应的配置或状态值。
     *
     * @return 查询得到的ProcessedAt结果
     */
    public LocalDateTime getProcessedAt() {
        return processedAt;
    }

    /**
     * 更新 {@code processedAt} 对应的配置或状态值。
     *
     * @param processedAt 方法参数 {@code processedAt}
     */
    public void setProcessedAt(LocalDateTime processedAt) {
        this.processedAt = processedAt;
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
