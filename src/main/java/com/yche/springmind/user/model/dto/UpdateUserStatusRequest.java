package com.yche.springmind.user.model.dto;

import com.yche.springmind.common.enums.UserStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateUserStatusRequest(
        @NotNull(message = "用户状态不能为空")
        UserStatus status
) {
}
