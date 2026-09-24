package com.yche.springmind.auth.security;

import com.yche.springmind.auth.config.AuthProperties;
import com.yche.springmind.auth.service.PasswordHasher;
import com.yche.springmind.auth.service.RefreshTokenRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 管理刷新令牌的签发、哈希存储、轮换、撤销与过期校验。
 *
 * <p>位于认证安全边界：负责令牌、Cookie 或安全上下文处理，为业务服务提供可信的用户身份。</p>
 */
@Service
public class RefreshTokenService {

    private static final String TOKEN_SEPARATOR = ".";
    private static final int SECRET_BYTES = 24;
    private static final RowMapper<RefreshTokenRecord> TOKEN_ROW_MAPPER = (resultSet, rowNum) -> new RefreshTokenRecord(
            resultSet.getLong("id"),
            resultSet.getLong("user_id"),
            resultSet.getString("token_id"),
            resultSet.getString("token_hash"),
            resultSet.getTimestamp("expires_at").toLocalDateTime(),
            Optional.ofNullable(resultSet.getTimestamp("revoked_at"))
                    .map(timestamp -> timestamp.toLocalDateTime())
                    .orElse(null)
    );

    private final JdbcTemplate jdbcTemplate;
    private final PasswordHasher passwordHasher;
    private final AuthProperties authProperties;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 创建并初始化 {@link RefreshTokenService}，保存该组件运行所需的依赖与配置。
     * <p>
     * 实现要点：使用安全哈希校验或保存密码。
     *
     * @param jdbcTemplate 方法参数 {@code jdbcTemplate}
     * @param passwordHasher 方法参数 {@code passwordHasher}
     * @param authProperties 方法参数 {@code authProperties}
     * @param clock 用于生成可测试时间的时钟
     */
    public RefreshTokenService(
            JdbcTemplate jdbcTemplate,
            PasswordHasher passwordHasher,
            AuthProperties authProperties,
            Clock clock
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordHasher = passwordHasher;
        this.authProperties = authProperties;
        this.clock = clock;
    }

    /**
     * 判断当前数据是否满足 {@code sueToken} 条件。
     *
     * @param userId 用户唯一标识
     * @return 方法执行结果，具体结构由返回类型 {@code IssuedRefreshToken} 表示
     */
    public IssuedRefreshToken issueToken(Long userId) {
        LocalDateTime now = LocalDateTime.now(clock);
        String tokenId = UUID.randomUUID().toString().replace("-", "");
        String refreshToken = tokenId + TOKEN_SEPARATOR + newTokenSecret();
        LocalDateTime expiresAt = now.plusDays(authProperties.getRefreshTokenExpireDays());
        Long id = jdbcTemplate.queryForObject(
                """
                insert into user_refresh_tokens (user_id, token_id, token_hash, expires_at, created_at)
                values (?, ?, ?, ?, ?)
                returning id
                """,
                Long.class,
                userId,
                tokenId,
                passwordHasher.hash(refreshToken),
                expiresAt,
                now
        );
        return new IssuedRefreshToken(refreshToken, findById(id).orElseThrow());
    }

    /**
     * 完成 {@code findActiveToken} 对应的处理。
     * <p>
     * 实现要点：读取数据库中的当前状态；使用安全哈希校验或保存密码。
     *
     * @param refreshToken 客户端提交的刷新令牌
     * @return 可能存在的查询结果；不存在时返回空 {@code Optional}
     */
    public Optional<RefreshTokenRecord> findActiveToken(String refreshToken) {
        Optional<ParsedRefreshToken> parsedToken = parseToken(refreshToken);
        if (parsedToken.isEmpty()) {
            return Optional.empty();
        }
        Optional<RefreshTokenRecord> storedToken = findByTokenId(parsedToken.get().tokenId());
        if (storedToken.isEmpty()) {
            return Optional.empty();
        }
        RefreshTokenRecord record = storedToken.get();
        if (!passwordHasher.matches(refreshToken, record.tokenHash())) {
            return Optional.empty();
        }
        if (!record.isActive(LocalDateTime.now(clock))) {
            return Optional.empty();
        }
        return Optional.of(record);
    }

