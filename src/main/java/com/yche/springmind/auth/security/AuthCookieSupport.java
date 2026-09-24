package com.yche.springmind.auth.security;

import com.yche.springmind.auth.config.AuthProperties;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 统一创建、写入和清除刷新令牌 Cookie，保证各认证接口使用相同安全属性。
 *
 * <p>位于认证安全边界：负责令牌、Cookie 或安全上下文处理，为业务服务提供可信的用户身份。</p>
 */
@Component
public class AuthCookieSupport {

    private static final String COOKIE_PATH = "/";
    private static final String SAME_SITE_POLICY = "Lax";

    private final AuthProperties authProperties;

    /**
     * 创建并初始化 {@link AuthCookieSupport}，保存该组件运行所需的依赖与配置。
     *
     * @param authProperties 方法参数 {@code authProperties}
     */
    public AuthCookieSupport(AuthProperties authProperties) {
        this.authProperties = authProperties;
    }

    /**
     * 完成 {@code writeRefreshTokenCookie} 对应的处理。
     *
     * @param response 方法参数 {@code response}
     * @param refreshToken 客户端提交的刷新令牌
     */
    public void writeRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        response.addHeader(
                HttpHeaders.SET_COOKIE,
                buildCookie(refreshToken, Duration.ofDays(authProperties.getRefreshTokenExpireDays())).toString()
        );
    }

    /**
     * 完成 {@code clearRefreshTokenCookie} 对应的处理。
     *
     * @param response 方法参数 {@code response}
     */
    public void clearRefreshTokenCookie(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie("", Duration.ZERO).toString());
    }

    /**
     * 完成 {@code buildCookie} 对应的处理。
     *
     * @param value 方法参数 {@code value}
     * @param maxAge 方法参数 {@code maxAge}
     * @return 方法执行结果，具体结构由返回类型 {@code ResponseCookie} 表示
     */
    private ResponseCookie buildCookie(String value, Duration maxAge) {
        return ResponseCookie.from(authProperties.getRefreshCookieName(), value)
                .httpOnly(true)
                .secure(authProperties.isRefreshCookieSecure())
                .path(COOKIE_PATH)
                .sameSite(SAME_SITE_POLICY)
                .maxAge(maxAge)
                .build();
    }
}
