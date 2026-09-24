package com.yche.springmind.common.exception;

/**
 * 表示用户身份有效但无权执行当前操作的授权失败。
 *
 * <p>用于把业务失败表达为明确的异常语义，并由全局异常处理器转换为统一 API 响应。</p>
 */
public class ForbiddenException extends RuntimeException {

    /**
     * 创建并初始化 {@link ForbiddenException}，保存该组件运行所需的依赖与配置。
     * <p>
     * 实现要点：校验群组成员关系和角色权限。
     *
     * @param message 方法参数 {@code message}
     */
    public ForbiddenException(String message) {
        super(message);
    }
}
