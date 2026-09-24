package com.yche.springmind.common.enums;

/**
 * 定义用户在整个平台范围内拥有的系统角色。
 *
 * <p>作为跨层共享的状态枚举，限制系统只能使用约定值表达业务状态，避免散落的字符串常量。</p>
 */
public enum SystemRole {
    ADMIN,
    USER
}
