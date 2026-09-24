package com.yche.springmind.user.controller;

import com.yche.springmind.common.api.ApiResponse;
import com.yche.springmind.identity.service.CurrentUserService;
import com.yche.springmind.user.model.dto.CreateUserRequest;
import com.yche.springmind.user.model.dto.ResetUserPasswordRequest;
import com.yche.springmind.user.model.dto.UpdateUserStatusRequest;
import com.yche.springmind.user.model.vo.AdminUserItemResponse;
import com.yche.springmind.user.service.AdminUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 提供管理员创建用户、重置密码和启停账号等管理接口。
 *
 * <p>位于接口层：负责接收 HTTP 请求、触发参数校验并调用业务服务；业务规则由 Service 层统一维护。</p>
 */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final CurrentUserService currentUserService;
    private final AdminUserService adminUserService;

    /**
     * 创建并初始化 {@link AdminUserController}，保存该组件运行所需的依赖与配置。
     * <p>
     * 实现要点：解析并确认当前登录用户。
     *
     * @param currentUserService 方法参数 {@code currentUserService}
     * @param adminUserService 方法参数 {@code adminUserService}
     */
    public AdminUserController(
            CurrentUserService currentUserService,
            AdminUserService adminUserService
    ) {
        this.currentUserService = currentUserService;
        this.adminUserService = adminUserService;
    }

    /**
     * 处理 {@code listUsers} 对应的 HTTP 请求，并将业务结果封装为统一响应。
     * <p>
     * 实现要点：解析并确认当前登录用户；先校验输入、状态或业务边界。
     *
     * @param request 已经通过控制器基础校验的请求对象
     * @return 查询得到的用户列表结果
     */
    @GetMapping
    public ApiResponse<List<AdminUserItemResponse>> listUsers(HttpServletRequest request) {
        currentUserService.requireSystemAdmin(request);
        return ApiResponse.success(adminUserService.listUsers());
    }

    /**
     * 返回 {@code user} 对应的配置或状态值。
     *
     * @param userId 用户唯一标识
     * @param request 已经通过控制器基础校验的请求对象
     * @return 查询得到的用户结果
     */
    @GetMapping("/{userId}")
    public ApiResponse<AdminUserItemResponse> getUser(
            @PathVariable Long userId,
            HttpServletRequest request
    ) {
        currentUserService.requireSystemAdmin(request);
        return ApiResponse.success(adminUserService.getUser(userId));
    }

    /**
     * 由管理员创建可登录用户，并按请求决定是否要求首次登录修改密码。
     * <p>
     * 实现要点：解析并确认当前登录用户；先校验输入、状态或业务边界。
     *
     * @param request 已经通过控制器基础校验的请求对象
     * @param httpServletRequest httpServlet请求参数
     * @return 方法执行结果，具体结构由返回类型 {@code ApiResponse&lt;AdminUserItemResponse&gt;} 表示
     */
    @PostMapping
    public ApiResponse<AdminUserItemResponse> createUser(
            @Valid @RequestBody CreateUserRequest request,
            HttpServletRequest httpServletRequest
    ) {
        currentUserService.requireSystemAdmin(httpServletRequest);
        return ApiResponse.success(adminUserService.createUser(request));
    }

    /**
     * 处理 {@code updateUserStatus} 对应的 HTTP 请求，并将业务结果封装为统一响应。
     * <p>
     * 实现要点：解析并确认当前登录用户；先校验输入、状态或业务边界。
     *
     * @param userId 用户唯一标识
     * @param request 已经通过控制器基础校验的请求对象
     * @param httpServletRequest httpServlet请求参数
     * @return 方法执行结果，具体结构由返回类型 {@code ApiResponse&lt;Void&gt;} 表示
     */
    @PatchMapping("/{userId}/status")
    public ApiResponse<Void> updateUserStatus(
            @PathVariable Long userId,
            @Valid @RequestBody UpdateUserStatusRequest request,
            HttpServletRequest httpServletRequest
    ) {
        currentUserService.requireSystemAdmin(httpServletRequest);
        adminUserService.updateUserStatus(userId, request);
        return ApiResponse.success(null);
    }

    /**
     * 由管理员重置用户密码，并要求该用户下次登录后立即修改初始密码。
     * <p>
     * 实现要点：解析并确认当前登录用户；先校验输入、状态或业务边界。
     *
     * @param userId 用户唯一标识
     * @param request 已经通过控制器基础校验的请求对象
     * @param httpServletRequest httpServlet请求参数
     * @return 方法执行结果，具体结构由返回类型 {@code ApiResponse&lt;Void&gt;} 表示
     */
    @PostMapping("/{userId}/reset-password")
    public ApiResponse<Void> resetPassword(
            @PathVariable Long userId,
            @Valid @RequestBody ResetUserPasswordRequest request,
            HttpServletRequest httpServletRequest
    ) {
        currentUserService.requireSystemAdmin(httpServletRequest);
        adminUserService.resetPassword(userId, request);
        return ApiResponse.success(null);
    }
}
