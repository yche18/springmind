package com.yche.springmind.qa.rag;

/** Retrieve group-scoped evidence and report whether it can support an answer. */
public interface EvidenceRetriever {

    int DEFAULT_TOP_K = 5;

    default RetrievedEvidenceBundle retrieve(Long groupId, String question) {
        return retrieve(groupId, question, DEFAULT_TOP_K);
    }

    default RetrievedEvidenceBundle retrieve(Long userId, Long groupId, String question) {
        return retrieve(userId, groupId, question, DEFAULT_TOP_K);
    }

    default RetrievedEvidenceBundle retrieve(Long userId, Long groupId, String question, int topK) {
        return retrieve(groupId, question, topK);
    }

    /**
     * Retrieve at most {@code topK} ranked evidence items for a question.
     * Implementations must not return evidence from another group.
     */
    RetrievedEvidenceBundle retrieve(Long groupId, String question, int topK);
}
