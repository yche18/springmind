package com.yche.springmind.user.service;

import com.yche.springmind.auth.security.RefreshTokenService;
import com.yche.springmind.auth.service.PasswordHasher;
import com.yche.springmind.common.enums.UserStatus;
import com.yche.springmind.common.exception.BusinessException;
import com.yche.springmind.user.model.dto.CreateUserRequest;
import com.yche.springmind.user.model.dto.ResetUserPasswordRequest;
import com.yche.springmind.user.model.dto.UpdateUserStatusRequest;
import com.yche.springmind.user.model.vo.AdminUserItemResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 处理管理员创建用户、重置初始密码和修改账号状态等操作。
 *
 * <p>位于业务服务层：编排领域操作和基础设施调用，并集中维护事务、权限校验及失败处理边界。</p>
 */
@Service
public class AdminUserService {

    private final JdbcTemplate jdbcTemplate;
    private final PasswordHasher passwordHasher;
    private final RefreshTokenService refreshTokenService;
    private final UserQueryService userQueryService;

    /**
     * 创建并初始化 {@link AdminUserService}，保存该组件运行所需的依赖与配置。
     * <p>
     * 实现要点：使用安全哈希校验或保存密码；维护刷新令牌的签发、轮换或撤销状态。
     *
     * @param jdbcTemplate 方法参数 {@code jdbcTemplate}
     * @param passwordHasher 方法参数 {@code passwordHasher}
     * @param refreshTokenService 方法参数 {@code refreshTokenService}
     * @param userQueryService 方法参数 {@code userQueryService}
     */
    public AdminUserService(
            JdbcTemplate jdbcTemplate,
            PasswordHasher passwordHasher,
            RefreshTokenService refreshTokenService,
            UserQueryService userQueryService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordHasher = passwordHasher;
        this.refreshTokenService = refreshTokenService;
        this.userQueryService = userQueryService;
    }

    /**
     * 执行 {@code listUsers} 对应的业务步骤。
     *
     * @return 符合条件的结果集合；无结果时返回空集合
     */
    public List<AdminUserItemResponse> listUsers() {
        return userQueryService.listUsers();
    }

    /**
     * 返回 {@code user} 对应的配置或状态值。
     *
     * @param userId 用户唯一标识
     * @return 查询得到的用户结果
     */
    public AdminUserItemResponse getUser(Long userId) {
        return userQueryService.getUser(requireUserId(userId));
    }

