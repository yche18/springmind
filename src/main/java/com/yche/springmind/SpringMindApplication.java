package com.yche.springmind;

import com.yche.springmind.ingestion.transformer.ChunkingProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * SpringMind 后端应用的启动入口，负责引导 Spring Boot 完成组件扫描与自动配置。
 *
 * <p>该类型封装本模块的一项明确职责，避免相关数据和行为散落在其他组件中。</p>
 */
@SpringBootApplication
@EnableConfigurationProperties(ChunkingProperties.class)
@EnableAsync
@EnableRetry
public class SpringMindApplication {

    /**
     * 启动 SpringMind 后端应用，并交由 Spring Boot 完成组件扫描与生命周期管理。
     *
     * @param args 方法参数 {@code args}
     */
    public static void main(String[] args) {
        SpringApplication.run(SpringMindApplication.class, args);
    }
}
