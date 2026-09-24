package com.yche.springmind.qa.rag;

import com.yche.springmind.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 利用模型把口语化或依赖上下文的问题改写为更适合知识库检索的独立查询。
 *
 * <p>位于 RAG 检索链路：把问题转换为受知识组权限约束的证据集合，供回答生成阶段使用。</p>
 */
@Service
public class QuestionQueryRewriteService {

    private static final Pattern SPLIT_PATTERN = Pattern.compile("[？?。；;]");

    /**
     * 结合原问题与上下文生成更适合知识库检索的查询表达。
     * <p>
     * 实现要点：先校验输入、状态或业务边界。
     *
     * @param question 用户提交的自然语言问题
     * @return 符合条件的结果集合；无结果时返回空集合
     */
    public List<String> rewrite(String question) {
        String normalizedQuestion = requireQuestion(question);
        Set<String> rewrittenQueries = new LinkedHashSet<>();
        rewrittenQueries.add(normalizedQuestion);

        String simplifiedQuestion = simplifyQuestion(normalizedQuestion);
        if (StringUtils.hasText(simplifiedQuestion)) {
            rewrittenQueries.add(simplifiedQuestion);
        }
        for (String splitQuery : splitQuestion(normalizedQuestion)) {
            rewrittenQueries.add(splitQuery);
        }
        return List.copyOf(rewrittenQueries);
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

    /**
     * 执行 {@code simplifyQuestion} 对应的业务步骤。
     *
     * @param question 用户提交的自然语言问题
     * @return 处理后得到的字符串结果
     */
    private String simplifyQuestion(String question) {
        String simplified = question
                .replaceFirst("^(请问|请|帮我|麻烦你|麻烦|想知道|我想知道)", "")
                .replaceFirst("(是什么|是啥|有哪些|怎么做|如何处理|如何实现|请说明)$", "")
                .trim();
        return simplified.equals(question) ? null : simplified;
    }

    /**
     * 执行 {@code splitQuestion} 对应的业务步骤。
     * <p>
     * 实现要点：先校验输入、状态或业务边界。
     *
     * @param question 用户提交的自然语言问题
     * @return 符合条件的结果集合；无结果时返回空集合
     */
    private List<String> splitQuestion(String question) {
        Set<String> splitQueries = new LinkedHashSet<>();
        for (String fragment : SPLIT_PATTERN.split(question)) {
            String normalized = fragment.trim();
            if (normalized.length() >= 4) {
                splitQueries.add(normalized);
            }
        }
        return List.copyOf(splitQueries);
    }
}
