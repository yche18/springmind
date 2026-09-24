package com.yche.springmind.ingestion.parser.strategy;

import com.yche.springmind.common.exception.BusinessException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.io.InputStream;

/**
 * 提取 PDF 各页文本并组合成后续清洗、切片可处理的内容。
 *
 * <p>位于文档解析阶段：把特定格式的原始文件转换成统一文本，为后续清洗与切片提供输入。</p>
 */
public class PdfDocumentParser implements DocumentParser {

    /**
     * 判断当前策略是否支持处理给定输入。
     *
     * @param extension 方法参数 {@code extension}
     * @return 满足条件时返回 {@code true}，否则返回 {@code false}
     */
    @Override
    public boolean supports(String extension) {
        return "pdf".equalsIgnoreCase(extension);
    }

    /**
     * 将输入内容解析为当前组件约定的结构化结果。
     * <p>
     * 实现要点：捕获依赖异常并转换、记录或执行降级策略。
     *
     * @param inputStream 待读取或上传的数据流
     * @return 处理后得到的字符串结果
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    @Override
    public String parse(InputStream inputStream) {
        try (PDDocument document = PDDocument.load(inputStream)) {
            String text = new PDFTextStripper().getText(document);
            return text.replace("\r\n", "\n").replace('\r', '\n').trim();
        } catch (IOException exception) {
            throw new BusinessException("PDF 文档解析失败", exception);
        }
    }
}
