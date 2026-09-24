package com.yche.springmind.ingestion.parser.strategy;

import com.yche.springmind.common.exception.BusinessException;
import com.yche.springmind.ingestion.parser.support.TextDecodingSupport;

import java.io.InputStream;

/**
 * 按可靠的字符编码策略读取纯文本文档。
 *
 * <p>位于文档解析阶段：把特定格式的原始文件转换成统一文本，为后续清洗与切片提供输入。</p>
 */
public class TxtDocumentParser implements DocumentParser {

    /**
     * 判断当前策略是否支持处理给定输入。
     *
     * @param extension 方法参数 {@code extension}
     * @return 满足条件时返回 {@code true}，否则返回 {@code false}
     */
    @Override
    public boolean supports(String extension) {
        return "txt".equalsIgnoreCase(extension);
    }

    /**
     * 将输入内容解析为当前组件约定的结构化结果。
     * <p>
     * 实现要点：先校验输入、状态或业务边界。
     *
     * @param inputStream 待读取或上传的数据流
     * @return 处理后得到的字符串结果
     */
    @Override
    public String parse(InputStream inputStream) {
        return normalizeText(TextDecodingSupport.decode(inputStream, "TXT 文档解析失败"));
    }

    /**
     * 在文档处理链路中执行 {@code normalizeText}。
     * <p>
     * 实现要点：先校验输入、状态或业务边界。
     *
     * @param content 方法参数 {@code content}
     * @return 处理后得到的字符串结果
     */
    private String normalizeText(String content) {
        return content.replace("\r\n", "\n").replace('\r', '\n').trim();
    }
}
