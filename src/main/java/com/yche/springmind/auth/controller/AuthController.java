package com.yche.springmind.auth.controller;

import com.yche.springmind.auth.config.AuthProperties;
import com.yche.springmind.auth.model.dto.LoginRequest;
import com.yche.springmind.auth.model.dto.RegisterRequest;
import com.yche.springmind.auth.model.vo.AuthTokensResponse;
import com.yche.springmind.auth.model.vo.CurrentUserProfileResponse;
import com.yche.springmind.auth.security.AuthCookieSupport;
import com.yche.springmind.auth.service.AuthService;
import com.yche.springmind.auth.service.AuthService.AuthTokens;
import com.yche.springmind.common.api.ApiResponse;
import com.yche.springmind.identity.service.CurrentUserService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供注册、登录、刷新令牌、退出登录和当前用户信息等认证接口。
 *
 * <p>位于接口层：负责接收 HTTP 请求、触发参数校验并调用业务服务；业务规则由 Service 层统一维护。</p>
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthCookieSupport authCookieSupport;
    private final CurrentUserService currentUserService;
    private final AuthProperties authProperties;

    /**
     * 创建并初始化 {@link AuthController}，保存该组件运行所需的依赖与配置。
     * <p>
     * 实现要点：解析并确认当前登录用户。
     *
     * @param authService 方法参数 {@code authService}
     * @param authCookieSupport 方法参数 {@code authCookieSupport}
     * @param currentUserService 方法参数 {@code currentUserService}
     * @param authProperties 方法参数 {@code authProperties}
     */
    public AuthController(
            AuthService authService,
            AuthCookieSupport authCookieSupport,
            CurrentUserService currentUserService,
            AuthProperties authProperties
    ) {
        this.authService = authService;
        this.authCookieSupport = authCookieSupport;
        this.currentUserService = currentUserService;
        this.authProperties = authProperties;
    }

    /**
     * 校验用户名或邮箱与密码，签发访问令牌和刷新令牌，并记录本次成功登录。
     *
     * @param request 已经通过控制器基础校验的请求对象
     * @param response 方法参数 {@code response}
     * @return 方法执行结果，具体结构由返回类型 {@code ApiResponse&lt;AuthTokensResponse&gt;} 表示
     */
    @PostMapping("/login")
    public ApiResponse<AuthTokensResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response
    ) {
        AuthTokens tokens = authService.login(request.loginId(), request.password());
        authCookieSupport.writeRefreshTokenCookie(response, tokens.refreshToken());
        return ApiResponse.success(AuthTokensResponse.from(tokens, authService.getCurrentUser(tokens.userId())));
    }

    /**
     * 完成用户自助注册；密码由用户本人设置，因此新账号无需执行首次改密。
     *
     * @param request 已经通过控制器基础校验的请求对象
     * @return 方法执行结果，具体结构由返回类型 {@code ApiResponse&lt;Void&gt;} 表示
     */
    @PostMapping("/register")
    public ApiResponse<Void> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return ApiResponse.success(null);
    }

    /**
     * 校验并轮换刷新令牌，为仍可登录的用户签发新的访问凭据。
     *
     * @param request 已经通过控制器基础校验的请求对象
     * @param response 方法参数 {@code response}
     * @return 方法执行结果，具体结构由返回类型 {@code ApiResponse&lt;AuthTokensResponse&gt;} 表示
     */
    @PostMapping("/refresh")
    public ApiResponse<AuthTokensResponse> refresh(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        String refreshToken = extractRefreshToken(request);
        AuthTokens tokens = authService.refresh(refreshToken);
        authCookieSupport.writeRefreshTokenCookie(response, tokens.refreshToken());
        return ApiResponse.success(AuthTokensResponse.from(tokens, authService.getCurrentUser(tokens.userId())));
    }

    /**
     * 撤销当前刷新令牌，使对应会话无法继续换取访问令牌。
     *
     * @param request 已经通过控制器基础校验的请求对象
     * @param response 方法参数 {@code response}
     * @return 方法执行结果，具体结构由返回类型 {@code ApiResponse&lt;Void&gt;} 表示
     */
    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        authService.logout(extractRefreshToken(request));
        authCookieSupport.clearRefreshTokenCookie(response);
        return ApiResponse.success(null);
    }

    /**
     * 处理 {@code currentUser} 对应的 HTTP 请求，并将业务结果封装为统一响应。
     * <p>
     * 实现要点：解析并确认当前登录用户。
     *
     * @param request 已经通过控制器基础校验的请求对象
     * @return 方法执行结果，具体结构由返回类型 {@code ApiResponse&lt;CurrentUserProfileResponse&gt;} 表示
     */
    @GetMapping("/me")
    public ApiResponse<CurrentUserProfileResponse> currentUser(HttpServletRequest request) {
        return ApiResponse.success(CurrentUserProfileResponse.from(currentUserService.getRequiredCurrentUser(request)));
    }

    /**
     * 处理 {@code extractRefreshToken} 对应的 HTTP 请求，并将业务结果封装为统一响应。
     *
     * @param request 已经通过控制器基础校验的请求对象
     * @return 处理后得到的字符串结果
     */
    private String extractRefreshToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null || cookies.length == 0) {
            return null;
        }
        String refreshCookieName = authProperties.getRefreshCookieName();
        for (Cookie cookie : cookies) {
            if (refreshCookieName.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
