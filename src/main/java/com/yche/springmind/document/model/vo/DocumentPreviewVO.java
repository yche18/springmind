package com.yche.springmind.document.model.vo;

/**
 * 表示文档预览结果及其可展示的文本内容和元数据。
 *
 * <p>这是接口输出视图，用于向前端稳定地暴露所需字段，避免直接返回数据库实体。</p>
 */
public class DocumentPreviewVO {

    private Long documentId;
    private String fileName;
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
