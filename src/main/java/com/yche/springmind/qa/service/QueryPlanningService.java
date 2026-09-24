package com.yche.springmind.qa.service;

import com.yche.springmind.ai.config.AiChatModelProvider;
import com.yche.springmind.common.exception.BusinessException;
import com.yche.springmind.qa.model.QueryPlanResult;
import com.yche.springmind.qa.model.QueryPlanStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 调用模型选择检索策略并规范化规划结果，规划失败时安全回退到原始问题。
 *
 * <p>位于业务服务层：编排领域操作和基础设施调用，并集中维护事务、权限校验及失败处理边界。</p>
 */
@Service
public class QueryPlanningService {

    private static final Logger log = LoggerFactory.getLogger(QueryPlanningService.class);
    private static final int MAX_QUERY_COUNT = 3;

    private final PromptTemplate queryPlanningUserPromptTemplate;
    private final AiChatModelProvider chatModelProvider;

    /**
     * 创建并初始化 {@link QueryPlanningService}，保存该组件运行所需的依赖与配置。
     * <p>
     * 实现要点：根据问题生成检索计划；调用大模型完成推理或生成。
     *
     * @param queryPlanningUserPromptTemplate 方法参数 {@code queryPlanningUserPromptTemplate}
     * @param chatModelProvider 方法参数 {@code chatModelProvider}
     */
    public QueryPlanningService(
            @Qualifier("queryPlanningUserPromptTemplate") PromptTemplate queryPlanningUserPromptTemplate,
            AiChatModelProvider chatModelProvider
    ) {
        this.queryPlanningUserPromptTemplate = queryPlanningUserPromptTemplate;
        this.chatModelProvider = chatModelProvider;
    }

    /**
     * 分析用户问题并生成检索计划；模型不可用时退化为直接检索。
     * <p>
     * 实现要点：根据问题生成检索计划。
     *
     * @param question 用户提交的自然语言问题
     * @return 方法执行结果，具体结构由返回类型 {@code QueryPlanResult} 表示
     */
    public QueryPlanResult plan(String question) {
        return plan(null, question);
    }

    /**
     * 分析用户问题并生成检索计划；模型不可用时退化为直接检索。
     * <p>
     * 实现要点：根据问题生成检索计划；先校验输入、状态或业务边界；调用大模型完成推理或生成；捕获依赖异常并转换、记录或执行降级策略。
     *
     * @param userId 用户唯一标识
     * @param question 用户提交的自然语言问题
     * @return 方法执行结果，具体结构由返回类型 {@code QueryPlanResult} 表示
     */
    public QueryPlanResult plan(Long userId, String question) {
        String normalizedQuestion = requireQuestion(question);
        if (userId == null || userId <= 0) {
            return QueryPlanResult.fallback(normalizedQuestion);
        }
        try {

            Prompt planPrompt = queryPlanningUserPromptTemplate.create(Map.of("question", normalizedQuestion));

            QueryPlanResult rawResult = chatClient().prompt(planPrompt)
                    .call()
                    .entity(QueryPlanResult.class);
            return validatePlan(rawResult, normalizedQuestion);
        } catch (RuntimeException exception) {
            log.warn("Query planning failed, fallback to direct query. question={}", normalizedQuestion, exception);
            return QueryPlanResult.fallback(normalizedQuestion);
        }
    }

    /**
     * 执行 {@code chatClient} 对应的业务步骤。
     * <p>
     * 实现要点：调用大模型完成推理或生成。
     *
     * @return 方法执行结果，具体结构由返回类型 {@code ChatClient} 表示
     */
    private ChatClient chatClient() {
        return ChatClient.builder(chatModelProvider.getChatModel()).build();
    }

    /**
     * 执行 {@code validatePlan} 对应的业务步骤。
     * <p>
     * 实现要点：根据问题生成检索计划；先校验输入、状态或业务边界。
     *
     * @param rawResult 方法参数 {@code rawResult}
     * @param originalQuestion 方法参数 {@code originalQuestion}
     * @return 方法执行结果，具体结构由返回类型 {@code QueryPlanResult} 表示
     */
    private QueryPlanResult validatePlan(QueryPlanResult rawResult, String originalQuestion) {
        if (rawResult == null || rawResult.strategy() == null) {
            return QueryPlanResult.fallback(originalQuestion);
        }
        Set<String> normalizedQueries = normalizeQueries(rawResult.queries());
        if (normalizedQueries.isEmpty()) {
            return QueryPlanResult.fallback(originalQuestion);
        }
        List<String> finalQueries = switch (rawResult.strategy()) {
            case DIRECT -> List.of(originalQuestion);
            case REWRITE -> buildRewriteQueries(originalQuestion, normalizedQueries);
            case DECOMPOSE -> limitQueries(normalizedQueries);
        };
        if (finalQueries.isEmpty()) {
            return QueryPlanResult.fallback(originalQuestion);
        }
        return new QueryPlanResult(rawResult.strategy(), finalQueries);
    }

    /**
     * 执行 {@code buildRewriteQueries} 对应的业务步骤。
     * <p>
     * 实现要点：先校验输入、状态或业务边界。
     *
     * @param originalQuestion 方法参数 {@code originalQuestion}
     * @param normalizedQueries 方法参数 {@code normalizedQueries}
     * @return 符合条件的结果集合；无结果时返回空集合
     */
    private List<String> buildRewriteQueries(String originalQuestion, Set<String> normalizedQueries) {
        LinkedHashSet<String> rewriteQueries = new LinkedHashSet<>();
        rewriteQueries.add(originalQuestion);
        rewriteQueries.addAll(normalizedQueries);
        return limitQueries(rewriteQueries);
    }

    /**
     * 执行 {@code normalizeQueries} 对应的业务步骤。
     * <p>
     * 实现要点：先校验输入、状态或业务边界。
     *
     * @param queries 方法参数 {@code queries}
     * @return 符合条件的结果集合；无结果时返回空集合
     */
    private Set<String> normalizeQueries(List<String> queries) {
        LinkedHashSet<String> normalizedQueries = new LinkedHashSet<>();
        if (queries == null) {
            return normalizedQueries;
        }
        for (String query : queries) {
            if (!StringUtils.hasText(query)) {
                continue;
            }
            String normalized = query.replaceAll("\\s+", " ").trim();
            if (StringUtils.hasText(normalized)) {
                normalizedQueries.add(normalized);
            }
        }
        return normalizedQueries;
    }

    /**
     * 执行 {@code limitQueries} 对应的业务步骤。
     *
     * @param queries 方法参数 {@code queries}
     * @return 符合条件的结果集合；无结果时返回空集合
     */
    private List<String> limitQueries(Set<String> queries) {
        return queries.stream()
                .limit(MAX_QUERY_COUNT)
                .toList();
    }

    /**
     * 执行 {@code requireQuestion} 对应的业务步骤。
     * <p>
     * 实现要点：先校验输入、状态或业务边界。
     *
     * @param question 用户提交的自然语言问题
     * @return 处理后得到的字符串结果
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    private String requireQuestion(String question) {
        if (!StringUtils.hasText(question)) {
            throw new BusinessException("问题不能为空");
        }
        return question.replaceAll("\\s+", " ").trim();
    }
}
