package com.yche.springmind.groupmembership.controller;

import com.yche.springmind.common.api.ApiResponse;
import com.yche.springmind.groupmembership.model.dto.CreateGroupRequest;
import com.yche.springmind.groupmembership.model.dto.CreateInvitationRequest;
import com.yche.springmind.groupmembership.model.vo.GroupMemberVO;
import com.yche.springmind.groupmembership.service.GroupManagementService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 提供创建知识组、邀请成员、管理成员及修改成员角色等管理接口。
 *
 * <p>位于接口层：负责接收 HTTP 请求、触发参数校验并调用业务服务；业务规则由 Service 层统一维护。</p>
 */
@RestController
@RequestMapping("/api/groups")
public class GroupManagementController {

    private final GroupManagementService groupManagementService;

    /**
     * 创建并初始化 {@link GroupManagementController}，保存该组件运行所需的依赖与配置。
     *
     * @param groupManagementService 方法参数 {@code groupManagementService}
     */
    public GroupManagementController(GroupManagementService groupManagementService) {
        this.groupManagementService = groupManagementService;
    }

    /**
     * 处理 {@code createGroup} 对应的 HTTP 请求，并将业务结果封装为统一响应。
     *
     * @param createGroupRequest 创建群组请求参数
     * @param request 已经通过控制器基础校验的请求对象
     * @return 方法执行结果，具体结构由返回类型 {@code ApiResponse&lt;Long&gt;} 表示
     */
    @PostMapping
    public ApiResponse<Long> createGroup(
            @Valid @RequestBody CreateGroupRequest createGroupRequest,
            HttpServletRequest request
    ) {
        return ApiResponse.success(groupManagementService.createGroup(request, createGroupRequest));
    }

    /**
     * 处理 {@code createInvitation} 对应的 HTTP 请求，并将业务结果封装为统一响应。
     *
     * @param groupId 群组唯一标识
     * @param createInvitationRequest 创建邀请请求参数
     * @param request 已经通过控制器基础校验的请求对象
     * @return 方法执行结果，具体结构由返回类型 {@code ApiResponse&lt;Long&gt;} 表示
     */
    @PostMapping("/{groupId}/invitations")
    public ApiResponse<Long> createInvitation(
            @PathVariable Long groupId,
            @Valid @RequestBody CreateInvitationRequest createInvitationRequest,
            HttpServletRequest request
    ) {
        return ApiResponse.success(groupManagementService.createInvitation(request, groupId, createInvitationRequest));
    }

    /**
     * 处理 {@code listMembers} 对应的 HTTP 请求，并将业务结果封装为统一响应。
     *
     * @param groupId 群组唯一标识
     * @param request 已经通过控制器基础校验的请求对象
     * @return 符合条件的结果集合；无结果时返回空集合
     */
    @GetMapping("/{groupId}/members")
    public List<GroupMemberVO> listMembers(
            @PathVariable Long groupId,
            HttpServletRequest request
    ) {
        return groupManagementService.listMembers(request, groupId);
    }

    /**
     * 处理 {@code removeMember} 对应的 HTTP 请求，并将业务结果封装为统一响应。
     *
     * @param groupId 群组唯一标识
     * @param userId 用户唯一标识
     * @param request 已经通过控制器基础校验的请求对象
     * @return 方法执行结果，具体结构由返回类型 {@code ApiResponse&lt;Void&gt;} 表示
     */
    @DeleteMapping("/{groupId}/members/{userId}")
    public ApiResponse<Void> removeMember(
            @PathVariable Long groupId,
            @PathVariable Long userId,
            HttpServletRequest request
    ) {
        groupManagementService.removeMember(request, groupId, userId);
        return ApiResponse.success(null);
    }

    /**
     * 处理 {@code leaveGroup} 对应的 HTTP 请求，并将业务结果封装为统一响应。
     *
     * @param groupId 群组唯一标识
     * @param request 已经通过控制器基础校验的请求对象
     * @return 方法执行结果，具体结构由返回类型 {@code ApiResponse&lt;Void&gt;} 表示
     */
    @PostMapping("/{groupId}/leave")
    public ApiResponse<Void> leaveGroup(
            @PathVariable Long groupId,
            HttpServletRequest request
    ) {
        groupManagementService.leaveGroup(request, groupId);
        return ApiResponse.success(null);
    }
}
