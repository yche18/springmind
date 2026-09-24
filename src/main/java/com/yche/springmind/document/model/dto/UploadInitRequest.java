package com.yche.springmind.document.model.dto;

/**
 * 承载初始化分片上传所需的文件元数据和归属信息。
 *
 * <p>这是接口输入契约，只承载并校验调用方提交的数据，不在对象内部实现业务流程。</p>
 */
public record UploadInitRequest(
        Long groupId,
        String fileName,
        Long fileSize,
        String contentType,
        String fileHash,
        Long chunkSize,
        Integer chunkCount
) {
}
