package com.yche.springmind.document.model.entity;

import java.time.LocalDateTime;

/**
 * 表示一次分片上传中某个已落盘分片的数据库记录。
 *
 * <p>这是数据库持久化模型，字段与表结构相对应；业务层通过 Mapper 装载或保存其实例。</p>
 */
public class DocumentUploadChunkEntity {

    private Long id;
    private String uploadId;
    private Integer chunkIndex;
    private Long chunkSize;
    private String chunkHash;
    private String storageBucket;
    private String storageObjectKey;
    private LocalDateTime uploadedAt;
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
     * 返回 {@code chunkIndex} 对应的配置或状态值。
     *
     * @return 计算或处理得到的数值结果
     */
    public Integer getChunkIndex() {
        return chunkIndex;
    }

    /**
     * 更新 {@code chunkIndex} 对应的配置或状态值。
     *
     * @param chunkIndex 方法参数 {@code chunkIndex}
     */
    public void setChunkIndex(Integer chunkIndex) {
        this.chunkIndex = chunkIndex;
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
     * 返回 {@code chunkHash} 对应的配置或状态值。
     *
     * @return 处理后得到的字符串结果
     */
    public String getChunkHash() {
        return chunkHash;
    }

    /**
     * 更新 {@code chunkHash} 对应的配置或状态值。
     *
     * @param chunkHash 方法参数 {@code chunkHash}
     */
    public void setChunkHash(String chunkHash) {
        this.chunkHash = chunkHash;
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
