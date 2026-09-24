package com.yche.springmind.user.controller;

import com.yche.springmind.common.api.ApiResponse;
import com.yche.springmind.identity.service.CurrentUserService;
import com.yche.springmind.user.model.dto.ChangePasswordRequest;
import com.yche.springmind.user.service.AccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供当前用户修改密码等账号自助管理接口。
 *
 * <p>位于接口层：负责接收 HTTP 请求、触发参数校验并调用业务服务；业务规则由 Service 层统一维护。</p>
 */
@RestController
@RequestMapping("/api/account")
public class AccountController {

    private final AccountService accountService;
    private final CurrentUserService currentUserService;

    /**
     * 创建并初始化 {@link AccountController}，保存该组件运行所需的依赖与配置。
     * <p>
     * 实现要点：解析并确认当前登录用户。
     *
     * @param accountService 方法参数 {@code accountService}
     * @param currentUserService 方法参数 {@code currentUserService}
     */
    public AccountController(
            AccountService accountService,
            CurrentUserService currentUserService
    ) {
        this.accountService = accountService;
        this.currentUserService = currentUserService;
    }

    /**
     * 验证旧密码并保存新密码，同时清除首次登录强制改密标记。
     * <p>
     * 实现要点：解析并确认当前登录用户。
     *
     * @param request 已经通过控制器基础校验的请求对象
     * @param httpServletRequest httpServlet请求参数
     * @return 方法执行结果，具体结构由返回类型 {@code ApiResponse&lt;Void&gt;} 表示
     */
    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            HttpServletRequest httpServletRequest
    ) {
        accountService.changePassword(currentUserService.getRequiredCurrentUser(httpServletRequest), request);
        return ApiResponse.success(null);
    }
}
