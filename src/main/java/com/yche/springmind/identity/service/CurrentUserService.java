package com.yche.springmind.identity.service;

import com.yche.springmind.auth.security.JwtAuthenticationFilter;
import com.yche.springmind.common.enums.SystemRole;
import com.yche.springmind.common.enums.UserStatus;
import com.yche.springmind.common.exception.BusinessException;
import com.yche.springmind.common.exception.ForbiddenException;
import com.yche.springmind.common.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 从安全上下文解析当前用户，并提供角色、账号状态和知识组权限校验能力。
 *
 * <p>位于业务服务层：编排领域操作和基础设施调用，并集中维护事务、权限校验及失败处理边界。</p>
 */
@Service
public class CurrentUserService {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 创建并初始化 {@link CurrentUserService}，保存该组件运行所需的依赖与配置。
     *
     * @param jdbcTemplate 方法参数 {@code jdbcTemplate}
     */
    public CurrentUserService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 返回 {@code requiredCurrentUser} 对应的配置或状态值。
     *
     * @param request 已经通过控制器基础校验的请求对象
     * @return 查询得到的Required当前用户结果
     */
    public CurrentUser getRequiredCurrentUser(HttpServletRequest request) {
        JwtAuthenticationFilter.AuthenticatedUser authenticatedUser = extractAuthenticatedUser(request);
        if (authenticatedUser != null) {
            return loadUserById(authenticatedUser.userId());
        }
        throw new UnauthorizedException("当前请求未登录");
    }

    /**
     * 执行 {@code requireSystemAdmin} 对应的业务步骤。
     * <p>
     * 实现要点：先校验输入、状态或业务边界；校验群组成员关系和角色权限。
     *
     * @param request 已经通过控制器基础校验的请求对象
     * @return 方法执行结果，具体结构由返回类型 {@code CurrentUser} 表示
     */
    public CurrentUser requireSystemAdmin(HttpServletRequest request) {
        CurrentUser currentUser = getRequiredCurrentUser(request);
        if (currentUser.systemRole() != SystemRole.ADMIN) {
            throw new ForbiddenException("当前用户不是系统管理员");
        }
        return currentUser;
    }

    /**
     * 执行 {@code requireBusinessUser} 对应的业务步骤。
     * <p>
     * 实现要点：先校验输入、状态或业务边界；校验群组成员关系和角色权限。
     *
     * @param request 已经通过控制器基础校验的请求对象
     * @return 方法执行结果，具体结构由返回类型 {@code CurrentUser} 表示
     */
    public CurrentUser requireBusinessUser(HttpServletRequest request) {
        CurrentUser currentUser = getRequiredCurrentUser(request);
        if (currentUser.systemRole() == SystemRole.ADMIN) {
            throw new ForbiddenException("系统管理员不能访问普通业务区");
        }
        return currentUser;
    }

    /**
     * 执行 {@code extractAuthenticatedUser} 对应的业务步骤。
     *
     * @param request 已经通过控制器基础校验的请求对象
     * @return 方法执行结果，具体结构由返回类型 {@code JwtAuthenticationFilter.AuthenticatedUser} 表示
     */
    private JwtAuthenticationFilter.AuthenticatedUser extractAuthenticatedUser(HttpServletRequest request) {
        Object attribute = request.getAttribute(JwtAuthenticationFilter.AUTHENTICATED_USER_REQUEST_ATTRIBUTE);
        if (attribute instanceof JwtAuthenticationFilter.AuthenticatedUser authenticatedUser) {
            return authenticatedUser;
        }
        return null;
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
    private CurrentUser loadUserById(Long userId) {
        List<CurrentUser> users = jdbcTemplate.query(
                """
                select id, user_code, display_name, system_role, status, must_change_password
                from users
                where id = ?
                """,
                (resultSet, rowNum) -> mapCurrentUser(resultSet.getLong("id"),
                        resultSet.getString("user_code"),
                        resultSet.getString("display_name"),
                        resultSet.getString("system_role"),
                        resultSet.getString("status"),
                        resultSet.getBoolean("must_change_password")),
                userId
        );
        if (users.isEmpty()) {
            throw new BusinessException("当前用户不存在");
        }
        return users.getFirst();
    }

    /**
     * 执行 {@code mapCurrentUser} 对应的业务步骤。
     *
     * @param userId 用户唯一标识
     * @param userCode 方法参数 {@code userCode}
     * @param displayName 面向界面展示的用户名称
     * @param systemRole 方法参数 {@code systemRole}
     * @param status 目标业务状态
     * @param mustChangePassword 方法参数 {@code mustChangePassword}
     * @return 方法执行结果，具体结构由返回类型 {@code CurrentUser} 表示
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    private CurrentUser mapCurrentUser(
            Long userId,
            String userCode,
            String displayName,
            String systemRole,
            String status,
            boolean mustChangePassword
    ) {
        if (UserStatus.DISABLED.name().equals(status)) {
            throw new BusinessException("账号已被禁用");
        }
        return new CurrentUser(
                userId,
                userCode,
                displayName,
                SystemRole.valueOf(systemRole),
                mustChangePassword
        );
    }

    /**
     * 表示从安全上下文和数据库解析出的当前用户身份与角色。
     *
     * <p>仅在 {@code CurrentUserService} 的实现过程中使用，用不可变数据结构收拢中间结果，避免参数和值的含义混淆。</p>
     */
    public record CurrentUser(
            Long userId,
            String userCode,
            String displayName,
            SystemRole systemRole,
            boolean mustChangePassword
    ) {
        /**
         * 创建并初始化 {@link CurrentUser}，保存该组件运行所需的依赖与配置。
         *
         * @param userId 用户唯一标识
         * @param userCode 方法参数 {@code userCode}
         * @param displayName 面向界面展示的用户名称
         */
        public CurrentUser(Long userId, String userCode, String displayName) {
            this(userId, userCode, displayName, SystemRole.USER, false);
        }
    }
}
