package com.yche.springmind.groupmembership.controller;

import com.yche.springmind.groupmembership.service.GroupMembershipService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供当前用户可见知识组、成员与待处理邀请的查询接口。
 *
 * <p>位于接口层：负责接收 HTTP 请求、触发参数校验并调用业务服务；业务规则由 Service 层统一维护。</p>
 */
@RestController
@RequestMapping("/api/groups")
public class GroupQueryController {

    private final GroupMembershipService groupMembershipService;

    /**
     * 创建并初始化 {@link GroupQueryController}，保存该组件运行所需的依赖与配置。
     * <p>
     * 实现要点：校验群组成员关系和角色权限。
     *
     * @param groupMembershipService 方法参数 {@code groupMembershipService}
     */
    public GroupQueryController(GroupMembershipService groupMembershipService) {
        this.groupMembershipService = groupMembershipService;
    }

    /**
     * 处理 {@code listVisibleGroups} 对应的 HTTP 请求，并将业务结果封装为统一响应。
     * <p>
     * 实现要点：校验群组成员关系和角色权限。
     *
     * @param request 已经通过控制器基础校验的请求对象
     * @return 查询得到的Visible群组列表结果
     */
    @GetMapping("/my")
    public GroupMembershipService.GroupQueryResult listVisibleGroups(HttpServletRequest request) {
        return groupMembershipService.listVisibleGroups(request);
    }
}
