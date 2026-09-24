package com.yche.springmind.user.service;

import com.yche.springmind.auth.security.RefreshTokenService;
import com.yche.springmind.auth.service.PasswordHasher;
import com.yche.springmind.common.exception.BusinessException;
import com.yche.springmind.identity.service.CurrentUserService;
import com.yche.springmind.user.model.dto.ChangePasswordRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 处理当前用户修改密码并维护首次登录强制改密状态。
 *
 * <p>位于业务服务层：编排领域操作和基础设施调用，并集中维护事务、权限校验及失败处理边界。</p>
 */
@Service
public class AccountService {

    private static final int MIN_PASSWORD_LENGTH = 8;

    private final JdbcTemplate jdbcTemplate;
    private final PasswordHasher passwordHasher;
    private final RefreshTokenService refreshTokenService;

    /**
     * 创建并初始化 {@link AccountService}，保存该组件运行所需的依赖与配置。
     * <p>
     * 实现要点：使用安全哈希校验或保存密码；维护刷新令牌的签发、轮换或撤销状态。
     *
     * @param jdbcTemplate 方法参数 {@code jdbcTemplate}
     * @param passwordHasher 方法参数 {@code passwordHasher}
     * @param refreshTokenService 方法参数 {@code refreshTokenService}
     */
    public AccountService(
            JdbcTemplate jdbcTemplate,
            PasswordHasher passwordHasher,
            RefreshTokenService refreshTokenService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordHasher = passwordHasher;
        this.refreshTokenService = refreshTokenService;
    }

    /**
     * 验证旧密码并保存新密码，同时清除首次登录强制改密标记。
     * <p>
     * 实现要点：使用事务保证多次数据库操作的一致性；先校验输入、状态或业务边界；使用安全哈希校验或保存密码；持久化数据库状态变更；维护刷新令牌的签发、轮换或撤销状态。
     *
     * @param currentUser 当前已经通过认证的用户信息
     * @param request 已经通过控制器基础校验的请求对象
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    @Transactional
    public void changePassword(CurrentUserService.CurrentUser currentUser, ChangePasswordRequest request) {
        validatePasswordPolicy(request.newPassword());
        UserCredential userCredential = loadCredential(currentUser.userId());
        if (userCredential == null || userCredential.passwordHash() == null) {
            throw new BusinessException("用户不存在");
        }
        if (!passwordHasher.matches(request.currentPassword(), userCredential.passwordHash())) {
            throw new BusinessException("当前密码不正确");
        }
        if (request.currentPassword().equals(request.newPassword())) {
            throw new BusinessException("新密码不能与当前密码相同");
        }
        int updated = jdbcTemplate.update(
                """
                update users
                set password_hash = ?, must_change_password = false, updated_at = now()
                where id = ?
                """,
                passwordHasher.hash(request.newPassword()),
                currentUser.userId()
        );
        if (updated == 0) {
            throw new BusinessException("用户不存在");
        }
        refreshTokenService.revokeActiveTokens(currentUser.userId());
    }

    /**
     * 执行 {@code validatePasswordPolicy} 对应的业务步骤。
     * <p>
     * 实现要点：先校验输入、状态或业务边界。
     *
     * @param newPassword 方法参数 {@code newPassword}
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    private void validatePasswordPolicy(String newPassword) {
        if (newPassword == null || newPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new BusinessException("新密码必须至少 8 位，且同时包含字母和数字");
        }
        boolean hasLetter = false;
        boolean hasDigit = false;
        for (int i = 0; i < newPassword.length(); i++) {
            char current = newPassword.charAt(i);
            if (Character.isLetter(current)) {
                hasLetter = true;
            }
            if (Character.isDigit(current)) {
                hasDigit = true;
            }
        }
        if (!hasLetter || !hasDigit) {
            throw new BusinessException("新密码必须至少 8 位，且同时包含字母和数字");
        }
    }

    /**
     * 执行 {@code loadCredential} 对应的业务步骤。
     * <p>
     * 实现要点：读取数据库中的当前状态。
     *
     * @param userId 用户唯一标识
     * @return 查询得到的Credential结果
     */
    private UserCredential loadCredential(Long userId) {
        return jdbcTemplate.query(
                """
                select id, password_hash
                from users
                where id = ?
                """,
                resultSet -> resultSet.next()
                        ? new UserCredential(resultSet.getLong("id"), resultSet.getString("password_hash"))
                        : null,
                userId
        );
    }

    /**
     * 封装修改密码时查询到的用户标识和密码哈希。
     *
     * <p>仅在 {@code AccountService} 的实现过程中使用，用不可变数据结构收拢中间结果，避免参数和值的含义混淆。</p>
     */
    private record UserCredential(Long userId, String passwordHash) {
    }
}
