package com.yche.springmind.user.model.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 承载当前用户修改密码时提交的旧密码和新密码。
 *
 * <p>这是接口输入契约，只承载并校验调用方提交的数据，不在对象内部实现业务流程。</p>
 */
public record ChangePasswordRequest(
        @NotBlank(message = "当前密码不能为空")
        String currentPassword,
        @NotBlank(message = "新密码不能为空")
        String newPassword
) {
}
