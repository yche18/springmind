package com.yche.springmind.auth.security;

import com.yche.springmind.common.api.ApiResponse;
import com.yche.springmind.common.enums.SystemRole;
import com.yche.springmind.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 从请求头提取 Bearer Token，校验后把认证用户写入 Spring Security 上下文。
 *
 * <p>位于认证安全边界：负责令牌、Cookie 或安全上下文处理，为业务服务提供可信的用户身份。</p>
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String AUTHENTICATED_USER_REQUEST_ATTRIBUTE =
            JwtAuthenticationFilter.class.getName() + ".AUTHENTICATED_USER";
    public static final String INVALID_ACCESS_TOKEN_MESSAGE = "登录凭证无效或已过期，请重新登录";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String LOGIN_PATH = "/api/auth/login";
    private static final String REGISTER_PATH = "/api/auth/register";
    private static final String REFRESH_PATH = "/api/auth/refresh";
    private static final String LOGOUT_PATH = "/api/auth/logout";

    private final JwtAccessTokenService jwtAccessTokenService;
    private final ObjectMapper objectMapper;

    /**
     * 创建并初始化 {@link JwtAuthenticationFilter}，保存该组件运行所需的依赖与配置。
     * <p>
     * 实现要点：签发或解析 JWT 访问令牌。
     *
     * @param jwtAccessTokenService 方法参数 {@code jwtAccessTokenService}
     * @param objectMapper 方法参数 {@code objectMapper}
     */
    public JwtAuthenticationFilter(
            JwtAccessTokenService jwtAccessTokenService,
            ObjectMapper objectMapper
    ) {
        this.jwtAccessTokenService = jwtAccessTokenService;
        this.objectMapper = objectMapper;
    }

    /**
     * 完成 {@code doFilterInternal} 对应的处理。
     * <p>
     * 实现要点：签发或解析 JWT 访问令牌；按文档类型解析正文内容；捕获依赖异常并转换、记录或执行降级策略。
     *
     * @param request 已经通过控制器基础校验的请求对象
     * @param response 方法参数 {@code response}
     * @param filterChain 方法参数 {@code filterChain}
     * @throws ServletException 当输入、状态或依赖不满足方法约束时抛出
     * @throws IOException 当输入、状态或依赖不满足方法约束时抛出
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }
        String accessToken = authorization.substring(BEARER_PREFIX.length()).trim();
        if (accessToken.isEmpty()) {
            writeUnauthorized(response, INVALID_ACCESS_TOKEN_MESSAGE);
            return;
        }
        try {
            JwtAccessTokenService.AccessTokenClaims claims = jwtAccessTokenService.parse(accessToken);
            request.setAttribute(
                    AUTHENTICATED_USER_REQUEST_ATTRIBUTE,
                    new AuthenticatedUser(
                            claims.userId(),
                            claims.userCode(),
                            claims.displayName(),
                            claims.systemRole(),
                            claims.mustChangePassword()
                    )
            );
            filterChain.doFilter(request, response);
        } catch (BusinessException exception) {
            writeUnauthorized(response, exception.getMessage());
        }
    }

    /**
     * 完成 {@code shouldNotFilter} 对应的处理。
     *
     * @param request 已经通过控制器基础校验的请求对象
     * @return 满足条件时返回 {@code true}，否则返回 {@code false}
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        return LOGIN_PATH.equals(requestUri)
                || REGISTER_PATH.equals(requestUri)
                || REFRESH_PATH.equals(requestUri)
                || LOGOUT_PATH.equals(requestUri);
    }

    /**
     * 完成 {@code writeUnauthorized} 对应的处理。
     *
     * @param response 方法参数 {@code response}
     * @param message 方法参数 {@code message}
     * @throws IOException 当输入、状态或依赖不满足方法约束时抛出
     */
    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8).toString());
        objectMapper.writeValue(response.getWriter(), new ApiResponse<>(false, null, message));
    }

    /**
     * 表示访问令牌验证后写入安全上下文的用户身份。
     *
     * <p>仅在 {@code JwtAuthenticationFilter} 的实现过程中使用，用不可变数据结构收拢中间结果，避免参数和值的含义混淆。</p>
     */
    public record AuthenticatedUser(
            Long userId,
            String userCode,
            String displayName,
            SystemRole systemRole,
            boolean mustChangePassword
    ) {
    }
}
