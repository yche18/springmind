package com.yche.springmind.groupmembership.controller;

import com.yche.springmind.common.api.ApiResponse;
import com.yche.springmind.groupmembership.service.GroupManagementService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供受邀用户接受或拒绝知识组邀请的接口。
 *
 * <p>位于接口层：负责接收 HTTP 请求、触发参数校验并调用业务服务；业务规则由 Service 层统一维护。</p>
 */
@RestController
@RequestMapping("/api/invitations")
public class InvitationDecisionController {

    private final GroupManagementService groupManagementService;

    /**
     * 创建并初始化 {@link InvitationDecisionController}，保存该组件运行所需的依赖与配置。
     *
     * @param groupManagementService 方法参数 {@code groupManagementService}
     */
    public InvitationDecisionController(GroupManagementService groupManagementService) {
        this.groupManagementService = groupManagementService;
    }

    /**
     * 处理 {@code acceptInvitation} 对应的 HTTP 请求，并将业务结果封装为统一响应。
     *
     * @param invitationId 邀请唯一标识
     * @param request 已经通过控制器基础校验的请求对象
     * @return 方法执行结果，具体结构由返回类型 {@code ApiResponse&lt;Void&gt;} 表示
     */
    @PostMapping("/{invitationId}/accept")
    public ApiResponse<Void> acceptInvitation(
            @PathVariable Long invitationId,
            HttpServletRequest request
    ) {
        groupManagementService.acceptInvitation(request, invitationId);
        return ApiResponse.success(null);
    }

    /**
     * 处理 {@code rejectInvitation} 对应的 HTTP 请求，并将业务结果封装为统一响应。
     *
     * @param invitationId 邀请唯一标识
     * @param request 已经通过控制器基础校验的请求对象
     * @return 方法执行结果，具体结构由返回类型 {@code ApiResponse&lt;Void&gt;} 表示
     */
    @PostMapping("/{invitationId}/reject")
    public ApiResponse<Void> rejectInvitation(
            @PathVariable Long invitationId,
            HttpServletRequest request
    ) {
        groupManagementService.rejectInvitation(request, invitationId);
        return ApiResponse.success(null);
    }

    /**
     * 处理 {@code cancelInvitation} 对应的 HTTP 请求，并将业务结果封装为统一响应。
     *
     * @param invitationId 邀请唯一标识
     * @param request 已经通过控制器基础校验的请求对象
     * @return 方法执行结果，具体结构由返回类型 {@code ApiResponse&lt;Void&gt;} 表示
     */
    @PostMapping("/{invitationId}/cancel")
    public ApiResponse<Void> cancelInvitation(
            @PathVariable Long invitationId,
            HttpServletRequest request
    ) {
        groupManagementService.cancelInvitation(request, invitationId);
        return ApiResponse.success(null);
    }
}
