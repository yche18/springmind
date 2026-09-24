package com.yche.springmind.qa.rag;

/**
 * 定义依据问题和知识组权限召回证据的统一接口。
 *
 * <p>位于 RAG 检索链路：把问题转换为受知识组权限约束的证据集合，供回答生成阶段使用。</p>
 */
public interface EvidenceRetriever {

    int DEFAULT_TOP_K = 5;

    /**
     * 依据查询条件检索与当前群组相关的证据片段。
     *
     * @param groupId 群组唯一标识
     * @param question 用户提交的自然语言问题
     * @return 方法执行结果，具体结构由返回类型 {@code RetrievedEvidenceBundle} 表示
     */
    default RetrievedEvidenceBundle retrieve(Long groupId, String question) {
        return retrieve(groupId, question, DEFAULT_TOP_K);
    }

    /**
     * 依据查询条件检索与当前群组相关的证据片段。
     *
     * @param userId 用户唯一标识
     * @param groupId 群组唯一标识
     * @param question 用户提交的自然语言问题
     * @return 方法执行结果，具体结构由返回类型 {@code RetrievedEvidenceBundle} 表示
     */
    default RetrievedEvidenceBundle retrieve(Long userId, Long groupId, String question) {
        return retrieve(userId, groupId, question, DEFAULT_TOP_K);
    }

    /**
     * 依据查询条件检索与当前群组相关的证据片段。
     *
     * @param userId 用户唯一标识
     * @param groupId 群组唯一标识
     * @param question 用户提交的自然语言问题
     * @param topK 最多返回的候选结果数量
     * @return 方法执行结果，具体结构由返回类型 {@code RetrievedEvidenceBundle} 表示
     */
    default RetrievedEvidenceBundle retrieve(Long userId, Long groupId, String question, int topK) {
        return retrieve(groupId, question, topK);
    }

    /**
     * 依据查询条件检索与当前群组相关的证据片段。
     *
     * @param groupId 群组唯一标识
     * @param question 用户提交的自然语言问题
     * @param topK 最多返回的候选结果数量
     * @return 方法执行结果，具体结构由返回类型 {@code RetrievedEvidenceBundle} 表示
     */
    RetrievedEvidenceBundle retrieve(Long groupId, String question, int topK);
}
