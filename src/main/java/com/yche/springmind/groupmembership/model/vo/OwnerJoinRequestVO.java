package com.yche.springmind.groupmembership.model.vo;

import java.time.LocalDateTime;

/**
 * 表示知识组所有者需要审批的一条加入申请。
 *
 * <p>这是接口输出视图，用于向前端稳定地暴露所需字段，避免直接返回数据库实体。</p>
 */
public record OwnerJoinRequestVO(
        Long requestId,
        Long groupId,
        Long applicantUserId,
        String applicantUserCode,
        String applicantDisplayName,
        String status,
        LocalDateTime createdAt
) {
}
