package com.yche.springmind.qa.service;

import com.yche.springmind.groupmembership.service.GroupMembershipService;
import com.yche.springmind.identity.service.CurrentUserService;
import com.yche.springmind.qa.model.dto.AskQuestionRequest;
import com.yche.springmind.qa.model.vo.AskQuestionResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

/**
 * 作为问答模块的简洁入口，将接口请求委托给完整聊天问答流程。
 *
 * <p>位于业务服务层：编排领域操作和基础设施调用，并集中维护事务、权限校验及失败处理边界。</p>
 */
@Service
public class QaService {

    private final GroupMembershipService groupMembershipService;
    private final QaChatService qaChatService;
    private final CurrentUserService currentUserService;

    /**
     * 创建并初始化 {@link QaService}，保存该组件运行所需的依赖与配置。
     * <p>
     * 实现要点：校验群组成员关系和角色权限；解析并确认当前登录用户。
     *
     * @param groupMembershipService 方法参数 {@code groupMembershipService}
     * @param qaChatService 方法参数 {@code qaChatService}
     * @param currentUserService 方法参数 {@code currentUserService}
     */
    public QaService(
            GroupMembershipService groupMembershipService,
            QaChatService qaChatService,
            CurrentUserService currentUserService
    ) {
        this.groupMembershipService = groupMembershipService;
        this.qaChatService = qaChatService;
        this.currentUserService = currentUserService;
    }

    /**
     * 围绕用户问题执行检索增强问答，并返回答案、证据等级和引用来源。
     * <p>
     * 实现要点：校验群组成员关系和角色权限；先校验输入、状态或业务边界；解析并确认当前登录用户。
     *
     * @param request 已经通过控制器基础校验的请求对象
     * @param askQuestionRequest ask问题请求参数
     * @return 方法执行结果，具体结构由返回类型 {@code AskQuestionResponse} 表示
     */
    public AskQuestionResponse ask(HttpServletRequest request, AskQuestionRequest askQuestionRequest) {
        Long groupId = askQuestionRequest.getGroupId();
        groupMembershipService.requireGroupReadable(request, groupId);
        CurrentUserService.CurrentUser currentUser = currentUserService.requireBusinessUser(request);
        return qaChatService.ask(currentUser.userId(), groupId, askQuestionRequest.getQuestion());
    }
}
