package com.yche.springmind.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 承载 OpenAI-compatible 聊天模型的连接地址、密钥、模型名及请求参数。
 *
 * <p>位于配置层：集中声明配置项或组装 Spring Bean，使业务代码不直接依赖组件创建细节。</p>
 */
@ConfigurationProperties(prefix = "app.ai.chat")
public class AiChatProperties {

    private String baseUrl = "https://api.openai.com/v1";
    private String apiKey;
    private String model = "gpt-4o-mini";

    /**
     * 返回 {@code baseUrl} 对应的配置或状态值。
     *
     * @return 处理后得到的字符串结果
     */
    public String getBaseUrl() {
        return baseUrl;
    }

    /**
     * 更新 {@code baseUrl} 对应的配置或状态值。
     *
     * @param baseUrl 方法参数 {@code baseUrl}
     */
    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    /**
     * 返回 {@code apiKey} 对应的配置或状态值。
     *
     * @return 处理后得到的字符串结果
     */
    public String getApiKey() {
        return apiKey;
    }

    /**
     * 更新 {@code apiKey} 对应的配置或状态值。
     *
     * @param apiKey 方法参数 {@code apiKey}
     */
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    /**
     * 返回 {@code model} 对应的配置或状态值。
     *
     * @return 处理后得到的字符串结果
     */
    public String getModel() {
        return model;
    }

    /**
     * 更新 {@code model} 对应的配置或状态值。
     *
     * @param model 方法参数 {@code model}
     */
    public void setModel(String model) {
        this.model = model;
    }
}
