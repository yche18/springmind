package com.yche.springmind.common.enums;

/**
 * 定义知识组邀请从待处理到接受、拒绝或失效的状态。
 *
 * <p>作为跨层共享的状态枚举，限制系统只能使用约定值表达业务状态，避免散落的字符串常量。</p>
 */
public enum GroupInvitationStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
    CANCELED
}
