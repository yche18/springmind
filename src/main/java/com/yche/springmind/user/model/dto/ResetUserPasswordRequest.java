package com.yche.springmind.user.model.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 承载管理员为指定用户设置新初始密码的请求。
 *
 * <p>这是接口输入契约，只承载并校验调用方提交的数据，不在对象内部实现业务流程。</p>
 */
public record ResetUserPasswordRequest(
        @NotBlank(message = "新密码不能为空")
        String newPassword
) {
}
