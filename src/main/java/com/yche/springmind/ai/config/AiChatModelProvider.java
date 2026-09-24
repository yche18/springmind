package com.yche.springmind.ai.config;

import com.yche.springmind.common.exception.BusinessException;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 按当前配置创建并缓存聊天模型客户端，同时在配置缺失时提供清晰的失败提示。
 *
 * <p>位于配置层：集中声明配置项或组装 Spring Bean，使业务代码不直接依赖组件创建细节。</p>
 */
@Component
public class AiChatModelProvider {

    private static final RetryTemplate NO_RETRY =
            RetryTemplate.builder().maxAttempts(1).noBackoff().build();

    private final AiChatProperties properties;
    private volatile ChatModel chatModel;

    /**
     * 创建并初始化 {@link AiChatModelProvider}，保存该组件运行所需的依赖与配置。
     *
     * @param properties 方法参数 {@code properties}
     */
    public AiChatModelProvider(AiChatProperties properties) {
        this.properties = properties;
    }

    /**
     * 返回 {@code chatModel} 对应的配置或状态值。
     *
     * @return 查询得到的Chat模型结果
     */
    public ChatModel getChatModel() {
        ChatModel current = chatModel;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (chatModel == null) {
                chatModel = createChatModel();
            }
            return chatModel;
        }
    }

    /**
     * 创建或配置 {@code createChatModel} 所需的 Spring 组件。
     * <p>
     * 实现要点：对可恢复失败执行有限次数重试。
     *
     * @return 方法执行结果，具体结构由返回类型 {@code ChatModel} 表示
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    private ChatModel createChatModel() {
        if (!StringUtils.hasText(properties.getApiKey())) {
            throw new BusinessException("AI_CHAT_NOT_CONFIGURED");
        }
        if (!StringUtils.hasText(properties.getBaseUrl()) || !StringUtils.hasText(properties.getModel())) {
            throw new BusinessException("AI_CHAT_CONFIGURATION_INVALID");
        }

        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(properties.getBaseUrl().trim())
                .apiKey(properties.getApiKey().trim())
                .build();
        return OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(properties.getModel().trim())
                        .streamUsage(true)
                        .build())
                .retryTemplate(NO_RETRY)
                .build();
    }
}
