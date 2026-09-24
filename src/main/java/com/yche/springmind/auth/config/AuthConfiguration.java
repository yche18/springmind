package com.yche.springmind.auth.config;

import com.yche.springmind.common.exception.BusinessException;
import com.yche.springmind.auth.service.PasswordHasher;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.time.Clock;

/**
 * 组装密码哈希等认证基础组件，隔离安全库的具体实现。
 *
 * <p>位于配置层：集中声明配置项或组装 Spring Bean，使业务代码不直接依赖组件创建细节。</p>
 */
@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class AuthConfiguration {

    private static final int BCRYPT_MAX_INPUT_BYTES = 72;

    /**
     * 创建或配置 {@code authClock} 所需的 Spring 组件。
     *
     * @return 方法执行结果，具体结构由返回类型 {@code Clock} 表示
     */
    @Bean
    Clock authClock() {
        return Clock.systemUTC();
    }

    /**
     * 创建或配置 {@code passwordHasher} 所需的 Spring 组件。
     * <p>
     * 实现要点：使用安全哈希校验或保存密码；先校验输入、状态或业务边界。
     *
     * @return 方法执行结果，具体结构由返回类型 {@code PasswordHasher} 表示
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    @Bean
    PasswordHasher passwordHasher() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        return new PasswordHasher() {
            /**
             * 判断当前对象是否具有 {@code h} 特征。
             *
             * @param rawPassword 尚未哈希的明文密码
             * @return 处理后得到的字符串结果
             */
            @Override
            public String hash(String rawPassword) {
                validateInputLength(rawPassword);
                return encoder.encode(rawPassword);
            }

            /**
             * 创建或配置 {@code matches} 所需的 Spring 组件。
             * <p>
             * 实现要点：先校验输入、状态或业务边界。
             *
             * @param rawPassword 尚未哈希的明文密码
             * @param passwordHash 方法参数 {@code passwordHash}
             * @return 满足条件时返回 {@code true}，否则返回 {@code false}
             */
            @Override
            public boolean matches(String rawPassword, String passwordHash) {
                validateInputLength(rawPassword);
                return encoder.matches(rawPassword, passwordHash);
            }

            /**
             * 创建或配置 {@code validateInputLength} 所需的 Spring 组件。
             * <p>
             * 实现要点：先校验输入、状态或业务边界。
             *
             * @param rawValue 方法参数 {@code rawValue}
             * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
             */
            private void validateInputLength(String rawValue) {
                if (rawValue == null || rawValue.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_INPUT_BYTES) {
                    throw new BusinessException("密码长度超过安全上限，请控制在 72 字节以内");
                }
            }
        };
    }
}
