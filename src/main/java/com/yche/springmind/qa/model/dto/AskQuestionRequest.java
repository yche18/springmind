package com.yche.springmind.qa.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 承载知识组、用户问题和可选会话上下文等问答输入。
 *
 * <p>这是接口输入契约，只承载并校验调用方提交的数据，不在对象内部实现业务流程。</p>
 */
public class AskQuestionRequest {

    @NotNull(message = "groupId 不能为空")
    @Positive(message = "groupId 非法")
    private Long groupId;

    @NotBlank(message = "问题不能为空")
    @Size(max = 2000, message = "问题长度不能超过 2000")
    private String question;

    /**
     * 返回 {@code groupId} 对应的配置或状态值。
     *
     * @return 计算或处理得到的数值结果
     */
    public Long getGroupId() {
        return groupId;
    }

    /**
     * 更新 {@code groupId} 对应的配置或状态值。
     *
     * @param groupId 群组唯一标识
     */
    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }

    /**
     * 返回 {@code question} 对应的配置或状态值。
     *
     * @return 处理后得到的字符串结果
     */
    public String getQuestion() {
        return question;
    }

    /**
     * 更新 {@code question} 对应的配置或状态值。
     *
     * @param question 用户提交的自然语言问题
     */
    public void setQuestion(String question) {
        this.question = question;
    }
}
