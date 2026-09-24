package com.yche.springmind.qa.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 定义要求模型返回的结构化答案、证据判断和引用编号。
 *
 * <p>用于在查询规划、证据检索和答案生成阶段之间传递结构化数据。</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KnowledgeAnswerOutput(
        boolean answered,
        String answer,
        String reasonCode,
        String reasonMessage
) {
}
