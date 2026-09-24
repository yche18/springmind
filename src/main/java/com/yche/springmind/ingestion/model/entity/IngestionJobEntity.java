package com.yche.springmind.ingestion.model.entity;

import java.time.LocalDateTime;

/**
 * 表示一次文档入库处理任务及其执行状态、错误和重试信息。
 *
 * <p>这是数据库持久化模型，字段与表结构相对应；业务层通过 Mapper 装载或保存其实例。</p>
 */
public class IngestionJobEntity {

    private Long id;
    private Long documentId;
    private Long groupId;
    private String jobType;
    private String status;
    private Integer retryCount;
    private Integer maxRetries;
    private String workerId;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime nextRetryAt;
    private String lastError;
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
     * 返回 {@code documentId} 对应的配置或状态值。
     *
     * @return 计算或处理得到的数值结果
     */
    public Long getDocumentId() {
        return documentId;
    }

    /**
     * 更新 {@code documentId} 对应的配置或状态值。
     *
     * @param documentId 文档唯一标识
     */
    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
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
     * 返回 {@code jobType} 对应的配置或状态值。
     *
     * @return 处理后得到的字符串结果
     */
    public String getJobType() {
        return jobType;
    }

    /**
     * 更新 {@code jobType} 对应的配置或状态值。
     *
     * @param jobType 方法参数 {@code jobType}
     */
    public void setJobType(String jobType) {
        this.jobType = jobType;
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
     * 返回 {@code retryCount} 对应的配置或状态值。
     *
     * @return 计算或处理得到的数值结果
     */
    public Integer getRetryCount() {
        return retryCount;
    }

    /**
     * 更新 {@code retryCount} 对应的配置或状态值。
     *
     * @param retryCount 方法参数 {@code retryCount}
     */
    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    /**
     * 返回 {@code maxRetries} 对应的配置或状态值。
     *
     * @return 计算或处理得到的数值结果
     */
    public Integer getMaxRetries() {
        return maxRetries;
    }

    /**
     * 更新 {@code maxRetries} 对应的配置或状态值。
     *
     * @param maxRetries 方法参数 {@code maxRetries}
     */
    public void setMaxRetries(Integer maxRetries) {
        this.maxRetries = maxRetries;
    }

    /**
     * 返回 {@code workerId} 对应的配置或状态值。
     *
     * @return 处理后得到的字符串结果
     */
    public String getWorkerId() {
        return workerId;
    }

    /**
     * 更新 {@code workerId} 对应的配置或状态值。
     *
     * @param workerId worker唯一标识
     */
    public void setWorkerId(String workerId) {
        this.workerId = workerId;
    }

    /**
     * 返回 {@code startedAt} 对应的配置或状态值。
     *
     * @return 查询得到的StartedAt结果
     */
    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    /**
     * 更新 {@code startedAt} 对应的配置或状态值。
     *
     * @param startedAt 方法参数 {@code startedAt}
     */
    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    /**
     * 返回 {@code finishedAt} 对应的配置或状态值。
     *
     * @return 查询得到的FinishedAt结果
     */
    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    /**
     * 更新 {@code finishedAt} 对应的配置或状态值。
     *
     * @param finishedAt 方法参数 {@code finishedAt}
     */
    public void setFinishedAt(LocalDateTime finishedAt) {
        this.finishedAt = finishedAt;
    }

    /**
     * 返回 {@code nextRetryAt} 对应的配置或状态值。
     *
     * @return 查询得到的Next重试At结果
     */
    public LocalDateTime getNextRetryAt() {
        return nextRetryAt;
    }

    /**
     * 更新 {@code nextRetryAt} 对应的配置或状态值。
     *
     * @param nextRetryAt 方法参数 {@code nextRetryAt}
     */
    public void setNextRetryAt(LocalDateTime nextRetryAt) {
        this.nextRetryAt = nextRetryAt;
    }

    /**
     * 返回 {@code lastError} 对应的配置或状态值。
     *
     * @return 处理后得到的字符串结果
     */
    public String getLastError() {
        return lastError;
    }

    /**
     * 更新 {@code lastError} 对应的配置或状态值。
     *
     * @param lastError 方法参数 {@code lastError}
     */
    public void setLastError(String lastError) {
        this.lastError = lastError;
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
