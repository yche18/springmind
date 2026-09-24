package com.yche.springmind.ingestion.parser.strategy;

import com.yche.springmind.common.exception.BusinessException;
import com.yche.springmind.ingestion.parser.support.TextDecodingSupport;

import java.io.InputStream;

/**
 * 读取 Markdown 文档并保留可用于结构切分的标题与正文信息。
 *
 * <p>位于文档解析阶段：把特定格式的原始文件转换成统一文本，为后续清洗与切片提供输入。</p>
 */
public class MdDocumentParser implements DocumentParser {

    /**
     * 判断当前策略是否支持处理给定输入。
     *
     * @param extension 方法参数 {@code extension}
     * @return 满足条件时返回 {@code true}，否则返回 {@code false}
     */
    @Override
    public boolean supports(String extension) {
        return "md".equalsIgnoreCase(extension);
    }

    /**
     * 将输入内容解析为当前组件约定的结构化结果。
     *
     * @param inputStream 待读取或上传的数据流
     * @return 处理后得到的字符串结果
     */
    @Override
    public String parse(InputStream inputStream) {
        return stripMarkdown(TextDecodingSupport.decode(inputStream, "Markdown 文档解析失败")).trim();
    }

    /**
     * 在文档处理链路中执行 {@code stripMarkdown}。
     *
     * @param content 方法参数 {@code content}
     * @return 处理后得到的字符串结果
     */
    private String stripMarkdown(String content) {
        String plainText = content.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("```[\\s\\S]*?```", " ")
                .replaceAll("`([^`]+)`", "$1")
                .replaceAll("!\\[[^\\]]*]\\([^)]*\\)", " ")
                .replaceAll("\\[([^\\]]+)]\\([^)]*\\)", "$1")
                .replaceAll("(?m)^#{1,6}\\s*", "")
                .replaceAll("(?m)^>\\s*", "")
                .replaceAll("(?m)^[-*+]\\s+", "")
                .replaceAll("(?m)^\\d+\\.\\s+", "")
                .replaceAll("(\\*\\*|__|[*_~])", "");
        return plainText.replaceAll("[ \\t]+", " ").replaceAll("\\n{3,}", "\n\n");
    }
}
