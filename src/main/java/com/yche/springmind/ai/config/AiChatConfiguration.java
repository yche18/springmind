package com.yche.springmind.ai.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 声明聊天模型相关配置，使模型地址、密钥和模型名称能够通过外部环境注入。
 *
 * <p>位于配置层：集中声明配置项或组装 Spring Bean，使业务代码不直接依赖组件创建细节。</p>
 */
@Configuration
@EnableConfigurationProperties(AiChatProperties.class)
public class AiChatConfiguration {
}
