package com.yche.springmind.common.exception;

/**
 * 表示可预期、可向客户端说明的通用业务失败。
 *
 * <p>用于把业务失败表达为明确的异常语义，并由全局异常处理器转换为统一 API 响应。</p>
 */
public class BusinessException extends RuntimeException {

    /**
     * 创建并初始化 {@link BusinessException}，保存该组件运行所需的依赖与配置。
     *
     * @param message 方法参数 {@code message}
     */
    public BusinessException(String message) {
        super(message);
    }

    /**
     * 创建并初始化 {@link BusinessException}，保存该组件运行所需的依赖与配置。
     *
     * @param message 方法参数 {@code message}
     * @param cause 方法参数 {@code cause}
     */
    public BusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}
