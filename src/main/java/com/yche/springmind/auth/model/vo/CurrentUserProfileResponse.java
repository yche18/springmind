package com.yche.springmind.auth.model.vo;

import com.yche.springmind.common.enums.SystemRole;
import com.yche.springmind.identity.service.CurrentUserService;

/**
 * 向前端返回当前登录用户的基本资料、系统角色与密码状态。
 *
 * <p>这是接口输出视图，用于向前端稳定地暴露所需字段，避免直接返回数据库实体。</p>
 */
public record CurrentUserProfileResponse(
        Long userId,
        String userCode,
        String displayName,
        SystemRole systemRole,
        boolean mustChangePassword
) {

    /**
     * 完成 {@code from} 对应的处理。
     *
     * @param currentUser 当前已经通过认证的用户信息
     * @return 方法执行结果，具体结构由返回类型 {@code CurrentUserProfileResponse} 表示
     */
    public static CurrentUserProfileResponse from(CurrentUserService.CurrentUser currentUser) {
        return new CurrentUserProfileResponse(
                currentUser.userId(),
                currentUser.userCode(),
                currentUser.displayName(),
                currentUser.systemRole(),
                currentUser.mustChangePassword()
        );
    }
}
