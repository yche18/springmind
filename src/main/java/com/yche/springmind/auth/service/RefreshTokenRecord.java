package com.yche.springmind.auth.service;

import java.time.LocalDateTime;

/**
 * 表示数据库中的刷新令牌记录及其生命周期信息。
 *
 * <p>位于业务服务层：编排领域操作和基础设施调用，并集中维护事务、权限校验及失败处理边界。</p>
 */
public record RefreshTokenRecord(
        Long id,
        Long userId,
        String tokenId,
        String tokenHash,
        LocalDateTime expiresAt,
        LocalDateTime revokedAt
) {

    /**
     * 判断当前数据是否满足 {@code active} 条件。
     *
     * @param now 方法参数 {@code now}
     * @return 满足条件时返回 {@code true}，否则返回 {@code false}
     */
    public boolean isActive(LocalDateTime now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }
}
