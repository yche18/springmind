package com.yche.springmind.document.model.vo;

import java.time.LocalDateTime;

/**
 * 表示文档列表中的摘要视图，包括处理状态和基础元数据。
 *
 * <p>这是接口输出视图，用于向前端稳定地暴露所需字段，避免直接返回数据库实体。</p>
 */
public class DocumentListItemVO {

    private Long documentId;
    private Long groupId;
    private String fileName;
    private String fileExt;
    private String contentType;
    private Long fileSize;
    private String status;
    private String failureReason;
    private LocalDateTime uploadedAt;
    private Long uploaderUserId;
    private String uploaderUserCode;
    private String uploaderDisplayName;
    private String previewText;

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
     * 返回 {@code uploaderUserCode} 对应的配置或状态值。
     *
     * @return 处理后得到的字符串结果
     */
    public String getUploaderUserCode() {
        return uploaderUserCode;
    }

    /**
     * 更新 {@code uploaderUserCode} 对应的配置或状态值。
     *
     * @param uploaderUserCode 方法参数 {@code uploaderUserCode}
     */
    public void setUploaderUserCode(String uploaderUserCode) {
        this.uploaderUserCode = uploaderUserCode;
    }

    /**
     * 返回 {@code uploaderDisplayName} 对应的配置或状态值。
     *
     * @return 处理后得到的字符串结果
     */
    public String getUploaderDisplayName() {
        return uploaderDisplayName;
    }

    /**
     * 更新 {@code uploaderDisplayName} 对应的配置或状态值。
     *
     * @param uploaderDisplayName 方法参数 {@code uploaderDisplayName}
     */
    public void setUploaderDisplayName(String uploaderDisplayName) {
        this.uploaderDisplayName = uploaderDisplayName;
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
}
