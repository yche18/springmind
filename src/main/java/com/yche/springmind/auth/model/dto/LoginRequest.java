package com.yche.springmind.auth.model.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 承载用户登录时提交的账号标识和明文密码。
 *
 * <p>这是接口输入契约，只承载并校验调用方提交的数据，不在对象内部实现业务流程。</p>
 */
public record LoginRequest(
        @NotBlank(message = "登录标识不能为空")
        String loginId,
        @NotBlank(message = "密码不能为空")
        String password
) {
}
