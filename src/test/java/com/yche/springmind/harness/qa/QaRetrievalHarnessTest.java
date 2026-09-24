package com.yche.springmind.harness.qa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.yche.springmind.ai.config.AiChatModelProvider;
import com.yche.springmind.common.enums.SystemRole;
import com.yche.springmind.common.exception.BusinessException;
import com.yche.springmind.groupmembership.service.GroupMembershipService;
import com.yche.springmind.identity.service.CurrentUserService;
import com.yche.springmind.qa.model.EvidenceLevel;
import com.yche.springmind.qa.model.dto.AskQuestionRequest;
import com.yche.springmind.qa.model.vo.AskQuestionResponse;
import com.yche.springmind.qa.rag.EvidenceRetriever;
import com.yche.springmind.qa.rag.RetrievedEvidenceBundle;
import com.yche.springmind.qa.service.QaChatService;
import com.yche.springmind.qa.service.QaService;
import com.yche.springmind.qa.support.CitationAssembler;
import com.yche.springmind.qa.support.QaAnswerParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
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
import org.springframework.mock.web.MockHttpServletRequest;

class QaRetrievalHarnessTest {
    private static final Long USER_ID = 7L;
    private static final Long GROUP_ID = 2001L;
    private static final String QUESTION = "产品团队如何安排迭代？";

    @Test
    void readableMemberRetrievesEvidenceAndReturnsGroundedCitations() {
        HarnessRuntime runtime = createRuntime();
        when(runtime.evidenceRetriever().retrieve(USER_ID, GROUP_ID, QUESTION)).thenReturn(bundle());
        when(runtime.chatModel().call(any(Prompt.class))).thenReturn(response("""
                {"answered":true,"answer":"产品团队每两周发布一次。"}
                """));

        AskQuestionResponse result = runtime.qaService().ask(new MockHttpServletRequest(), askRequest());

        assertThat(result.answered()).isTrue();
        assertThat(result.citations()).hasSize(1);
        assertThat(result.citations().getFirst().documentId()).isEqualTo(3001L);
        verify(runtime.chatModelProvider()).getChatModel();
    }

    @Test
    void nonMemberIsRejectedBeforeRetrievalOrModelSideEffects() {
        HarnessRuntime runtime = createRuntime();
        when(runtime.groupMembershipService().requireGroupReadable(any(HttpServletRequest.class), eq(GROUP_ID)))
                .thenThrow(new BusinessException("当前用户不是目标群组成员"));

        assertThatThrownBy(() -> runtime.qaService().ask(new MockHttpServletRequest(), askRequest()))
                .isInstanceOf(BusinessException.class).hasMessage("当前用户不是目标群组成员");

        verifyNoInteractions(runtime.evidenceRetriever(), runtime.chatModelProvider(), runtime.chatModel());
    }

    @Test
    void emptyEvidenceReturnsUnansweredAndDoesNotCallModel() {
        HarnessRuntime runtime = createRuntime();
        when(runtime.evidenceRetriever().retrieve(USER_ID, GROUP_ID, QUESTION))
                .thenReturn(RetrievedEvidenceBundle.empty());

        AskQuestionResponse result = runtime.qaService().ask(new MockHttpServletRequest(), askRequest());

        assertThat(result.reasonCode()).isEqualTo("INSUFFICIENT_EVIDENCE");
        verifyNoInteractions(runtime.chatModelProvider(), runtime.chatModel());
    }

    private HarnessRuntime createRuntime() {
        GroupMembershipService membership = mock(GroupMembershipService.class);
        CurrentUserService currentUser = mock(CurrentUserService.class);
        when(currentUser.requireBusinessUser(any(HttpServletRequest.class)))
                .thenReturn(new CurrentUserService.CurrentUser(USER_ID, "u7", "user", SystemRole.USER, false));
        EvidenceRetriever retriever = mock(EvidenceRetriever.class);
        AiChatModelProvider provider = mock(AiChatModelProvider.class);
        ChatModel chatModel = mock(ChatModel.class);
        when(provider.getChatModel()).thenReturn(chatModel);
        DocumentRetriever advisorRetriever = mock(DocumentRetriever.class);
        when(advisorRetriever.retrieve(any())).thenReturn(List.of());
        RetrievalAugmentationAdvisor advisor = RetrievalAugmentationAdvisor.builder()
                .documentRetriever(advisorRetriever).build();
        QaChatService chat = new QaChatService(template("系统规则"), advisor,
                template("问题：{question}\n证据等级：{evidenceLevel}\n回答策略：{evidenceGuidance}"), retriever,
                new QaAnswerParser(new ObjectMapper()), new CitationAssembler(), provider);
        return new HarnessRuntime(
                new QaService(membership, chat, currentUser),
                membership,
                retriever,
                provider,
                chatModel
        );
    }

    private AskQuestionRequest askRequest() {
        AskQuestionRequest request = new AskQuestionRequest();
        request.setGroupId(GROUP_ID);
        request.setQuestion(QUESTION);
        return request;
    }

    private RetrievedEvidenceBundle bundle() {
        return new RetrievedEvidenceBundle(List.of(Document.builder().id("E1").text("产品团队每两周发布一次。")
                .metadata(Map.of("evidenceId", "E1", "documentId", 3001L, "chunkId", 9001L,
                        "chunkIndex", 0, "fileName", "产品手册.md", "score", 0.91D)).build()),
                EvidenceLevel.SUFFICIENT, "仅基于证据回答。");
    }

    private ChatResponse response(String content) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(content))));
    }

    private PromptTemplate template(String value) {
        return PromptTemplate.builder().template(value).build();
    }

    private record HarnessRuntime(
            QaService qaService,
            GroupMembershipService groupMembershipService,
            EvidenceRetriever evidenceRetriever,
            AiChatModelProvider chatModelProvider,
            ChatModel chatModel
    ) {
    }
}
