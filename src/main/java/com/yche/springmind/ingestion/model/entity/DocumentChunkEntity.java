package com.yche.springmind.ingestion.model.entity;

import java.time.LocalDateTime;

/**
 * 表示一个可检索的文档切片及其位置、结构路径和向量关联信息。
 *
 * <p>这是数据库持久化模型，字段与表结构相对应；业务层通过 Mapper 装载或保存其实例。</p>
 */
public class DocumentChunkEntity {

    private Long id;
    private Long documentId;
    private Long groupId;
    private Integer chunkIndex;
    private String chunkText;
    private String chunkSummary;
    private Integer charStart;
    private Integer charEnd;
    private String metadataJson;
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
     * 返回 {@code chunkText} 对应的配置或状态值。
     *
     * @return 处理后得到的字符串结果
     */
    public String getChunkText() {
        return chunkText;
    }

    /**
     * 更新 {@code chunkText} 对应的配置或状态值。
     *
     * @param chunkText 方法参数 {@code chunkText}
     */
    public void setChunkText(String chunkText) {
        this.chunkText = chunkText;
    }

    /**
     * 返回 {@code chunkSummary} 对应的配置或状态值。
     *
     * @return 处理后得到的字符串结果
     */
    public String getChunkSummary() {
        return chunkSummary;
    }

    /**
     * 更新 {@code chunkSummary} 对应的配置或状态值。
     *
     * @param chunkSummary 方法参数 {@code chunkSummary}
     */
    public void setChunkSummary(String chunkSummary) {
        this.chunkSummary = chunkSummary;
    }

    /**
     * 返回 {@code charStart} 对应的配置或状态值。
     *
     * @return 计算或处理得到的数值结果
     */
    public Integer getCharStart() {
        return charStart;
    }

    /**
     * 更新 {@code charStart} 对应的配置或状态值。
     *
     * @param charStart 方法参数 {@code charStart}
     */
    public void setCharStart(Integer charStart) {
        this.charStart = charStart;
    }

    /**
     * 返回 {@code charEnd} 对应的配置或状态值。
     *
     * @return 计算或处理得到的数值结果
     */
    public Integer getCharEnd() {
        return charEnd;
    }

    /**
     * 更新 {@code charEnd} 对应的配置或状态值。
     *
     * @param charEnd 方法参数 {@code charEnd}
     */
    public void setCharEnd(Integer charEnd) {
        this.charEnd = charEnd;
    }

    /**
     * 返回 {@code metadataJson} 对应的配置或状态值。
     *
     * @return 处理后得到的字符串结果
     */
    public String getMetadataJson() {
        return metadataJson;
    }

    /**
     * 更新 {@code metadataJson} 对应的配置或状态值。
     *
     * @param metadataJson 方法参数 {@code metadataJson}
     */
    public void setMetadataJson(String metadataJson) {
        this.metadataJson = metadataJson;
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
