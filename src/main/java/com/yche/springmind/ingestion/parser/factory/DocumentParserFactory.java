package com.yche.springmind.ingestion.parser.factory;

import com.yche.springmind.common.exception.BusinessException;
import com.yche.springmind.ingestion.parser.strategy.DocxDocumentParser;
import com.yche.springmind.ingestion.parser.strategy.DocumentParser;
import com.yche.springmind.ingestion.parser.strategy.MdDocumentParser;
import com.yche.springmind.ingestion.parser.strategy.PdfDocumentParser;
import com.yche.springmind.ingestion.parser.strategy.TxtDocumentParser;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 根据文件扩展名选择合适的解析策略，并拒绝系统不支持的格式。
 *
 * <p>位于文档解析阶段：把特定格式的原始文件转换成统一文本，为后续清洗与切片提供输入。</p>
 */
@Component
public class DocumentParserFactory {

    private final Map<String, DocumentParser> parserByExtension = new LinkedHashMap<>();

    /**
     * 创建并初始化 {@link DocumentParserFactory}，保存该组件运行所需的依赖与配置。
     */
    public DocumentParserFactory() {
        this(List.of(
                new TxtDocumentParser(),
                new MdDocumentParser(),
                new PdfDocumentParser(),
                new DocxDocumentParser()
        ));
    }

    /**
     * 创建并初始化 {@link DocumentParserFactory}，保存该组件运行所需的依赖与配置。
     *
     * @param parsers 方法参数 {@code parsers}
     */
    public DocumentParserFactory(List<DocumentParser> parsers) {
        for (DocumentParser parser : parsers) {
            register("txt", parser);
            register("md", parser);
            register("pdf", parser);
            register("docx", parser);
        }
    }

    /**
     * 返回 {@code parser} 对应的配置或状态值。
     *
     * @param extension 方法参数 {@code extension}
     * @return 查询得到的Parser结果
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    public DocumentParser getParser(String extension) {
        String normalizedExtension = normalizeExtension(extension);
        DocumentParser parser = parserByExtension.get(normalizedExtension);
        if (parser == null) {
            throw new BusinessException("不支持的文档类型: " + normalizedExtension);
        }
        return parser;
    }

    /**
     * 完成用户自助注册；密码由用户本人设置，因此新账号无需执行首次改密。
     *
     * @param extension 方法参数 {@code extension}
     * @param parser 方法参数 {@code parser}
     */
    private void register(String extension, DocumentParser parser) {
        if (parser.supports(extension)) {
            parserByExtension.put(extension, parser);
        }
    }

    /**
     * 在文档处理链路中执行 {@code normalizeExtension}。
     * <p>
     * 实现要点：先校验输入、状态或业务边界。
     *
     * @param extension 方法参数 {@code extension}
     * @return 处理后得到的字符串结果
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    private String normalizeExtension(String extension) {
        if (extension == null || extension.isBlank()) {
            throw new BusinessException("文档扩展名不能为空");
        }
        return extension.replaceFirst("^\\.", "").toLowerCase(Locale.ROOT);
    }
}
