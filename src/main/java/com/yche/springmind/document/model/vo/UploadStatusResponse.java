package com.yche.springmind.document.model.vo;

import java.util.List;

/**
 * 返回上传会话当前进度、状态和最终生成的文档标识。
 *
 * <p>这是接口输出视图，用于向前端稳定地暴露所需字段，避免直接返回数据库实体。</p>
 */
public record UploadStatusResponse(
        String status,
        List<Integer> uploadedChunks,
        Integer uploadedChunkCount,
        Integer chunkCount
) {
}
