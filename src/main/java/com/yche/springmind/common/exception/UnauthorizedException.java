package com.yche.springmind.common.exception;

/**
 * 表示请求缺少有效登录身份或认证信息已经失效。
 *
 * <p>用于把业务失败表达为明确的异常语义，并由全局异常处理器转换为统一 API 响应。</p>
 */
public class UnauthorizedException extends RuntimeException {

    /**
     * 创建并初始化 {@link UnauthorizedException}，保存该组件运行所需的依赖与配置。
     *
     * @param message 方法参数 {@code message}
     */
    public UnauthorizedException(String message) {
        super(message);
    }
}
