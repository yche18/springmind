package com.yche.springmind.user.service;

import com.yche.springmind.common.enums.SystemRole;
import com.yche.springmind.common.enums.UserStatus;
import com.yche.springmind.common.exception.BusinessException;
import com.yche.springmind.user.model.vo.AdminUserItemResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 为管理端提供用户列表和用户详情查询，并组装稳定的响应视图。
 *
 * <p>位于业务服务层：编排领域操作和基础设施调用，并集中维护事务、权限校验及失败处理边界。</p>
 */
@Service
public class UserQueryService {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 创建并初始化 {@link UserQueryService}，保存该组件运行所需的依赖与配置。
     *
     * @param jdbcTemplate 方法参数 {@code jdbcTemplate}
     */
    public UserQueryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 执行 {@code listUsers} 对应的业务步骤。
     * <p>
     * 实现要点：读取数据库中的当前状态。
     *
     * @return 符合条件的结果集合；无结果时返回空集合
     */
    public List<AdminUserItemResponse> listUsers() {
        return jdbcTemplate.query(
                """
                select id, user_code, username, email, display_name,
                       system_role, status, must_change_password, last_login_at
                from users
                order by id
                """,
                (resultSet, rowNum) -> new AdminUserItemResponse(
                        resultSet.getLong("id"),
                        resultSet.getString("user_code"),
                        resultSet.getString("username"),
                        resultSet.getString("email"),
                        resultSet.getString("display_name"),
                        SystemRole.valueOf(resultSet.getString("system_role")),
                        UserStatus.valueOf(resultSet.getString("status")),
                        resultSet.getBoolean("must_change_password"),
                        resultSet.getTimestamp("last_login_at") == null
                                ? null
                                : resultSet.getTimestamp("last_login_at").toLocalDateTime()
                )
        );
    }

    /**
     * 返回 {@code user} 对应的配置或状态值。
     *
     * @param userId 用户唯一标识
     * @return 查询得到的用户结果
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    public AdminUserItemResponse getUser(Long userId) {
        List<AdminUserItemResponse> users = jdbcTemplate.query(
                """
                select id, user_code, username, email, display_name,
                       system_role, status, must_change_password, last_login_at
                from users
                where id = ?
                """,
                (resultSet, rowNum) -> new AdminUserItemResponse(
                        resultSet.getLong("id"),
                        resultSet.getString("user_code"),
                        resultSet.getString("username"),
                        resultSet.getString("email"),
                        resultSet.getString("display_name"),
                        SystemRole.valueOf(resultSet.getString("system_role")),
                        UserStatus.valueOf(resultSet.getString("status")),
                        resultSet.getBoolean("must_change_password"),
                        resultSet.getTimestamp("last_login_at") == null
                                ? null
                                : resultSet.getTimestamp("last_login_at").toLocalDateTime()
                ),
                userId
        );
        if (users.isEmpty()) {
            throw new BusinessException("用户不存在");
        }
        return users.getFirst();
    }

    /**
     * 判断 {@code byUsername} 对应的数据是否存在。
     *
     * @param username 用户登录名
     * @return 满足条件时返回 {@code true}，否则返回 {@code false}
     */
    public boolean existsByUsername(String username) {
        return count("select count(*) from users where username = ?", username) > 0;
    }

    /**
     * 判断 {@code byEmail} 对应的数据是否存在。
     *
     * @param email 用户邮箱地址
     * @return 满足条件时返回 {@code true}，否则返回 {@code false}
     */
    public boolean existsByEmail(String email) {
        return count("select count(*) from users where email = ?", email) > 0;
    }

    /**
     * 执行 {@code count} 对应的业务步骤。
     * <p>
     * 实现要点：读取数据库中的当前状态。
     *
     * @param sql 方法参数 {@code sql}
     * @param value 方法参数 {@code value}
     * @return 计算或处理得到的数值结果
     */
    private long count(String sql, String value) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class, value);
        return count == null ? 0 : count;
    }
}
