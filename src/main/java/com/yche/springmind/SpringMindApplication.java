package com.yche.springmind;

import com.yche.springmind.ingestion.transformer.ChunkingProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableConfigurationProperties(ChunkingProperties.class)
@EnableAsync
@EnableRetry
public class SpringMindApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringMindApplication.class, args);
    }
}
