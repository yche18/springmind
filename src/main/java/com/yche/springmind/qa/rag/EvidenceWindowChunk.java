package com.yche.springmind.qa.rag;

public record EvidenceWindowChunk(Long chunkId, int index, String text, String retrievalSource) {
    public EvidenceWindowChunk(Long chunkId, int index, String text) {
        this(chunkId, index, text, "UNKNOWN");
    }
}
