package com.yche.springmind.qa.service;

import com.yche.springmind.groupmembership.service.GroupMembershipService;
import com.yche.springmind.identity.service.CurrentUserService;
import com.yche.springmind.qa.model.dto.AskQuestionRequest;
import com.yche.springmind.qa.model.vo.AskQuestionResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

@Service
public class QaService {

    private final GroupMembershipService groupMembershipService;
    private final QaChatService qaChatService;
    private final CurrentUserService currentUserService;

    public QaService(
            GroupMembershipService groupMembershipService,
            QaChatService qaChatService,
            CurrentUserService currentUserService
    ) {
        this.groupMembershipService = groupMembershipService;
        this.qaChatService = qaChatService;
        this.currentUserService = currentUserService;
    }

    public AskQuestionResponse ask(HttpServletRequest request, AskQuestionRequest askQuestionRequest) {
        Long groupId = askQuestionRequest.getGroupId();
        groupMembershipService.requireGroupReadable(request, groupId);
        CurrentUserService.CurrentUser currentUser = currentUserService.requireBusinessUser(request);
        return qaChatService.ask(currentUser.userId(), groupId, askQuestionRequest.getQuestion());
    }
}
