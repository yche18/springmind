package com.yche.springmind.user.model.vo;

import com.yche.springmind.common.enums.SystemRole;
import com.yche.springmind.common.enums.UserStatus;

import java.time.LocalDateTime;

/**
 * 表示管理端用户列表中的账号、角色、状态和密码信息摘要。
 *
 * <p>这是接口输出视图，用于向前端稳定地暴露所需字段，避免直接返回数据库实体。</p>
 */
public record AdminUserItemResponse(
        Long userId,
        String userCode,
        String username,
        String email,
        String displayName,
        SystemRole systemRole,
        UserStatus status,
        boolean mustChangePassword,
        LocalDateTime lastLoginAt
) {
}
