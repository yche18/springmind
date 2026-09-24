package com.yche.springmind.qa.model.vo;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * 向前端返回答案、证据等级、引用来源及拒答说明。
 *
 * <p>这是接口输出视图，用于向前端稳定地暴露所需字段，避免直接返回数据库实体。</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AskQuestionResponse(
        boolean answered,
        String answer,
        String reasonCode,
        String reasonMessage,
        List<Citation> citations
) {

    /**
     * 完成 {@code answered} 对应的处理。
     * <p>
     * 实现要点：根据命中证据组装可追溯引用。
     *
     * @param answer 方法参数 {@code answer}
     * @param citations 方法参数 {@code citations}
     * @return 方法执行结果，具体结构由返回类型 {@code AskQuestionResponse} 表示
     */
    public static AskQuestionResponse answered(String answer, List<Citation> citations) {
        return new AskQuestionResponse(true, answer, null, null, citations);
    }

    /**
     * 完成 {@code unanswered} 对应的处理。
     * <p>
     * 实现要点：根据命中证据组装可追溯引用。
     *
     * @param reasonCode 方法参数 {@code reasonCode}
     * @param reasonMessage 方法参数 {@code reasonMessage}
     * @param citations 方法参数 {@code citations}
     * @return 方法执行结果，具体结构由返回类型 {@code AskQuestionResponse} 表示
     */
    public static AskQuestionResponse unanswered(
            String reasonCode,
            String reasonMessage,
            List<Citation> citations
    ) {
        return new AskQuestionResponse(false, null, reasonCode, reasonMessage, citations);
    }

    /**
     * 表示答案中的一条可追溯引用及其文档、切片和相关性信息。
     *
     * <p>仅在 {@code AskQuestionResponse} 的实现过程中使用，用不可变数据结构收拢中间结果，避免参数和值的含义混淆。</p>
     */
    public record Citation(
            Long documentId,
            Long chunkId,
            Integer chunkIndex,
            String fileName,
            double score,
            String snippet
    ) {
    }
}
