package com.yche.springmind.auth.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 集中管理访问令牌、刷新令牌和认证 Cookie 的有效期及安全参数。
 *
 * <p>位于配置层：集中声明配置项或组装 Spring Bean，使业务代码不直接依赖组件创建细节。</p>
 */
@Validated
@ConfigurationProperties(prefix = "springmind.auth")
public class AuthProperties {

    @NotBlank
    private String issuer = "springmind";

    @Min(1)
    private int accessTokenExpireMinutes = 30;

    @Min(1)
    private int refreshTokenExpireDays = 14;

    @NotBlank
    private String jwtSecret;

    @NotBlank
    private String refreshCookieName = "SPRINGMIND_REFRESH_TOKEN";

    private boolean refreshCookieSecure = true;

    /**
     * 返回 {@code issuer} 对应的配置或状态值。
     *
     * @return 处理后得到的字符串结果
     */
    public String getIssuer() {
        return issuer;
    }

    /**
     * 更新 {@code issuer} 对应的配置或状态值。
     *
     * @param issuer 方法参数 {@code issuer}
     */
    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    /**
     * 返回 {@code accessTokenExpireMinutes} 对应的配置或状态值。
     *
     * @return 计算或处理得到的数值结果
     */
    public int getAccessTokenExpireMinutes() {
        return accessTokenExpireMinutes;
    }

    /**
     * 更新 {@code accessTokenExpireMinutes} 对应的配置或状态值。
     *
     * @param accessTokenExpireMinutes 方法参数 {@code accessTokenExpireMinutes}
     */
    public void setAccessTokenExpireMinutes(int accessTokenExpireMinutes) {
        this.accessTokenExpireMinutes = accessTokenExpireMinutes;
    }

    /**
     * 返回 {@code refreshTokenExpireDays} 对应的配置或状态值。
     *
     * @return 计算或处理得到的数值结果
     */
    public int getRefreshTokenExpireDays() {
        return refreshTokenExpireDays;
    }

    /**
     * 更新 {@code refreshTokenExpireDays} 对应的配置或状态值。
     *
     * @param refreshTokenExpireDays 方法参数 {@code refreshTokenExpireDays}
     */
    public void setRefreshTokenExpireDays(int refreshTokenExpireDays) {
        this.refreshTokenExpireDays = refreshTokenExpireDays;
    }

    /**
     * 返回 {@code jwtSecret} 对应的配置或状态值。
     *
     * @return 处理后得到的字符串结果
     */
    public String getJwtSecret() {
        return jwtSecret;
    }

    /**
     * 更新 {@code jwtSecret} 对应的配置或状态值。
     *
     * @param jwtSecret 方法参数 {@code jwtSecret}
     */
    public void setJwtSecret(String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    /**
     * 返回 {@code refreshCookieName} 对应的配置或状态值。
     *
     * @return 处理后得到的字符串结果
     */
    public String getRefreshCookieName() {
        return refreshCookieName;
    }

    /**
     * 更新 {@code refreshCookieName} 对应的配置或状态值。
     *
     * @param refreshCookieName 方法参数 {@code refreshCookieName}
     */
    public void setRefreshCookieName(String refreshCookieName) {
        this.refreshCookieName = refreshCookieName;
    }

    /**
     * 判断当前数据是否满足 {@code refreshCookieSecure} 条件。
     *
     * @return 满足条件时返回 {@code true}，否则返回 {@code false}
     */
    public boolean isRefreshCookieSecure() {
        return refreshCookieSecure;
    }

    /**
     * 更新 {@code refreshCookieSecure} 对应的配置或状态值。
     *
     * @param refreshCookieSecure 方法参数 {@code refreshCookieSecure}
     */
    public void setRefreshCookieSecure(boolean refreshCookieSecure) {
        this.refreshCookieSecure = refreshCookieSecure;
    }
}
