package com.yche.springmind.auth.config;

import com.yche.springmind.auth.service.PasswordHasher;
import com.yche.springmind.common.enums.SystemRole;
import com.yche.springmind.common.enums.UserStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 仅在 dev 环境确保一个可预测的管理员账号存在，避免本地调试还要额外手工开户。
 */
@Component
@Profile("dev")
public class DevAdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevAdminInitializer.class);

    private final JdbcTemplate jdbcTemplate;
    private final PasswordHasher passwordHasher;
    private final String username;
    private final String email;
    private final String displayName;
    private final String password;
    private final String userCode;

    /**
     * 创建并初始化 {@link DevAdminInitializer}，保存该组件运行所需的依赖与配置。
     * <p>
     * 实现要点：使用安全哈希校验或保存密码。
     *
     * @param jdbcTemplate 方法参数 {@code jdbcTemplate}
     * @param passwordHasher 方法参数 {@code passwordHasher}
     * @param username 用户登录名
     * @param email 用户邮箱地址
     * @param displayName 面向界面展示的用户名称
     * @param password 用户提交的明文密码，仅用于本次校验或哈希计算
     * @param userCode 方法参数 {@code userCode}
     */
    public DevAdminInitializer(
            JdbcTemplate jdbcTemplate,
            PasswordHasher passwordHasher,
            @Value("${springmind.dev-admin.username:admin}") String username,
            @Value("${springmind.dev-admin.email:admin@local.springmind.test}") String email,
            @Value("${springmind.dev-admin.display-name:开发环境管理员}") String displayName,
            @Value("${springmind.dev-admin.password:Admin@123456}") String password,
            @Value("${springmind.dev-admin.user-code:admin}") String userCode
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordHasher = passwordHasher;
        this.username = username.trim();
        this.email = email.trim();
        this.displayName = displayName.trim();
        this.password = password;
        this.userCode = userCode.trim();
    }

    /**
     * 在应用生命周期的指定阶段执行初始化或恢复任务。
     * <p>
     * 实现要点：使用事务保证多次数据库操作的一致性；读取数据库中的当前状态；持久化数据库状态变更；使用安全哈希校验或保存密码。
     *
     * @param args 方法参数 {@code args}
     */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Long userId = jdbcTemplate.query(
                "select id from users where username = ? order by id limit 1",
                resultSet -> resultSet.next() ? resultSet.getLong("id") : null,
                username
        );
        if (userId == null) {
            jdbcTemplate.update(
                    """
                    insert into users (
                        user_code, username, email, display_name, password_hash,
                        system_role, status, must_change_password, created_at, updated_at
                    ) values (?, ?, ?, ?, ?, ?, ?, ?, now(), now())
                    """,
                    userCode,
                    username,
                    email,
                    displayName,
                    passwordHasher.hash(password),
                    SystemRole.ADMIN.name(),
                    UserStatus.ACTIVE.name(),
                    false
            );
            log.info("Dev admin initialized. username={}", username);
            return;
        }
        jdbcTemplate.update(
                """
                update users
                set email = ?,
                    display_name = ?,
                    password_hash = ?,
                    system_role = ?,
                    status = ?,
                    must_change_password = false,
                    updated_at = now()
                where id = ?
                """,
                email,
                displayName,
                passwordHasher.hash(password),
                SystemRole.ADMIN.name(),
                UserStatus.ACTIVE.name(),
                userId
        );
        log.info("Dev admin refreshed. username={}", username);
    }
}
