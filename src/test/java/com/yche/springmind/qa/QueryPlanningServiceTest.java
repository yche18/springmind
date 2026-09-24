package com.yche.springmind.qa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.yche.springmind.ai.config.AiChatModelProvider;
import com.yche.springmind.qa.model.QueryPlanResult;
import com.yche.springmind.qa.model.QueryPlanStrategy;
import com.yche.springmind.qa.service.QueryPlanningService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;

class QueryPlanningServiceTest {

    @Test
    void shouldReturnStructuredQueryPlan() {
        AiChatModelProvider provider = mock(AiChatModelProvider.class);
        ChatModel chatModel = mock(ChatModel.class);
        when(provider.getChatModel()).thenReturn(chatModel);
        when(chatModel.call(any(Prompt.class))).thenReturn(response("""
                {"strategy":"REWRITE","queries":["文档上传流程"]}
                """));

        QueryPlanResult result = service(provider).plan(7L, "请问上传流程是什么");

        assertThat(result.strategy()).isEqualTo(QueryPlanStrategy.REWRITE);
        assertThat(result.queries()).containsExactly("请问上传流程是什么", "文档上传流程");
        verify(provider).getChatModel();
        verify(chatModel).call(any(Prompt.class));
    }

    @Test
    void shouldFallbackWhenUserContextIsMissingWithoutModelInvocation() {
        AiChatModelProvider provider = mock(AiChatModelProvider.class);

        QueryPlanResult result = service(provider).plan("上传流程");

        assertThat(result).isEqualTo(QueryPlanResult.fallback("上传流程"));
        verifyNoInteractions(provider);
    }

    @Test
    void shouldFallbackWhenModelInvocationFails() {
        AiChatModelProvider provider = mock(AiChatModelProvider.class);
        ChatModel chatModel = mock(ChatModel.class);
        when(provider.getChatModel()).thenReturn(chatModel);
        when(chatModel.call(any(Prompt.class))).thenThrow(new IllegalStateException("unavailable"));

        QueryPlanResult result = service(provider).plan(7L, "上传流程");

        assertThat(result).isEqualTo(QueryPlanResult.fallback("上传流程"));
    }

    private QueryPlanningService service(AiChatModelProvider provider) {
        return new QueryPlanningService(
                PromptTemplate.builder().template("问题：{question}").build(),
                provider
        );
    }

    private ChatResponse response(String content) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(content))));
    }
}
