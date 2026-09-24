package com.yche.springmind.qa.config;

import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

/**
 * 创建查询规划使用的 ChatClient，使规划提示与最终回答提示彼此隔离。
 *
 * <p>位于配置层：集中声明配置项或组装 Spring Bean，使业务代码不直接依赖组件创建细节。</p>
 */
@Configuration
public class QueryPlanningConfiguration {

    /**
     * 创建或配置 {@code queryPlanningUserPromptTemplate} 所需的 Spring 组件。
     * <p>
     * 实现要点：根据问题生成检索计划。
     *
     * @return 查询得到的Planning用户PromptTemplate结果
     */
    @Bean("queryPlanningUserPromptTemplate")
    public PromptTemplate queryPlanningUserPromptTemplate() {
        return PromptTemplate.builder()
                .resource(new ClassPathResource("prompts/query-planning/user.st"))
                .build();
    }
}
