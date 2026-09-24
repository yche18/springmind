package com.yche.springmind.qa.model;

/**
 * 定义直接检索、问题改写或问题拆解等查询规划策略。
 *
 * <p>用于在查询规划、证据检索和答案生成阶段之间传递结构化数据。</p>
 */
public enum QueryPlanStrategy {
    DIRECT,
    REWRITE,
    DECOMPOSE
}
