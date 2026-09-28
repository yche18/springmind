package com.yche.springmind.ingestion.service;

/** Convert a stored source document into persisted, searchable artifacts. */
public interface DocumentIngestionProcessor {

    /** Process one document within its group isolation boundary. */
    void process(Long documentId, Long groupId);
}
