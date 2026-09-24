package com.yche.springmind.auth.model.vo;

import com.yche.springmind.auth.service.AuthService.AuthTokens;
import com.yche.springmind.identity.service.CurrentUserService;

/**
 * 向客户端返回访问令牌、过期时间以及用户是否必须修改密码。
 *
 * <p>这是接口输出视图，用于向前端稳定地暴露所需字段，避免直接返回数据库实体。</p>
 */
public record AuthTokensResponse(
        String accessToken,
        CurrentUserProfileResponse currentUser
) {

    /**
     * 完成 {@code from} 对应的处理。
     *
     * @param tokens 方法参数 {@code tokens}
     * @param currentUser 当前已经通过认证的用户信息
     * @return 方法执行结果，具体结构由返回类型 {@code AuthTokensResponse} 表示
     */
    public static AuthTokensResponse from(AuthTokens tokens, CurrentUserService.CurrentUser currentUser) {
        return new AuthTokensResponse(tokens.accessToken(), CurrentUserProfileResponse.from(currentUser));
    }
}
