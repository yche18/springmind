package com.yche.springmind.document.model.dto;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * 承载文档列表的知识组、状态、关键词和分页查询条件。
 *
 * <p>这是接口输入契约，只承载并校验调用方提交的数据，不在对象内部实现业务流程。</p>
 */
public class DocumentQuery {

    private Long currentUserId;
    private Long groupId;
    private String groupRelation;
    private String fileName;
    private Long uploaderUserId;
    private String status;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime uploadedFrom;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime uploadedTo;

    /**
     * 返回 {@code currentUserId} 对应的配置或状态值。
     *
     * @return 计算或处理得到的数值结果
     */
    public Long getCurrentUserId() {
        return currentUserId;
    }

    /**
     * 更新 {@code currentUserId} 对应的配置或状态值。
     *
     * @param currentUserId 当前用户唯一标识
     */
    public void setCurrentUserId(Long currentUserId) {
        this.currentUserId = currentUserId;
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
     * 返回 {@code groupRelation} 对应的配置或状态值。
     *
     * @return 处理后得到的字符串结果
     */
    public String getGroupRelation() {
        return groupRelation;
    }

    /**
     * 更新 {@code groupRelation} 对应的配置或状态值。
     *
     * @param groupRelation 方法参数 {@code groupRelation}
     */
    public void setGroupRelation(String groupRelation) {
        this.groupRelation = groupRelation;
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
     * 返回 {@code uploadedFrom} 对应的配置或状态值。
     *
     * @return 查询得到的Uploaded来自结果
     */
    public LocalDateTime getUploadedFrom() {
        return uploadedFrom;
    }

    /**
     * 更新 {@code uploadedFrom} 对应的配置或状态值。
     *
     * @param uploadedFrom 方法参数 {@code uploadedFrom}
     */
    public void setUploadedFrom(LocalDateTime uploadedFrom) {
        this.uploadedFrom = uploadedFrom;
    }

    /**
     * 返回 {@code uploadedTo} 对应的配置或状态值。
     *
     * @return 查询得到的Uploaded转为结果
     */
    public LocalDateTime getUploadedTo() {
        return uploadedTo;
    }

    /**
     * 更新 {@code uploadedTo} 对应的配置或状态值。
     *
     * @param uploadedTo 方法参数 {@code uploadedTo}
     */
    public void setUploadedTo(LocalDateTime uploadedTo) {
        this.uploadedTo = uploadedTo;
    }
}
