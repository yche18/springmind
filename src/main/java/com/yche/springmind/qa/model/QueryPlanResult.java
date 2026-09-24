package com.yche.springmind.qa.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * 表示查询规划阶段选择的检索策略、改写问题和拆分后的子查询。
 *
 * <p>用于在查询规划、证据检索和答案生成阶段之间传递结构化数据。</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record QueryPlanResult(
        QueryPlanStrategy strategy,
        List<String> queries
) {

    /**
     * 完成 {@code fallback} 对应的处理。
     * <p>
     * 实现要点：根据问题生成检索计划。
     *
     * @param question 用户提交的自然语言问题
     * @return 方法执行结果，具体结构由返回类型 {@code QueryPlanResult} 表示
     */
    public static QueryPlanResult fallback(String question) {
        return new QueryPlanResult(QueryPlanStrategy.DIRECT, List.of(question));
    }
}
