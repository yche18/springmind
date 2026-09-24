package com.yche.springmind.qa.support;

import com.yche.springmind.qa.model.vo.AskQuestionResponse;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 把模型引用编号与真实检索证据对应起来，生成可追溯且不会越界的引用列表。
 *
 * <p>位于问答辅助层：对模型输出或检索证据进行确定性处理，保持核心问答服务职责清晰。</p>
 */
@Component
public class CitationAssembler {

    /**
     * 完成 {@code assembleDocuments} 对应的处理。
     * <p>
     * 实现要点：根据命中证据组装可追溯引用。
     *
     * @param documents 方法参数 {@code documents}
     * @return 符合条件的结果集合；无结果时返回空集合
     */
    public List<AskQuestionResponse.Citation> assembleDocuments(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }
        Map<String, AskQuestionResponse.Citation> citationsByFileName = new LinkedHashMap<>();
        for (Document document : documents) {
            AskQuestionResponse.Citation citation = toCitation(document);
            if (citation != null) {
                citationsByFileName.putIfAbsent(citation.fileName(), citation);
            }
        }
        return List.copyOf(citationsByFileName.values());
    }

    /**
     * 完成 {@code toCitation} 对应的处理。
     *
     * @param document 当前处理的文档实体
     * @return 方法执行结果，具体结构由返回类型 {@code AskQuestionResponse.Citation} 表示
     */
    private AskQuestionResponse.Citation toCitation(Document document) {
        Map<String, Object> metadata = document.getMetadata();
        String fileName = readFileName(metadata);
        if (!StringUtils.hasText(fileName)) {
            return null;
        }
        return new AskQuestionResponse.Citation(
                readLong(metadata, "documentId"),
                readLong(metadata, "chunkId"),
                readInteger(metadata, "chunkIndex"),
                fileName,
                readScore(metadata),
                null
        );
    }

    /**
     * 完成 {@code readLong} 对应的处理。
     *
     * @param metadata 方法参数 {@code metadata}
     * @param key 方法参数 {@code key}
     * @return 计算或处理得到的数值结果
     */
    private Long readLong(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return null;
    }

    /**
     * 完成 {@code readInteger} 对应的处理。
     *
     * @param metadata 方法参数 {@code metadata}
     * @param key 方法参数 {@code key}
     * @return 计算或处理得到的数值结果
     */
    private Integer readInteger(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return null;
    }

    /**
     * 完成 {@code readScore} 对应的处理。
     *
     * @param metadata 方法参数 {@code metadata}
     * @return 计算或处理得到的数值结果
     */
    private double readScore(Map<String, Object> metadata) {
        Object value = metadata.get("score");
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return 0D;
    }

    /**
     * 完成 {@code readText} 对应的处理。
     *
     * @param metadata 方法参数 {@code metadata}
     * @param key 方法参数 {@code key}
     * @return 处理后得到的字符串结果
     */
    private String readText(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        return value instanceof String text ? text.trim() : null;
    }

    /**
     * 完成 {@code readFileName} 对应的处理。
     *
     * @param metadata 方法参数 {@code metadata}
     * @return 处理后得到的字符串结果
     */
    private String readFileName(Map<String, Object> metadata) {
        String fileName = readText(metadata, "fileName");
        if (StringUtils.hasText(fileName)) {
            return fileName;
        }
        return readText(metadata, "documentName");
    }
}
