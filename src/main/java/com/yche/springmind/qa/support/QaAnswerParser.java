package com.yche.springmind.qa.support;

import com.yche.springmind.qa.model.KnowledgeAnswerOutput;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 解析并校验模型返回的结构化答案，在格式异常时构造可控的降级结果。
 *
 * <p>位于问答辅助层：对模型输出或检索证据进行确定性处理，保持核心问答服务职责清晰。</p>
 */
@Component
public class QaAnswerParser {

    private final ObjectMapper objectMapper;

    /**
     * 创建并初始化 {@link QaAnswerParser}，保存该组件运行所需的依赖与配置。
     *
     * @param objectMapper 方法参数 {@code objectMapper}
     */
    public QaAnswerParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 将输入内容解析为当前组件约定的结构化结果。
     * <p>
     * 实现要点：捕获依赖异常并转换、记录或执行降级策略。
     *
     * @param rawAnswer 方法参数 {@code rawAnswer}
     * @return 方法执行结果，具体结构由返回类型 {@code KnowledgeAnswerOutput} 表示
     */
    public KnowledgeAnswerOutput parse(String rawAnswer) {
        if (!StringUtils.hasText(rawAnswer)) {
            return null;
        }
        try {
            return objectMapper.readValue(rawAnswer, KnowledgeAnswerOutput.class);
        } catch (JsonProcessingException exception) {
            return null;
        }
    }
}
