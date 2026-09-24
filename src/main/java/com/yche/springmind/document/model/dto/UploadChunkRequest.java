package com.yche.springmind.document.model.dto;

import org.springframework.web.multipart.MultipartFile;

/**
 * 承载单个文件分片及其序号、校验信息和上传会话标识。
 *
 * <p>这是接口输入契约，只承载并校验调用方提交的数据，不在对象内部实现业务流程。</p>
 */
public record UploadChunkRequest(
        String uploadId,
        Integer chunkIndex,
        String chunkHash,
        MultipartFile chunk
) {
}
