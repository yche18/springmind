package com.yche.springmind.qa.rag;

import com.yche.springmind.common.exception.BusinessException;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 通过 Spring AI Retriever 适配器检索已完成入库的文档切片。
 *
 * <p>位于 RAG 检索链路：把问题转换为受知识组权限约束的证据集合，供回答生成阶段使用。</p>
 */
@Component
public class ReadyChunkDocumentRetriever implements DocumentRetriever {

    private static final String GROUP_ID_CONTEXT_KEY = "groupId";
    public static final String PREFETCHED_DOCUMENTS_CONTEXT_KEY = "qaRetrievedDocuments";

    private final EvidenceRetriever evidenceRetriever;
    private final int topK;

    /**
     * 创建并初始化 {@link ReadyChunkDocumentRetriever}，保存该组件运行所需的依赖与配置。
     * <p>
     * 实现要点：执行混合检索与结果融合。
     *
     * @param evidenceRetriever 方法参数 {@code evidenceRetriever}
     */
    @Autowired
    public ReadyChunkDocumentRetriever(
            EvidenceRetriever evidenceRetriever
    ) {
        this(evidenceRetriever, EvidenceRetriever.DEFAULT_TOP_K);
    }

    /**
     * 创建并初始化 {@link ReadyChunkDocumentRetriever}，保存该组件运行所需的依赖与配置。
     * <p>
     * 实现要点：执行混合检索与结果融合。
     *
     * @param evidenceRetriever 方法参数 {@code evidenceRetriever}
     * @param topK 最多返回的候选结果数量
     */
    public ReadyChunkDocumentRetriever(
            EvidenceRetriever evidenceRetriever,
            int topK
    ) {
        this.evidenceRetriever = evidenceRetriever;
        this.topK = topK > 0 ? topK : EvidenceRetriever.DEFAULT_TOP_K;
    }

    /**
     * 依据查询条件检索与当前群组相关的证据片段。
     * <p>
     * 实现要点：先校验输入、状态或业务边界。
     *
     * @param query 用于检索或筛选的查询条件
     * @return 符合条件的结果集合；无结果时返回空集合
     */
    @Override
    public List<Document> retrieve(Query query) {
        Query validQuery = requireQuery(query);
        Long groupId = requireGroupId(validQuery);
        List<Document> prefetchedDocuments = readPrefetchedDocuments(validQuery);
        if (prefetchedDocuments != null) {
            return prefetchedDocuments;
        }
        return retrieve(groupId, query.text());
    }

    /**
     * 依据查询条件检索与当前群组相关的证据片段。
     * <p>
     * 实现要点：执行混合检索与结果融合。
     *
     * @param groupId 群组唯一标识
     * @param question 用户提交的自然语言问题
     * @return 符合条件的结果集合；无结果时返回空集合
     */
    public List<Document> retrieve(Long groupId, String question) {
        return retrieveEvidence(groupId, question).documents();
    }

    /**
     * 执行 {@code retrieveEvidence} 对应的业务步骤。
     * <p>
     * 实现要点：执行混合检索与结果融合。
     *
     * @param groupId 群组唯一标识
     * @param question 用户提交的自然语言问题
     * @return 方法执行结果，具体结构由返回类型 {@code RetrievedEvidenceBundle} 表示
     */
    public RetrievedEvidenceBundle retrieveEvidence(Long groupId, String question) {
        return evidenceRetriever.retrieve(groupId, question, topK);
    }

    /**
     * 执行 {@code requireQuery} 对应的业务步骤。
     * <p>
     * 实现要点：先校验输入、状态或业务边界。
     *
     * @param query 用于检索或筛选的查询条件
     * @return 方法执行结果，具体结构由返回类型 {@code Query} 表示
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    private Query requireQuery(Query query) {
        if (query == null) {
            throw new BusinessException("检索请求不能为空");
        }
        return query;
    }

    /**
     * 执行 {@code requireGroupId} 对应的业务步骤。
     * <p>
     * 实现要点：先校验输入、状态或业务边界；捕获依赖异常并转换、记录或执行降级策略。
     *
     * @param query 用于检索或筛选的查询条件
     * @return 计算或处理得到的数值结果
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    private Long requireGroupId(Query query) {
        Object groupId = query.context().get(GROUP_ID_CONTEXT_KEY);
        if (groupId instanceof Number) {
            return requirePositiveGroupId(((Number) groupId).longValue());
        }
        if (groupId instanceof String && StringUtils.hasText((String) groupId)) {
            try {
                return requirePositiveGroupId(Long.parseLong(((String) groupId).trim()));
            } catch (NumberFormatException exception) {
                throw new BusinessException("检索上下文中的 groupId 非法", exception);
            }
        }
        throw new BusinessException("检索上下文缺少 groupId");
    }

    /**
     * 执行 {@code readPrefetchedDocuments} 对应的业务步骤。
     *
     * @param query 用于检索或筛选的查询条件
     * @return 符合条件的结果集合；无结果时返回空集合
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    private List<Document> readPrefetchedDocuments(Query query) {
        Object documents = query.context().get(PREFETCHED_DOCUMENTS_CONTEXT_KEY);
        if (documents == null) {
            return null;
        }
        if (!(documents instanceof List<?> documentList)) {
            throw new BusinessException("检索上下文中的预取证据格式非法");
        }
        for (Object document : documentList) {
            if (!(document instanceof Document)) {
                throw new BusinessException("检索上下文中的预取证据格式非法");
            }
        }
        @SuppressWarnings("unchecked")
        List<Document> castedDocuments = (List<Document>) documentList;
        return List.copyOf(castedDocuments);
    }

    /**
     * 执行 {@code requirePositiveGroupId} 对应的业务步骤。
     * <p>
     * 实现要点：先校验输入、状态或业务边界。
     *
     * @param groupId 群组唯一标识
     * @return 计算或处理得到的数值结果
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    private Long requirePositiveGroupId(long groupId) {
        if (groupId <= 0) {
            throw new BusinessException("groupId 非法");
        }
        return groupId;
    }

}
