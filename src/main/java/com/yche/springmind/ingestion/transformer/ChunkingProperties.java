package com.yche.springmind.ingestion.transformer;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 集中管理结构化切片的目标长度、重叠窗口和最小片段等参数。
 *
 * <p>位于文档转换阶段：对解析后的文本进行清洗或结构化切分，为索引建立准备稳定数据。</p>
 */
@ConfigurationProperties(prefix = "ingestion.chunking")
public class ChunkingProperties {

    private int targetTokens = 500;
    private int maxTokens = 800;
    private int overlapTokens = 80;

    /**
     * 创建并初始化 {@link ChunkingProperties}，保存该组件运行所需的依赖与配置。
     */
    public ChunkingProperties() {
    }

    /**
     * 创建并初始化 {@link ChunkingProperties}，保存该组件运行所需的依赖与配置。
     *
     * @param targetTokens 方法参数 {@code targetTokens}
     * @param maxTokens 方法参数 {@code maxTokens}
     * @param overlapTokens 方法参数 {@code overlapTokens}
     */
    public ChunkingProperties(int targetTokens, int maxTokens, int overlapTokens) {
        this.targetTokens = targetTokens;
        this.maxTokens = maxTokens;
        this.overlapTokens = overlapTokens;
    }

    /**
     * 返回 {@code targetTokens} 对应的配置或状态值。
     *
     * @return 计算或处理得到的数值结果
     */
    public int getTargetTokens() {
        return targetTokens;
    }

    /**
     * 更新 {@code targetTokens} 对应的配置或状态值。
     *
     * @param targetTokens 方法参数 {@code targetTokens}
     */
    public void setTargetTokens(int targetTokens) {
        this.targetTokens = targetTokens;
    }

    /**
     * 返回 {@code maxTokens} 对应的配置或状态值。
     *
     * @return 计算或处理得到的数值结果
     */
    public int getMaxTokens() {
        return maxTokens;
    }

    /**
     * 更新 {@code maxTokens} 对应的配置或状态值。
     *
     * @param maxTokens 方法参数 {@code maxTokens}
     */
    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    /**
     * 返回 {@code overlapTokens} 对应的配置或状态值。
     *
     * @return 计算或处理得到的数值结果
     */
    public int getOverlapTokens() {
        return overlapTokens;
    }

    /**
     * 更新 {@code overlapTokens} 对应的配置或状态值。
     *
     * @param overlapTokens 方法参数 {@code overlapTokens}
     */
    public void setOverlapTokens(int overlapTokens) {
        this.overlapTokens = overlapTokens;
    }
}
