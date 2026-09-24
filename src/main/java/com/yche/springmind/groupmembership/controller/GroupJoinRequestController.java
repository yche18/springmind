package com.yche.springmind.groupmembership.controller;

import com.yche.springmind.common.api.ApiResponse;
import com.yche.springmind.groupmembership.model.dto.CreateJoinRequestRequest;
import com.yche.springmind.groupmembership.model.vo.MyJoinRequestVO;
import com.yche.springmind.groupmembership.model.vo.OwnerJoinRequestVO;
import com.yche.springmind.groupmembership.service.GroupJoinRequestService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 提供申请加入知识组、查询申请和组主审批等接口。
 *
 * <p>位于接口层：负责接收 HTTP 请求、触发参数校验并调用业务服务；业务规则由 Service 层统一维护。</p>
 */
@RestController
@RequestMapping("/api/groups")
public class GroupJoinRequestController {

    private final GroupJoinRequestService groupJoinRequestService;

    /**
     * 创建并初始化 {@link GroupJoinRequestController}，保存该组件运行所需的依赖与配置。
     *
     * @param groupJoinRequestService 方法参数 {@code groupJoinRequestService}
     */
    public GroupJoinRequestController(GroupJoinRequestService groupJoinRequestService) {
        this.groupJoinRequestService = groupJoinRequestService;
    }

    /**
     * 处理 {@code submitJoinRequest} 对应的 HTTP 请求，并将业务结果封装为统一响应。
     *
     * @param joinRequestRequest 加入申请请求参数
     * @param request 已经通过控制器基础校验的请求对象
     * @return 方法执行结果，具体结构由返回类型 {@code ApiResponse&lt;Long&gt;} 表示
     */
    @PostMapping("/join-requests")
    public ApiResponse<Long> submitJoinRequest(
            @Valid @RequestBody CreateJoinRequestRequest joinRequestRequest,
            HttpServletRequest request
    ) {
        return ApiResponse.success(groupJoinRequestService.submitJoinRequest(request, joinRequestRequest));
    }

    /**
     * 处理 {@code listMyJoinRequests} 对应的 HTTP 请求，并将业务结果封装为统一响应。
     *
     * @param request 已经通过控制器基础校验的请求对象
     * @return 查询得到的My加入申请列表结果
     */
    @GetMapping("/join-requests/my")
    public ApiResponse<List<MyJoinRequestVO>> listMyJoinRequests(HttpServletRequest request) {
        return ApiResponse.success(groupJoinRequestService.listMyJoinRequests(request));
    }

    /**
     * 处理 {@code listOwnerJoinRequests} 对应的 HTTP 请求，并将业务结果封装为统一响应。
     *
     * @param groupId 群组唯一标识
     * @param request 已经通过控制器基础校验的请求对象
     * @return 查询得到的Owner加入申请列表结果
     */
    @GetMapping("/{groupId}/join-requests")
    public ApiResponse<List<OwnerJoinRequestVO>> listOwnerJoinRequests(
            @PathVariable Long groupId,
            HttpServletRequest request
    ) {
        return ApiResponse.success(groupJoinRequestService.listOwnerJoinRequests(request, groupId));
    }

    /**
     * 处理 {@code approveJoinRequest} 对应的 HTTP 请求，并将业务结果封装为统一响应。
     *
     * @param groupId 群组唯一标识
     * @param requestId 申请唯一标识
     * @param request 已经通过控制器基础校验的请求对象
     * @return 方法执行结果，具体结构由返回类型 {@code ApiResponse&lt;Void&gt;} 表示
     */
    @PostMapping("/{groupId}/join-requests/{requestId}/approve")
    public ApiResponse<Void> approveJoinRequest(
            @PathVariable Long groupId,
            @PathVariable Long requestId,
            HttpServletRequest request
    ) {
        groupJoinRequestService.approveJoinRequest(request, groupId, requestId);
        return ApiResponse.success(null);
    }

    /**
     * 处理 {@code rejectJoinRequest} 对应的 HTTP 请求，并将业务结果封装为统一响应。
     *
     * @param groupId 群组唯一标识
     * @param requestId 申请唯一标识
     * @param request 已经通过控制器基础校验的请求对象
     * @return 方法执行结果，具体结构由返回类型 {@code ApiResponse&lt;Void&gt;} 表示
     */
    @PostMapping("/{groupId}/join-requests/{requestId}/reject")
    public ApiResponse<Void> rejectJoinRequest(
            @PathVariable Long groupId,
            @PathVariable Long requestId,
            HttpServletRequest request
    ) {
        groupJoinRequestService.rejectJoinRequest(request, groupId, requestId);
        return ApiResponse.success(null);
    }
}
