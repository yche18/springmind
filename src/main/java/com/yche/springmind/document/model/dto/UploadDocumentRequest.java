package com.yche.springmind.document.model.dto;

import org.springframework.web.multipart.MultipartFile;

/**
 * 承载普通文档上传时的知识组、文件和可选说明信息。
 *
 * <p>这是接口输入契约，只承载并校验调用方提交的数据，不在对象内部实现业务流程。</p>
 */
public class UploadDocumentRequest {

    private Long groupId;
    private MultipartFile file;

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
     * 返回 {@code file} 对应的配置或状态值。
     *
     * @return 查询得到的文件结果
     */
    public MultipartFile getFile() {
        return file;
    }

    /**
     * 更新 {@code file} 对应的配置或状态值。
     *
     * @param file 用户上传的文件
     */
    public void setFile(MultipartFile file) {
        this.file = file;
    }
}
