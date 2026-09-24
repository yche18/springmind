package com.yche.springmind.user.model.dto;

import com.yche.springmind.common.enums.UserStatus;
import jakarta.validation.constraints.NotNull;

/**
 * 承载管理员启用或停用用户账号的目标状态。
 *
 * <p>这是接口输入契约，只承载并校验调用方提交的数据，不在对象内部实现业务流程。</p>
 */
public record UpdateUserStatusRequest(
        @NotNull(message = "用户状态不能为空")
        UserStatus status
) {
}
