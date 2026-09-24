package com.yche.springmind.common.enums;

/**
 * 定义文档从上传到入库完成或失败的生命周期状态。
 *
 * <p>作为跨层共享的状态枚举，限制系统只能使用约定值表达业务状态，避免散落的字符串常量。</p>
 */
public enum DocumentStatus {
    UPLOADED,
    PROCESSING,
    READY,
    FAILED
}
