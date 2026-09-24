package com.yche.springmind.common.api;

/**
 * 定义所有 REST 接口统一使用的响应结构。
 *
 * <p>作为所有 HTTP 接口共享的响应外壳，统一成功标记、数据和提示信息的返回格式。</p>
 */
public record ApiResponse<T>(boolean success, T data, String message) {

    /**
     * 完成 {@code success} 对应的处理。
     *
     * @param <T> 方法使用的泛型类型
     * @param data 方法参数 {@code data}
     * @return 方法执行结果，具体结构由返回类型 {@code ApiResponse&lt;T&gt;} 表示
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null);
    }

    /**
     * 判断当前数据是否满足 {@code success} 条件。
     *
     * @return 满足条件时返回 {@code true}，否则返回 {@code false}
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * 返回 {@code data} 对应的配置或状态值。
     *
     * @return 查询得到的Data结果
     */
    public T getData() {
        return data;
    }

    /**
     * 返回 {@code message} 对应的配置或状态值。
     *
     * @return 处理后得到的字符串结果
     */
    public String getMessage() {
        return message;
    }
}
