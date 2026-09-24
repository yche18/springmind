package com.yche.springmind.auth.service;

import com.yche.springmind.auth.model.dto.RegisterRequest;
import com.yche.springmind.auth.security.JwtAccessTokenService;
import com.yche.springmind.auth.security.RefreshTokenService;
import com.yche.springmind.common.enums.SystemRole;
import com.yche.springmind.common.enums.UserStatus;
import com.yche.springmind.common.exception.BusinessException;
import com.yche.springmind.identity.service.CurrentUserService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 编排用户注册、登录、令牌刷新、退出和当前用户查询等完整认证流程。
 *
 * <p>位于业务服务层：编排领域操作和基础设施调用，并集中维护事务、权限校验及失败处理边界。</p>
 */
@Service
public class AuthService {

    private static final String INVALID_CREDENTIALS_MESSAGE = "账号或密码错误";
    private static final String INVALID_PASSWORD_MESSAGE = "新密码必须至少 8 位，且同时包含字母和数字";
    private static final int MAX_LOGIN_ID_LENGTH = 128;
    private static final int MAX_USERNAME_LENGTH = 64;
    private static final int MAX_EMAIL_LENGTH = 128;
    private static final int MAX_DISPLAY_NAME_LENGTH = 128;
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MAX_PASSWORD_LENGTH = 256;
    private static final int BCRYPT_MAX_PASSWORD_BYTES = 72;

    private final JdbcTemplate jdbcTemplate;
    private final PasswordHasher passwordHasher;
    private final JwtAccessTokenService jwtAccessTokenService;
    private final RefreshTokenService refreshTokenService;
    private final Clock clock;

    /**
     * 创建并初始化 {@link AuthService}，保存该组件运行所需的依赖与配置。
     * <p>
     * 实现要点：使用安全哈希校验或保存密码；签发或解析 JWT 访问令牌；维护刷新令牌的签发、轮换或撤销状态。
     *
     * @param jdbcTemplate 方法参数 {@code jdbcTemplate}
     * @param passwordHasher 方法参数 {@code passwordHasher}
     * @param jwtAccessTokenService 方法参数 {@code jwtAccessTokenService}
     * @param refreshTokenService 方法参数 {@code refreshTokenService}
     * @param clock 用于生成可测试时间的时钟
     */
    public AuthService(
            JdbcTemplate jdbcTemplate,
            PasswordHasher passwordHasher,
            JwtAccessTokenService jwtAccessTokenService,
            RefreshTokenService refreshTokenService,
            Clock clock
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordHasher = passwordHasher;
        this.jwtAccessTokenService = jwtAccessTokenService;
        this.refreshTokenService = refreshTokenService;
        this.clock = clock;
    }

    /**
     * 校验用户名或邮箱与密码，签发访问令牌和刷新令牌，并记录本次成功登录。
     * <p>
     * 实现要点：使用事务保证多次数据库操作的一致性；先校验输入、状态或业务边界；维护刷新令牌的签发、轮换或撤销状态。
     *
     * @param loginId 用户名或邮箱形式的登录标识
     * @param password 用户提交的明文密码，仅用于本次校验或哈希计算
     * @return 方法执行结果，具体结构由返回类型 {@code AuthTokens} 表示
     */
    @Transactional
    public AuthTokens login(String loginId, String password) {
        LoginCommand command = validateLoginCommand(loginId, password);
        UserAccount user = loadUserForLogin(command.loginId());
        ensureUserCanLogin(user, command.password());
        refreshTokenService.revokeActiveTokens(user.userId());
        RefreshTokenService.IssuedRefreshToken refreshToken = refreshTokenService.issueToken(user.userId());
        updateSuccessfulLogin(user.userId());
        return new AuthTokens(
                user.userId(),
                issueAccessToken(user),
                refreshToken.refreshToken(),
                user.mustChangePassword()
        );
    }

    /**
     * 完成用户自助注册；密码由用户本人设置，因此新账号无需执行首次改密。
     * <p>
     * 实现要点：使用事务保证多次数据库操作的一致性；先校验输入、状态或业务边界；持久化数据库状态变更；使用安全哈希校验或保存密码。
     *
     * @param request 已经通过控制器基础校验的请求对象
     */
    @Transactional
    public void register(RegisterRequest request) {
        RegisterCommand command = validateRegisterCommand(request);
        ensureUniqueIdentity(command.username(), command.email());
        jdbcTemplate.update(
                """
                insert into users (
                    user_code, username, email, display_name, password_hash,
                    system_role, status, must_change_password, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, now(), now())
                """,
                command.username(),
                command.username(),
                command.email(),
                command.displayName(),
                passwordHasher.hash(command.password()),
                SystemRole.USER.name(),
                UserStatus.ACTIVE.name(),
                false
        );
    }

