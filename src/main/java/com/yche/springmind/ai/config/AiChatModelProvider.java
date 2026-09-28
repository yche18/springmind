package com.yche.springmind.ai.config;

import com.yche.springmind.common.exception.BusinessException;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AiChatModelProvider {

    private static final RetryTemplate NO_RETRY =
            RetryTemplate.builder().maxAttempts(1).noBackoff().build();

    private final AiChatProperties properties;
    private volatile ChatModel chatModel;

    public AiChatModelProvider(AiChatProperties properties) {
        this.properties = properties;
    }

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
