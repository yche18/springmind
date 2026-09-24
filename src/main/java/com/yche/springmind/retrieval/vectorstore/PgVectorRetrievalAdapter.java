package com.yche.springmind.retrieval.vectorstore;

import com.yche.springmind.common.exception.BusinessException;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * 使用 pgvector 执行语义相似度检索，并应用知识组、文档状态等安全过滤条件。
 *
 * <p>封装 pgvector 语义检索细节，并确保查询受到知识组和文档状态条件约束。</p>
 */
@Component
public class PgVectorRetrievalAdapter {

    private final VectorStore vectorStore;

    /**
     * 创建并初始化 {@link PgVectorRetrievalAdapter}，保存该组件运行所需的依赖与配置。
     * <p>
     * 实现要点：写入或查询 pgvector 向量索引。
     *
     * @param vectorStore 方法参数 {@code vectorStore}
     */
    public PgVectorRetrievalAdapter(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /**
     * 在限定群组范围内执行关键词检索，并返回归一化后的候选切片。
     * <p>
     * 实现要点：维护或查询 Elasticsearch 关键词索引；写入或查询 pgvector 向量索引。
     *
     * @param groupId 群组唯一标识
     * @param question 用户提交的自然语言问题
     * @param topK 最多返回的候选结果数量
     * @return 符合条件的结果集合；无结果时返回空集合
     */
    public List<VectorHit> search(Long groupId, String question, int topK) {
        SearchRequest searchRequest = SearchRequest.builder()
                .query(question)
                .topK(topK)
                .filterExpression(new FilterExpressionBuilder().eq("groupId", groupId).build())
                .build();
        return vectorStore.similaritySearch(searchRequest).stream()
                .map(document -> toVectorHit(groupId, document))
                .toList();
    }

    /**
     * 完成 {@code toVectorHit} 对应的处理。
     * <p>
     * 实现要点：先校验输入、状态或业务边界。
     *
     * @param expectedGroupId expected群组唯一标识
     * @param document 当前处理的文档实体
     * @return 方法执行结果，具体结构由返回类型 {@code VectorHit} 表示
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    private VectorHit toVectorHit(Long expectedGroupId, Document document) {
        Map<String, Object> metadata = document.getMetadata();
        Long groupId = requireLong(metadata, "groupId");
        if (!expectedGroupId.equals(groupId)) {
            throw new BusinessException("向量检索返回了跨群组数据");
        }
        return new VectorHit(
                requireLong(metadata, "documentId"),
                requireLong(metadata, "chunkId"),
                requireInteger(metadata, "chunkIndex"),
                requireText(document.getText()),
                document.getScore() == null ? 0D : document.getScore()
        );
    }

    /**
     * 完成 {@code requireLong} 对应的处理。
     * <p>
     * 实现要点：先校验输入、状态或业务边界；捕获依赖异常并转换、记录或执行降级策略。
     *
     * @param metadata 方法参数 {@code metadata}
     * @param key 方法参数 {@code key}
     * @return 计算或处理得到的数值结果
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    private Long requireLong(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String && StringUtils.hasText((String) value)) {
            try {
                return Long.parseLong(((String) value).trim());
            } catch (NumberFormatException exception) {
                throw new BusinessException("向量检索元数据格式非法: " + key, exception);
            }
        }
        throw new BusinessException("向量检索缺少必要元数据: " + key);
    }

    /**
     * 完成 {@code requireInteger} 对应的处理。
     * <p>
     * 实现要点：先校验输入、状态或业务边界；捕获依赖异常并转换、记录或执行降级策略。
     *
     * @param metadata 方法参数 {@code metadata}
     * @param key 方法参数 {@code key}
     * @return 计算或处理得到的数值结果
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    private Integer requireInteger(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String && StringUtils.hasText((String) value)) {
            try {
                return Integer.parseInt(((String) value).trim());
            } catch (NumberFormatException exception) {
                throw new BusinessException("向量检索元数据格式非法: " + key, exception);
            }
        }
        throw new BusinessException("向量检索缺少必要元数据: " + key);
    }

    /**
     * 完成 {@code requireText} 对应的处理。
     * <p>
     * 实现要点：先校验输入、状态或业务边界。
     *
     * @param text 方法参数 {@code text}
     * @return 处理后得到的字符串结果
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    private String requireText(String text) {
        if (!StringUtils.hasText(text)) {
            throw new BusinessException("向量检索返回空切片");
        }
        return text.trim();
    }

    /**
     * 表示一次向量召回命中的切片、相似度和关联元数据。
     *
     * <p>仅在 {@code PgVectorRetrievalAdapter} 的实现过程中使用，用不可变数据结构收拢中间结果，避免参数和值的含义混淆。</p>
     */
    public record VectorHit(
            Long documentId,
            Long chunkId,
            Integer chunkIndex,
            String chunkText,
            double score
    ) {
    }
}
