package com.yche.springmind.groupmembership.model.vo;

import java.time.LocalDateTime;

/**
 * 表示当前用户提交的一条知识组加入申请。
 *
 * <p>这是接口输出视图，用于向前端稳定地暴露所需字段，避免直接返回数据库实体。</p>
 */
public record MyJoinRequestVO(
        Long requestId,
        Long groupId,
        String groupCode,
        String groupName,
        String status,
        LocalDateTime createdAt,
        LocalDateTime decidedAt
) {
}