    /**
     * 校验并轮换刷新令牌，为仍可登录的用户签发新的访问凭据。
     * <p>
     * 实现要点：使用事务保证多次数据库操作的一致性；维护刷新令牌的签发、轮换或撤销状态；先校验输入、状态或业务边界。
     *
     * @param refreshToken 客户端提交的刷新令牌
     * @return 方法执行结果，具体结构由返回类型 {@code AuthTokens} 表示
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    @Transactional
    public AuthTokens refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BusinessException("refresh token 不存在或已失效");
        }
        RefreshTokenRecord activeToken = refreshTokenService.findActiveToken(refreshToken)
                .orElseThrow(() -> new BusinessException("refresh token 不存在或已失效"));
        UserAccount user = loadUserById(activeToken.userId());
        ensureRefreshAllowed(user);
        refreshTokenService.revokeToken(refreshToken);
        RefreshTokenService.IssuedRefreshToken nextRefreshToken = refreshTokenService.issueToken(user.userId());
        return new AuthTokens(
                user.userId(),
                issueAccessToken(user),
                nextRefreshToken.refreshToken(),
                user.mustChangePassword()
        );
    }

    /**
     * 撤销当前刷新令牌，使对应会话无法继续换取访问令牌。
     * <p>
     * 实现要点：使用事务保证多次数据库操作的一致性；维护刷新令牌的签发、轮换或撤销状态。
     *
     * @param refreshToken 客户端提交的刷新令牌
     */
    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        refreshTokenService.revokeToken(refreshToken);
    }

    /**
     * 返回 {@code currentUser} 对应的配置或状态值。
     *
     * @param userId 用户唯一标识
     * @return 查询得到的当前用户结果
     */
    public CurrentUserService.CurrentUser getCurrentUser(Long userId) {
        UserAccount user = loadUserById(userId);
        return new CurrentUserService.CurrentUser(
                user.userId(),
                user.userCode(),
                user.displayName(),
                user.systemRole(),
                user.mustChangePassword()
        );
    }

    /**
     * 执行 {@code validateRegisterCommand} 对应的业务步骤。
     * <p>
     * 实现要点：先校验输入、状态或业务边界。
     *
     * @param request 已经通过控制器基础校验的请求对象
     * @return 方法执行结果，具体结构由返回类型 {@code RegisterCommand} 表示
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    private RegisterCommand validateRegisterCommand(RegisterRequest request) {
        if (request == null) {
            throw new BusinessException("注册请求不能为空");
        }
        String username = normalizeRequiredValue(request.username(), "用户名不能为空", "用户名长度不能超过 64", MAX_USERNAME_LENGTH);
        String email = normalizeRequiredValue(request.email(), "邮箱不能为空", "邮箱长度不能超过 128", MAX_EMAIL_LENGTH);
        String displayName = normalizeRequiredValue(
                request.displayName(),
                "显示名称不能为空",
                "显示名称长度不能超过 128",
                MAX_DISPLAY_NAME_LENGTH
        );
        validateRegisterPassword(request.password());
        return new RegisterCommand(username, email, displayName, request.password());
    }

    /**
     * 执行 {@code validateLoginCommand} 对应的业务步骤。
     * <p>
     * 实现要点：先校验输入、状态或业务边界。
     *
     * @param loginId 用户名或邮箱形式的登录标识
     * @param password 用户提交的明文密码，仅用于本次校验或哈希计算
     * @return 方法执行结果，具体结构由返回类型 {@code LoginCommand} 表示
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    private LoginCommand validateLoginCommand(String loginId, String password) {
        String normalizedLoginId = normalizeLoginId(loginId);
        if (password == null || password.isBlank()) {
            throw new BusinessException("密码不能为空");
        }
        if (password.length() > MAX_PASSWORD_LENGTH) {
            throw new BusinessException("密码长度非法");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_PASSWORD_BYTES) {
            throw new BusinessException("密码长度超过安全上限，请控制在 72 字节以内");
        }
        return new LoginCommand(normalizedLoginId, password);
    }

    /**
     * 执行 {@code normalizeLoginId} 对应的业务步骤。
     * <p>
     * 实现要点：先校验输入、状态或业务边界。
     *
     * @param loginId 用户名或邮箱形式的登录标识
     * @return 处理后得到的字符串结果
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    private String normalizeLoginId(String loginId) {
        if (loginId == null || loginId.isBlank()) {
            throw new BusinessException("登录标识不能为空");
        }
        String normalizedLoginId = loginId.trim();
        if (normalizedLoginId.length() > MAX_LOGIN_ID_LENGTH) {
            throw new BusinessException("登录标识长度非法");
        }
        return normalizedLoginId;
    }

    /**
     * 执行 {@code validateRegisterPassword} 对应的业务步骤。
     * <p>
     * 实现要点：先校验输入、状态或业务边界。
     *
     * @param password 用户提交的明文密码，仅用于本次校验或哈希计算
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    private void validateRegisterPassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new BusinessException(INVALID_PASSWORD_MESSAGE);
        }
        if (password.length() > MAX_PASSWORD_LENGTH) {
            throw new BusinessException("密码长度非法");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_PASSWORD_BYTES) {
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
            throw new BusinessException(INVALID_PASSWORD_MESSAGE);
        }
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

    /**
     * 执行 {@code ensureUniqueIdentity} 对应的业务步骤。
     * <p>
     * 实现要点：先校验输入、状态或业务边界。
     *
     * @param username 用户登录名
     * @param email 用户邮箱地址
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    private void ensureUniqueIdentity(String username, String email) {
        if (existsByUsername(username)) {
            throw new BusinessException("用户名已存在");
        }
        if (existsByEmail(email)) {
            throw new BusinessException("邮箱已存在");
        }
    }

    /**
     * 判断 {@code byUsername} 对应的数据是否存在。
     * <p>
     * 实现要点：读取数据库中的当前状态。
     *
     * @param username 用户登录名
     * @return 满足条件时返回 {@code true}，否则返回 {@code false}
     */
    private boolean existsByUsername(String username) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from users where username = ?",
                Integer.class,
                username
        );
        return count != null && count > 0;
    }

    /**
     * 判断 {@code byEmail} 对应的数据是否存在。
     * <p>
     * 实现要点：读取数据库中的当前状态。
     *
     * @param email 用户邮箱地址
     * @return 满足条件时返回 {@code true}，否则返回 {@code false}
     */
    private boolean existsByEmail(String email) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from users where email = ?",
                Integer.class,
                email
        );
        return count != null && count > 0;
    }

    /**
     * 执行 {@code loadUserForLogin} 对应的业务步骤。
     * <p>
     * 实现要点：读取数据库中的当前状态；先校验输入、状态或业务边界。
     *
     * @param loginId 用户名或邮箱形式的登录标识
     * @return 查询得到的用户用于登录结果
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    private UserAccount loadUserForLogin(String loginId) {
        List<UserAccount> users = jdbcTemplate.query(
                """
                select id, user_code, username, email, display_name, password_hash,
                       system_role, status, must_change_password
                from users
                where username = ? or email = ?
                order by id
                for update
                """,
                (resultSet, rowNum) -> new UserAccount(
                        resultSet.getLong("id"),
                        resultSet.getString("user_code"),
                        resultSet.getString("username"),
                        resultSet.getString("email"),
                        resultSet.getString("display_name"),
                        resultSet.getString("password_hash"),
                        SystemRole.valueOf(resultSet.getString("system_role")),
                        UserStatus.valueOf(resultSet.getString("status")),
                        resultSet.getBoolean("must_change_password")
                ),
                loginId,
                loginId
        );
        if (users.isEmpty()) {
            throw new BusinessException(INVALID_CREDENTIALS_MESSAGE);
        }
        ensureUniqueLoginMatch(users);
        return users.getFirst();
    }

    /**
     * 执行 {@code loadUserById} 对应的业务步骤。
     * <p>
     * 实现要点：读取数据库中的当前状态。
     *
     * @param userId 用户唯一标识
     * @return 查询得到的用户按标识结果
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    private UserAccount loadUserById(Long userId) {
        List<UserAccount> users = jdbcTemplate.query(
                """
                select id, user_code, username, email, display_name, password_hash,
                       system_role, status, must_change_password
                from users
                where id = ?
                """,
                (resultSet, rowNum) -> new UserAccount(
                        resultSet.getLong("id"),
                        resultSet.getString("user_code"),
                        resultSet.getString("username"),
                        resultSet.getString("email"),
                        resultSet.getString("display_name"),
                        resultSet.getString("password_hash"),
                        SystemRole.valueOf(resultSet.getString("system_role")),
                        UserStatus.valueOf(resultSet.getString("status")),
                        resultSet.getBoolean("must_change_password")
                ),
                userId
        );
        if (users.isEmpty()) {
            throw new BusinessException("用户不存在");
        }
        return users.getFirst();
    }

    /**
     * 执行 {@code ensureUniqueLoginMatch} 对应的业务步骤。
     * <p>
     * 实现要点：先校验输入、状态或业务边界。
     *
     * @param users 方法参数 {@code users}
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    private void ensureUniqueLoginMatch(List<UserAccount> users) {
        Set<Long> userIds = new LinkedHashSet<>();
        for (UserAccount user : users) {
            userIds.add(user.userId());
        }
        if (userIds.size() > 1) {
            throw new BusinessException("登录标识存在冲突，请联系管理员处理");
        }
    }

    /**
     * 校验账号状态与密码是否允许登录，避免禁用账号或无有效密码的账号进入系统。
     * <p>
     * 实现要点：先校验输入、状态或业务边界；使用安全哈希校验或保存密码。
     *
     * @param user 方法参数 {@code user}
     * @param password 用户提交的明文密码，仅用于本次校验或哈希计算
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    private void ensureUserCanLogin(UserAccount user, String password) {
        if (user.status() == UserStatus.DISABLED) {
            throw new BusinessException("账号已被禁用");
        }
        if (user.passwordHash() == null || !passwordHasher.matches(password, user.passwordHash())) {
            throw new BusinessException(INVALID_CREDENTIALS_MESSAGE);
        }
    }

    /**
     * 执行 {@code ensureRefreshAllowed} 对应的业务步骤。
     * <p>
     * 实现要点：先校验输入、状态或业务边界。
     *
     * @param user 方法参数 {@code user}
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    private void ensureRefreshAllowed(UserAccount user) {
        if (user.status() == UserStatus.DISABLED) {
            throw new BusinessException("账号已被禁用");
        }
    }

    /**
     * 执行 {@code updateSuccessfulLogin} 对应的业务步骤。
     * <p>
     * 实现要点：持久化数据库状态变更。
     *
     * @param userId 用户唯一标识
     */
    private void updateSuccessfulLogin(Long userId) {
        jdbcTemplate.update(
                "update users set last_login_at = ?, updated_at = ? where id = ?",
                LocalDateTime.now(clock),
                LocalDateTime.now(clock),
                userId
        );
    }

    /**
     * 根据用户身份、角色和改密状态签发 JWT 访问令牌。
     *
     * @param user 方法参数 {@code user}
     * @return 处理后得到的字符串结果
     */
    private String issueAccessToken(UserAccount user) {
        return jwtAccessTokenService.issueToken(
                new JwtAccessTokenService.TokenSubject(
                        user.userId(),
                        user.userCode(),
                        user.displayName(),
                        user.systemRole(),
                        user.mustChangePassword()
                )
        );
    }

    /**
     * 封装认证成功后签发的访问令牌、刷新令牌及相关过期信息。
     *
     * <p>仅在 {@code AuthService} 的实现过程中使用，用不可变数据结构收拢中间结果，避免参数和值的含义混淆。</p>
     */
    public record AuthTokens(
            Long userId,
            String accessToken,
            String refreshToken,
            boolean mustChangePassword
    ) {
    }

    /**
     * 保存登录输入经过规范化后的账号标识和密码。
     *
     * <p>仅在 {@code AuthService} 的实现过程中使用，用不可变数据结构收拢中间结果，避免参数和值的含义混淆。</p>
     */
    private record LoginCommand(String loginId, String password) {
    }

    /**
     * 保存注册输入经过规范化后的账号、邮箱、显示名和密码。
     *
     * <p>仅在 {@code AuthService} 的实现过程中使用，用不可变数据结构收拢中间结果，避免参数和值的含义混淆。</p>
     */
    private record RegisterCommand(String username, String email, String displayName, String password) {
    }

    /**
     * 表示认证流程从数据库加载的最小用户账号快照。
     *
     * <p>仅在 {@code AuthService} 的实现过程中使用，用不可变数据结构收拢中间结果，避免参数和值的含义混淆。</p>
     */
    private record UserAccount(
            Long userId,
            String userCode,
            String username,
            String email,
            String displayName,
            String passwordHash,
            SystemRole systemRole,
            UserStatus status,
            boolean mustChangePassword
    ) {
    }
}
