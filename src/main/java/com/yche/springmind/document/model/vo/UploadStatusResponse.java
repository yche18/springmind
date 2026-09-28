package com.yche.springmind.document.model.vo;

import java.util.List;

public record UploadStatusResponse(
        String status,
        List<Integer> uploadedChunks,
        Integer uploadedChunkCount,
        Integer chunkCount
) {
}
