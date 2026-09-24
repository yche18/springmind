package com.yche.springmind.qa;

import com.yche.springmind.ai.config.AiChatModelProvider;
import com.yche.springmind.ai.config.AiChatProperties;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiChatModelProviderTest {

    @Test
    void shouldRejectMissingApiKey() {
        AiChatProperties properties = new AiChatProperties();

        assertThatThrownBy(() -> new AiChatModelProvider(properties).getChatModel())
                .hasMessage("AI_CHAT_NOT_CONFIGURED");
    }

    @Test
    void shouldRejectIncompleteConfiguration() {
        AiChatProperties properties = new AiChatProperties();
        properties.setApiKey("test-key");
        properties.setModel(" ");

        assertThatThrownBy(() -> new AiChatModelProvider(properties).getChatModel())
                .hasMessage("AI_CHAT_CONFIGURATION_INVALID");
    }

    @Test
    void shouldCreateAndCacheOneChatModel() {
        AiChatProperties properties = new AiChatProperties();
        properties.setApiKey("test-key");
        AiChatModelProvider provider = new AiChatModelProvider(properties);

        ChatModel first = provider.getChatModel();
        ChatModel second = provider.getChatModel();

        assertThat(first).isSameAs(second);
    }
}
