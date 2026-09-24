package com.yche.springmind.ingestion.parser.strategy;

import com.yche.springmind.common.exception.BusinessException;
import org.apache.poi.openxml4j.exceptions.NotOfficeXmlFileException;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipException;

/**
 * 使用 DOCX 解析能力提取 Word 文档正文并转换为统一文本。
 *
 * <p>位于文档解析阶段：把特定格式的原始文件转换成统一文本，为后续清洗与切片提供输入。</p>
 */
public class DocxDocumentParser implements DocumentParser {

    /**
     * 判断当前策略是否支持处理给定输入。
     *
     * @param extension 方法参数 {@code extension}
     * @return 满足条件时返回 {@code true}，否则返回 {@code false}
     */
    @Override
    public boolean supports(String extension) {
        return "docx".equalsIgnoreCase(extension);
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
        try (XWPFDocument document = new XWPFDocument(inputStream);
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText().replace("\r\n", "\n").replace('\r', '\n').trim();
        } catch (NotOfficeXmlFileException | ZipException exception) {
            throw new BusinessException("无效 DOCX 文件：文件内容不是合法的 Word 文档", exception);
        } catch (IOException exception) {
            throw new BusinessException("DOCX 文档解析失败", exception);
        }
    }
}
