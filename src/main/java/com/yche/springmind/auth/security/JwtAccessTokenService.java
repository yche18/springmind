package com.yche.springmind.auth.security;

import com.yche.springmind.auth.config.AuthProperties;
import com.yche.springmind.common.enums.SystemRole;
import com.yche.springmind.common.exception.BusinessException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * 签发并校验短期 JWT 访问令牌，将合法声明转换为系统可识别的用户身份。
 *
 * <p>位于认证安全边界：负责令牌、Cookie 或安全上下文处理，为业务服务提供可信的用户身份。</p>
 */
@Service
public class JwtAccessTokenService {

    private static final String USER_ID_CLAIM = "uid";
    private static final String DISPLAY_NAME_CLAIM = "displayName";
    private static final String SYSTEM_ROLE_CLAIM = "systemRole";
    private static final String MUST_CHANGE_PASSWORD_CLAIM = "mustChangePassword";
    private static final int MIN_SECRET_LENGTH = 32;

    private final AuthProperties authProperties;
    private final Clock clock;
    private final SecretKey signingKey;

    /**
     * 创建并初始化 {@link JwtAccessTokenService}，保存该组件运行所需的依赖与配置。
     *
     * @param authProperties 方法参数 {@code authProperties}
     * @param clock 用于生成可测试时间的时钟
     */
    public JwtAccessTokenService(AuthProperties authProperties, Clock clock) {
        this.authProperties = authProperties;
        this.clock = clock;
        this.signingKey = buildSigningKey(authProperties.getJwtSecret());
    }

    /**
     * 判断当前数据是否满足 {@code sueToken} 条件。
     *
     * @param subject 方法参数 {@code subject}
     * @return 处理后得到的字符串结果
     */
    public String issueToken(TokenSubject subject) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(authProperties.getAccessTokenExpireMinutes(), ChronoUnit.MINUTES);
        return Jwts.builder()
                .issuer(authProperties.getIssuer())
                .subject(subject.userCode())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .claim(USER_ID_CLAIM, subject.userId())
                .claim(DISPLAY_NAME_CLAIM, subject.displayName())
                .claim(SYSTEM_ROLE_CLAIM, subject.systemRole().name())
                .claim(MUST_CHANGE_PASSWORD_CLAIM, subject.mustChangePassword())
                .signWith(signingKey)
                .compact();
    }

    /**
     * 将输入内容解析为当前组件约定的结构化结果。
     * <p>
     * 实现要点：先校验输入、状态或业务边界；捕获依赖异常并转换、记录或执行降级策略。
     *
     * @param token 方法参数 {@code token}
     * @return 方法执行结果，具体结构由返回类型 {@code AccessTokenClaims} 表示
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    public AccessTokenClaims parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            validateClaims(claims);
            return new AccessTokenClaims(
                    claims.get(USER_ID_CLAIM, Long.class),
                    claims.getSubject(),
                    claims.get(DISPLAY_NAME_CLAIM, String.class),
                    SystemRole.valueOf(claims.get(SYSTEM_ROLE_CLAIM, String.class)),
                    claims.get(MUST_CHANGE_PASSWORD_CLAIM, Boolean.class),
                    claims.getIssuedAt().toInstant(),
                    claims.getExpiration().toInstant()
            );
        } catch (BusinessException exception) {
            throw exception;
        } catch (JwtException | IllegalArgumentException exception) {
            throw new BusinessException(JwtAuthenticationFilter.INVALID_ACCESS_TOKEN_MESSAGE, exception);
        } catch (RuntimeException exception) {
            throw new BusinessException(JwtAuthenticationFilter.INVALID_ACCESS_TOKEN_MESSAGE, exception);
        }
    }

    /**
     * 完成 {@code validateClaims} 对应的处理。
     * <p>
     * 实现要点：先校验输入、状态或业务边界。
     *
     * @param claims 方法参数 {@code claims}
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    private void validateClaims(Claims claims) {
        if (!authProperties.getIssuer().equals(claims.getIssuer())) {
            throw new BusinessException(JwtAuthenticationFilter.INVALID_ACCESS_TOKEN_MESSAGE);
        }
        if (claims.getSubject() == null
                || claims.get(USER_ID_CLAIM, Long.class) == null
                || claims.get(DISPLAY_NAME_CLAIM, String.class) == null
                || claims.get(SYSTEM_ROLE_CLAIM, String.class) == null
                || claims.get(MUST_CHANGE_PASSWORD_CLAIM, Boolean.class) == null
                || claims.getIssuedAt() == null
                || claims.getExpiration() == null) {
            throw new BusinessException(JwtAuthenticationFilter.INVALID_ACCESS_TOKEN_MESSAGE);
        }
    }

    /**
     * 完成 {@code buildSigningKey} 对应的处理。
     *
     * @param jwtSecret 方法参数 {@code jwtSecret}
     * @return 方法执行结果，具体结构由返回类型 {@code SecretKey} 表示
     * @throws IllegalStateException 当输入、状态或依赖不满足方法约束时抛出
     */
    private SecretKey buildSigningKey(String jwtSecret) {
        byte[] secretBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < MIN_SECRET_LENGTH) {
            throw new IllegalStateException("JWT secret 至少需要 32 字节");
        }
        return Keys.hmacShaKeyFor(secretBytes);
    }

    /**
     * 表示签发访问令牌时写入的用户主体和角色信息。
     *
     * <p>仅在 {@code JwtAccessTokenService} 的实现过程中使用，用不可变数据结构收拢中间结果，避免参数和值的含义混淆。</p>
     */
    public record TokenSubject(
            Long userId,
            String userCode,
            String displayName,
            SystemRole systemRole,
            boolean mustChangePassword
    ) {
    }

    /**
     * 表示从合法访问令牌中解析出的身份声明和过期时间。
     *
     * <p>仅在 {@code JwtAccessTokenService} 的实现过程中使用，用不可变数据结构收拢中间结果，避免参数和值的含义混淆。</p>
     */
    public record AccessTokenClaims(
            Long userId,
            String userCode,
            String displayName,
            SystemRole systemRole,
            boolean mustChangePassword,
            Instant issuedAt,
            Instant expiresAt
    ) {
    }
}
