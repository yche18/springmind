package com.yche.springmind.groupmembership.model.vo;

import java.time.LocalDateTime;

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
