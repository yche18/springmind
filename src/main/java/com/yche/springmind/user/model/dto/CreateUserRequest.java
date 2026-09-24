package com.yche.springmind.user.model.dto;

import com.yche.springmind.common.enums.SystemRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 承载管理员创建用户时提交的账号资料和初始密码。
 *
 * <p>这是接口输入契约，只承载并校验调用方提交的数据，不在对象内部实现业务流程。</p>
 */
public record CreateUserRequest(
        @NotBlank(message = "用户名不能为空")
        @Size(max = 64, message = "用户名长度不能超过 64")
        String username,
        @Email(message = "邮箱格式非法")
        @NotBlank(message = "邮箱不能为空")
        @Size(max = 128, message = "邮箱长度不能超过 128")
        String email,
        @NotBlank(message = "显示名称不能为空")
        @Size(max = 128, message = "显示名称长度不能超过 128")
        String displayName,
        @NotNull(message = "系统角色不能为空")
        SystemRole systemRole,
        @NotBlank(message = "密码不能为空")
        @Size(max = 256, message = "密码长度非法")
        String initialPassword,
        boolean mustChangePassword
) {
}
