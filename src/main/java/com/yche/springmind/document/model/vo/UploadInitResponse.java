package com.yche.springmind.document.model.vo;

import java.util.List;

/**
 * 返回分片上传会话、分片规划以及可跳过的已上传分片。
 *
 * <p>这是接口输出视图，用于向前端稳定地暴露所需字段，避免直接返回数据库实体。</p>
 */
public record UploadInitResponse(
        boolean instantUpload,
        Long documentId,
        String uploadId,
        List<Integer> uploadedChunks,
        Long chunkSize,
        Integer chunkCount
) {

    /**
     * 完成 {@code instant} 对应的处理。
     *
     * @param documentId 文档唯一标识
     * @return 方法执行结果，具体结构由返回类型 {@code UploadInitResponse} 表示
     */
    public static UploadInitResponse instant(Long documentId) {
        return new UploadInitResponse(true, documentId, null, List.of(), null, null);
    }

    /**
     * 完成 {@code uploadSession} 对应的处理。
     *
     * @param uploadId 分片上传任务唯一标识
     * @param chunkSize 方法参数 {@code chunkSize}
     * @param chunkCount 方法参数 {@code chunkCount}
     * @return 方法执行结果，具体结构由返回类型 {@code UploadInitResponse} 表示
     */
    public static UploadInitResponse uploadSession(String uploadId, Long chunkSize, Integer chunkCount) {
        return new UploadInitResponse(false, null, uploadId, List.of(), chunkSize, chunkCount);
    }

    /**
     * 完成 {@code uploadSession} 对应的处理。
     *
     * @param uploadId 分片上传任务唯一标识
     * @param uploadedChunks 方法参数 {@code uploadedChunks}
     * @param chunkSize 方法参数 {@code chunkSize}
     * @param chunkCount 方法参数 {@code chunkCount}
     * @return 方法执行结果，具体结构由返回类型 {@code UploadInitResponse} 表示
     */
    public static UploadInitResponse uploadSession(
            String uploadId,
            List<Integer> uploadedChunks,
            Long chunkSize,
            Integer chunkCount
    ) {
        return new UploadInitResponse(false, null, uploadId, uploadedChunks, chunkSize, chunkCount);
    }
}
