package com.yche.springmind.auth.model.vo;

import com.yche.springmind.common.enums.SystemRole;
import com.yche.springmind.identity.service.CurrentUserService;

public record CurrentUserProfileResponse(
        Long userId,
        String userCode,
        String displayName,
        SystemRole systemRole,
        boolean mustChangePassword
) {

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
