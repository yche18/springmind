package com.yche.springmind.qa;

import com.yche.springmind.groupmembership.service.GroupMembershipService;
import com.yche.springmind.identity.service.CurrentUserService;
import com.yche.springmind.qa.model.dto.AskQuestionRequest;
import com.yche.springmind.qa.model.vo.AskQuestionResponse;
import com.yche.springmind.qa.service.QaChatService;
import com.yche.springmind.qa.service.QaService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QaServiceTest {

    @Test
    void shouldRequireReadablePermissionBeforeAsking() {
        GroupMembershipService groupMembershipService = mock(GroupMembershipService.class);
        QaChatService qaChatService = mock(QaChatService.class);
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        QaService qaService = new QaService(groupMembershipService, qaChatService, currentUserService);
        HttpServletRequest request = mock(HttpServletRequest.class);
        AskQuestionRequest askQuestionRequest = new AskQuestionRequest();
        askQuestionRequest.setGroupId(2001L);
        askQuestionRequest.setQuestion("产品团队如何安排迭代？");
        AskQuestionResponse expected = new AskQuestionResponse(
                true,
                "根据当前资料，产品团队每两周发布一次。",
                null,
                null,
                java.util.List.of()
        );
        when(currentUserService.requireBusinessUser(request)).thenReturn(new CurrentUserService.CurrentUser(7L, "u7", "用户"));
        when(qaChatService.ask(7L, 2001L, "产品团队如何安排迭代？")).thenReturn(expected);

        AskQuestionResponse actual = qaService.ask(request, askQuestionRequest);

        assertThat(actual).isEqualTo(expected);
        verify(groupMembershipService).requireGroupReadable(request, 2001L);
        verify(currentUserService).requireBusinessUser(request);
        verify(qaChatService).ask(7L, 2001L, "产品团队如何安排迭代？");
    }
}
