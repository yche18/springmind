package com.yche.springmind.user.model.vo;

import com.yche.springmind.common.enums.SystemRole;
import com.yche.springmind.common.enums.UserStatus;

import java.time.LocalDateTime;

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
