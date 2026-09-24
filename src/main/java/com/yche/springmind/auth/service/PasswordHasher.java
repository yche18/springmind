package com.yche.springmind.auth.service;

/**
 * 定义密码哈希与匹配能力，避免业务层直接绑定具体加密算法。
 *
 * <p>位于业务服务层：编排领域操作和基础设施调用，并集中维护事务、权限校验及失败处理边界。</p>
 */
public interface PasswordHasher {

    /**
     * 判断当前对象是否具有 {@code h} 特征。
     *
     * @param rawPassword 尚未哈希的明文密码
     * @return 处理后得到的字符串结果
     */
    String hash(String rawPassword);

    /**
     * 执行 {@code matches} 对应的业务步骤。
     *
     * @param rawPassword 尚未哈希的明文密码
     * @param passwordHash 方法参数 {@code passwordHash}
     * @return 满足条件时返回 {@code true}，否则返回 {@code false}
     */
    boolean matches(String rawPassword, String passwordHash);
}