    /**
     * 由管理员创建可登录用户，并按请求决定是否要求首次登录修改密码。
     * <p>
     * 实现要点：使用事务保证多次数据库操作的一致性；先校验输入、状态或业务边界；读取数据库中的当前状态；使用安全哈希校验或保存密码。
     *
     * @param request 已经通过控制器基础校验的请求对象
     * @return 方法执行结果，具体结构由返回类型 {@code AdminUserItemResponse} 表示
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    @Transactional
    public AdminUserItemResponse createUser(CreateUserRequest request) {
        String username = normalizeRequiredValue(request.username(), "用户名不能为空", "用户名长度不能超过 64", 64);
        String email = normalizeRequiredValue(request.email(), "邮箱不能为空", "邮箱长度不能超过 128", 128);
        String displayName = normalizeRequiredValue(request.displayName(), "显示名称不能为空", "显示名称长度不能超过 128", 128);
        if (userQueryService.existsByUsername(username)) {
            throw new BusinessException("用户名已存在");
        }
        if (userQueryService.existsByEmail(email)) {
            throw new BusinessException("邮箱已存在");
        }
        validatePasswordPolicy(request.initialPassword());
        Long userId = jdbcTemplate.queryForObject(
                """
                insert into users (
                    user_code, username, email, display_name, password_hash,
                    system_role, status, must_change_password, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, now(), now())
                returning id
                """,
                Long.class,
                username,
                username,
                email,
                displayName,
                passwordHasher.hash(request.initialPassword()),
                request.systemRole().name(),
                UserStatus.ACTIVE.name(),
                request.mustChangePassword()
        );
        return userQueryService.getUser(userId);
    }

    /**
     * 执行 {@code updateUserStatus} 对应的业务步骤。
     * <p>
     * 实现要点：使用事务保证多次数据库操作的一致性；持久化数据库状态变更；先校验输入、状态或业务边界；维护刷新令牌的签发、轮换或撤销状态。
     *
     * @param userId 用户唯一标识
     * @param request 已经通过控制器基础校验的请求对象
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    @Transactional
    public void updateUserStatus(Long userId, UpdateUserStatusRequest request) {
        int updated = jdbcTemplate.update(
                "update users set status = ?, updated_at = now() where id = ?",
                request.status().name(),
                requireUserId(userId)
        );
        if (updated == 0) {
            throw new BusinessException("用户不存在");
        }
        if (request.status() == UserStatus.DISABLED) {
            refreshTokenService.revokeActiveTokens(userId);
        }
    }

    /**
     * 由管理员重置用户密码，并要求该用户下次登录后立即修改初始密码。
     * <p>
     * 实现要点：使用事务保证多次数据库操作的一致性；先校验输入、状态或业务边界；持久化数据库状态变更；使用安全哈希校验或保存密码；维护刷新令牌的签发、轮换或撤销状态。
     *
     * @param userId 用户唯一标识
     * @param request 已经通过控制器基础校验的请求对象
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    @Transactional
    public void resetPassword(Long userId, ResetUserPasswordRequest request) {
        validatePasswordPolicy(request.newPassword());
        int updated = jdbcTemplate.update(
                """
                update users
                set password_hash = ?, must_change_password = true, updated_at = now()
                where id = ?
                """,
                passwordHasher.hash(request.newPassword()),
                requireUserId(userId)
        );
        if (updated == 0) {
            throw new BusinessException("用户不存在");
        }
        refreshTokenService.revokeActiveTokens(userId);
    }

    /**
     * 执行 {@code validatePasswordPolicy} 对应的业务步骤。
     * <p>
     * 实现要点：先校验输入、状态或业务边界。
     *
     * @param password 用户提交的明文密码，仅用于本次校验或哈希计算
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    private void validatePasswordPolicy(String password) {
        int minPasswordLength = 8;
        int bcryptMaxPasswordBytes = 72;
        if (password == null || password.length() < minPasswordLength) {
            throw new BusinessException("新密码必须至少 8 位，且同时包含字母和数字");
        }
        if (password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > bcryptMaxPasswordBytes) {
            throw new BusinessException("密码长度超过安全上限，请控制在 72 字节以内");
        }
        boolean hasLetter = false;
        boolean hasDigit = false;
        for (int i = 0; i < password.length(); i++) {
            char current = password.charAt(i);
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
     * 执行 {@code requireUserId} 对应的业务步骤。
     * <p>
     * 实现要点：先校验输入、状态或业务边界。
     *
     * @param userId 用户唯一标识
     * @return 计算或处理得到的数值结果
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    private Long requireUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BusinessException("用户ID非法");
        }
        return userId;
    }

    /**
     * 执行 {@code normalizeRequiredValue} 对应的业务步骤。
     * <p>
     * 实现要点：先校验输入、状态或业务边界。
     *
     * @param value 方法参数 {@code value}
     * @param blankMessage 方法参数 {@code blankMessage}
     * @param lengthMessage 方法参数 {@code lengthMessage}
     * @param maxLength 方法参数 {@code maxLength}
     * @return 处理后得到的字符串结果
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    private String normalizeRequiredValue(String value, String blankMessage, String lengthMessage, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(blankMessage);
        }
        String normalizedValue = value.trim();
        if (normalizedValue.length() > maxLength) {
            throw new BusinessException(lengthMessage);
        }
        return normalizedValue;
    }
}
