package com.yche.springmind.ingestion.parser.strategy;

import java.io.InputStream;

/**
 * 定义把原始文件内容解析为统一文本的策略接口。
 *
 * <p>位于文档解析阶段：把特定格式的原始文件转换成统一文本，为后续清洗与切片提供输入。</p>
 */
public interface DocumentParser {

    /**
     * 判断当前策略是否支持处理给定输入。
     *
     * @param extension 方法参数 {@code extension}
     * @return 满足条件时返回 {@code true}，否则返回 {@code false}
     */
    boolean supports(String extension);

    /**
     * 将输入内容解析为当前组件约定的结构化结果。
     *
     * @param inputStream 待读取或上传的数据流
     * @return 处理后得到的字符串结果
     */
    String parse(InputStream inputStream);
}
