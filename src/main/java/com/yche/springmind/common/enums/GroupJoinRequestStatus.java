package com.yche.springmind.common.enums;

/**
 * 定义用户加入知识组申请的审批状态。
 *
 * <p>作为跨层共享的状态枚举，限制系统只能使用约定值表达业务状态，避免散落的字符串常量。</p>
 */
public enum GroupJoinRequestStatus {
    PENDING,
    APPROVED,
    REJECTED,
    CANCELED
}