    /**
     * 完成 {@code revokeActiveTokens} 对应的处理。
     * <p>
     * 实现要点：持久化数据库状态变更。
     *
     * @param userId 用户唯一标识
     */
    public void revokeActiveTokens(Long userId) {
        LocalDateTime now = LocalDateTime.now(clock);
        jdbcTemplate.update(
                """
                update user_refresh_tokens
                set revoked_at = ?
                where user_id = ?
                  and revoked_at is null
                  and expires_at > ?
                """,
                now,
                userId,
                now
        );
    }

    /**
     * 完成 {@code revokeToken} 对应的处理。
     * <p>
     * 实现要点：持久化数据库状态变更。
     *
     * @param refreshToken 客户端提交的刷新令牌
     */
    public void revokeToken(String refreshToken) {
        Optional<RefreshTokenRecord> activeToken = findActiveToken(refreshToken);
        if (activeToken.isEmpty()) {
            return;
        }
        jdbcTemplate.update(
                "update user_refresh_tokens set revoked_at = ? where id = ?",
                LocalDateTime.now(clock),
                activeToken.get().id()
        );
    }

    /**
     * 完成 {@code countActiveTokens} 对应的处理。
     * <p>
     * 实现要点：读取数据库中的当前状态。
     *
     * @param userId 用户唯一标识
     * @return 计算或处理得到的数值结果
     */
    public long countActiveTokens(Long userId) {
        Long count = jdbcTemplate.queryForObject(
                """
                select count(*)
                from user_refresh_tokens
                where user_id = ?
                  and revoked_at is null
                  and expires_at > ?
                """,
                Long.class,
                userId,
                LocalDateTime.now(clock)
        );
        return count == null ? 0 : count;
    }

    /**
     * 完成 {@code findById} 对应的处理。
     * <p>
     * 实现要点：读取数据库中的当前状态。
     *
     * @param id 方法参数 {@code id}
     * @return 可能存在的查询结果；不存在时返回空 {@code Optional}
     */
    private Optional<RefreshTokenRecord> findById(Long id) {
        List<RefreshTokenRecord> tokens = jdbcTemplate.query(
                """
                select id, user_id, token_id, token_hash, expires_at, revoked_at
                from user_refresh_tokens
                where id = ?
                """,
                TOKEN_ROW_MAPPER,
                id
        );
        return tokens.stream().findFirst();
    }

    /**
     * 完成 {@code findByTokenId} 对应的处理。
     * <p>
     * 实现要点：读取数据库中的当前状态。
     *
     * @param tokenId 令牌唯一标识
     * @return 可能存在的查询结果；不存在时返回空 {@code Optional}
     */
    private Optional<RefreshTokenRecord> findByTokenId(String tokenId) {
        List<RefreshTokenRecord> tokens = jdbcTemplate.query(
                """
                select id, user_id, token_id, token_hash, expires_at, revoked_at
                from user_refresh_tokens
                where token_id = ?
                """,
                TOKEN_ROW_MAPPER,
                tokenId
        );
        return tokens.stream().findFirst();
    }

    /**
     * 完成 {@code parseToken} 对应的处理。
     *
     * @param refreshToken 客户端提交的刷新令牌
     * @return 可能存在的查询结果；不存在时返回空 {@code Optional}
     */
    private Optional<ParsedRefreshToken> parseToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return Optional.empty();
        }
        String[] segments = refreshToken.trim().split("\\Q" + TOKEN_SEPARATOR + "\\E", 2);
        if (segments.length != 2 || segments[0].isBlank() || segments[1].isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new ParsedRefreshToken(segments[0], segments[1]));
    }

    /**
     * 完成 {@code newTokenSecret} 对应的处理。
     *
     * @return 处理后得到的字符串结果
     */
    private String newTokenSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * 保存刷新令牌拆分得到的公开标识和待校验秘密值。
     *
     * <p>仅在 {@code RefreshTokenService} 的实现过程中使用，用不可变数据结构收拢中间结果，避免参数和值的含义混淆。</p>
     */
    private record ParsedRefreshToken(String tokenId, String secret) {
    }

    /**
     * 同时返回客户端可用的刷新令牌和对应持久化记录。
     *
     * <p>仅在 {@code RefreshTokenService} 的实现过程中使用，用不可变数据结构收拢中间结果，避免参数和值的含义混淆。</p>
     */
    public record IssuedRefreshToken(String refreshToken, RefreshTokenRecord record) {
    }
}
