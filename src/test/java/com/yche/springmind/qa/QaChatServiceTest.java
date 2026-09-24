package com.yche.springmind.qa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.yche.springmind.ai.config.AiChatModelProvider;
import com.yche.springmind.qa.model.EvidenceLevel;
import com.yche.springmind.qa.model.vo.AskQuestionResponse;
import com.yche.springmind.qa.rag.EvidenceRetriever;
import com.yche.springmind.qa.rag.RetrievedEvidenceBundle;
import com.yche.springmind.qa.service.QaChatService;
import com.yche.springmind.qa.support.CitationAssembler;
import com.yche.springmind.qa.support.QaAnswerParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;

class QaChatServiceTest {

    @Test
    void shouldReturnUnansweredWithoutEvidenceWithoutResolvingModel() {
        EvidenceRetriever retriever = mock(EvidenceRetriever.class);
        AiChatModelProvider provider = mock(AiChatModelProvider.class);
        when(retriever.retrieve(7L, 2001L, "上传流程")).thenReturn(RetrievedEvidenceBundle.empty());

        AskQuestionResponse response = service(retriever, provider).ask(7L, 2001L, "上传流程");

        assertThat(response.answered()).isFalse();
        assertThat(response.reasonCode()).isEqualTo("INSUFFICIENT_EVIDENCE");
        verifyNoInteractions(provider);
    }

    @Test
    void shouldReturnGroundedAnswerWithCitations() {
        EvidenceRetriever retriever = mock(EvidenceRetriever.class);
        AiChatModelProvider provider = mock(AiChatModelProvider.class);
        ChatModel chatModel = mock(ChatModel.class);
        when(retriever.retrieve(7L, 2001L, "上传流程")).thenReturn(bundle());
        when(provider.getChatModel()).thenReturn(chatModel);
        when(chatModel.call(any(Prompt.class))).thenReturn(response("""
                {"answered":true,"answer":"产品团队每两周发布一次。"}
                """));

        AskQuestionResponse answer = service(retriever, provider).ask(7L, 2001L, "上传流程");

        assertThat(answer.answered()).isTrue();
        assertThat(answer.answer()).isEqualTo("产品团队每两周发布一次。");
        assertThat(answer.citations()).hasSize(1);
        verify(provider).getChatModel();
        verify(chatModel).call(any(Prompt.class));
    }

    @Test
    void shouldFallbackToRawAnswerThroughASecondModelInvocation() {
        EvidenceRetriever retriever = mock(EvidenceRetriever.class);
        AiChatModelProvider provider = mock(AiChatModelProvider.class);
        ChatModel chatModel = mock(ChatModel.class);
        when(retriever.retrieve(7L, 2001L, "上传流程")).thenReturn(bundle());
        when(provider.getChatModel()).thenReturn(chatModel);
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(response("not-json"))
                .thenReturn(response("""
                        {"answered":true,"answer":"产品团队每两周发布一次。"}
                        """));

        AskQuestionResponse answer = service(retriever, provider).ask(7L, 2001L, "上传流程");

        assertThat(answer.answered()).isTrue();
        assertThat(answer.answer()).isEqualTo("产品团队每两周发布一次。");
        verify(chatModel, times(2)).call(any(Prompt.class));
    }

    @Test
    void shouldRejectLegacyCallWithoutActualUserContext() {
        QaChatService service = service(
                mock(EvidenceRetriever.class),
                mock(AiChatModelProvider.class)
        );

        assertThatThrownBy(() -> service.ask(2001L, "上传流程"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("实际用户上下文");
    }

    private QaChatService service(EvidenceRetriever retriever, AiChatModelProvider provider) {
        return new QaChatService(
                promptTemplate("系统规则"),
                retrievalAdvisor(),
                promptTemplate("问题：{question}\n证据等级：{evidenceLevel}\n回答策略：{evidenceGuidance}"),
                retriever,
                new QaAnswerParser(new ObjectMapper()),
                new CitationAssembler(),
                provider
        );
    }

    private RetrievedEvidenceBundle bundle() {
        return new RetrievedEvidenceBundle(List.of(Document.builder().id("E1").text("产品团队每两周发布一次。")
                .metadata(Map.of("evidenceId", "E1", "documentId", 1001L, "chunkId", 9001L,
                        "chunkIndex", 0, "fileName", "产品手册.md", "score", 0.91D)).build()),
                EvidenceLevel.SUFFICIENT, "仅基于证据回答。");
    }

    private ChatResponse response(String content) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(content))));
    }

    private PromptTemplate promptTemplate(String template) {
        return PromptTemplate.builder().template(template).build();
    }

    private RetrievalAugmentationAdvisor retrievalAdvisor() {
        DocumentRetriever retriever = mock(DocumentRetriever.class);
        when(retriever.retrieve(any())).thenReturn(List.of());
        return RetrievalAugmentationAdvisor.builder().documentRetriever(retriever).build();
    }
}
