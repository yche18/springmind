package com.yche.springmind.ingestion.transformer;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentTransformer;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 清理不可见字符、异常空白和格式噪声，同时尽量保留有意义的文档结构。
 *
 * <p>位于文档转换阶段：对解析后的文本进行清洗或结构化切分，为索引建立准备稳定数据。</p>
 */
public class TextCleanupTransformer implements DocumentTransformer {

    private static final int MAX_FENCE_INDENT = 3;
    private static final int MIN_FENCE_LENGTH = 3;
    private static final Pattern CONTROL_CHARACTERS = Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]");
    private static final Pattern INLINE_WHITESPACE = Pattern.compile("[ \\t]+");
    private static final Pattern EXCESSIVE_BLANK_LINES = Pattern.compile("\\n{3,}");

    /**
     * 在文档处理链路中执行 {@code apply}。
     * <p>
     * 实现要点：清洗并规范化解析后的文本。
     *
     * @param documents 方法参数 {@code documents}
     * @return 符合条件的结果集合；无结果时返回空集合
     */
    @Override
    public List<Document> apply(List<Document> documents) {
        return documents.stream()
                .map(this::cleanupDocument)
                .toList();
    }

    /**
     * 在文档处理链路中执行 {@code cleanupDocument}。
     * <p>
     * 实现要点：清洗并规范化解析后的文本。
     *
     * @param document 当前处理的文档实体
     * @return 方法执行结果，具体结构由返回类型 {@code Document} 表示
     */
    private Document cleanupDocument(Document document) {
        if (document.getText() == null) {
            return document;
        }
        return document.mutate()
                .text(clean(document.getText()))
                .build();
    }

    /**
     * 在文档处理链路中执行 {@code clean}。
     * <p>
     * 实现要点：先校验输入、状态或业务边界。
     *
     * @param source 方法参数 {@code source}
     * @return 处理后得到的字符串结果
     */
    String clean(String source) {
        if (source == null || source.isEmpty()) {
            return "";
        }

        String normalized = source.replace("\r\n", "\n").replace('\r', '\n');
        normalized = CONTROL_CHARACTERS.matcher(normalized).replaceAll("");
        return cleanLines(normalized);
    }

    /**
     * 在文档处理链路中执行 {@code normalizeLine}。
     * <p>
     * 实现要点：先校验输入、状态或业务边界。
     *
     * @param line 方法参数 {@code line}
     * @return 处理后得到的字符串结果
     */
    private String normalizeLine(String line) {
        String trimmed = line.strip();
        if (trimmed.isEmpty()) {
            return "";
        }
        return INLINE_WHITESPACE.matcher(trimmed).replaceAll(" ");
    }

    /**
     * 在文档处理链路中执行 {@code cleanLines}。
     * <p>
     * 实现要点：先校验输入、状态或业务边界。
     *
     * @param normalized 方法参数 {@code normalized}
     * @return 处理后得到的字符串结果
     */
    private String cleanLines(String normalized) {
        List<String> segments = new ArrayList<>();
        List<String> plainLines = new ArrayList<>();
        List<String> fenceLines = null;
        FenceMarker openingFence = null;

        for (String line : normalized.split("\n", -1)) {
            if (openingFence != null) {
                if (isClosingFence(line, openingFence)) {
                    fenceLines.add(line);
                    segments.add(String.join("\n", fenceLines));
                    fenceLines = null;
                    openingFence = null;
                } else {
                    fenceLines.add(line);
                }
                continue;
            }

            FenceMarker candidateFence = parseOpeningFence(line);
            if (candidateFence != null) {
                appendPlainSegment(segments, plainLines);
                fenceLines = new ArrayList<>();
                fenceLines.add(line);
                openingFence = candidateFence;
                continue;
            }

            plainLines.add(line);
        }

        if (openingFence != null && fenceLines != null) {
            segments.add(String.join("\n", fenceLines));
        }
        appendPlainSegment(segments, plainLines);
        return String.join("\n", segments);
    }

    /**
     * 在文档处理链路中执行 {@code parseOpeningFence}。
     *
     * @param line 方法参数 {@code line}
     * @return 方法执行结果，具体结构由返回类型 {@code FenceMarker} 表示
     */
    private FenceMarker parseOpeningFence(String line) {
        int contentStart = countLeadingSpaces(line);
        if (contentStart < 0 || contentStart >= line.length()) {
            return null;
        }

        char marker = line.charAt(contentStart);
        int fenceLength = countFenceLength(line, contentStart, marker);
        if ((marker != '`' && marker != '~') || fenceLength < MIN_FENCE_LENGTH) {
            return null;
        }
        return new FenceMarker(marker, fenceLength);
    }

    /**
     * 判断当前数据是否满足 {@code closingFence} 条件。
     *
     * @param line 方法参数 {@code line}
     * @param openingFence 方法参数 {@code openingFence}
     * @return 满足条件时返回 {@code true}，否则返回 {@code false}
     */
    private boolean isClosingFence(String line, FenceMarker openingFence) {
        int contentStart = countLeadingSpaces(line);
        if (contentStart < 0 || contentStart >= line.length()) {
            return false;
        }

        int fenceLength = countFenceLength(line, contentStart, openingFence.marker());
        return line.charAt(contentStart) == openingFence.marker()
                && fenceLength >= openingFence.length()
                && hasOnlyTrailingWhitespace(line, contentStart + fenceLength);
    }

    /**
     * 在文档处理链路中执行 {@code countLeadingSpaces}。
     *
     * @param line 方法参数 {@code line}
     * @return 计算或处理得到的数值结果
     */
    private int countLeadingSpaces(String line) {
        int leadingSpaces = 0;
        while (leadingSpaces < line.length() && line.charAt(leadingSpaces) == ' ') {
            leadingSpaces++;
        }
        return leadingSpaces <= MAX_FENCE_INDENT ? leadingSpaces : -1;
    }

    /**
     * 在文档处理链路中执行 {@code countFenceLength}。
     *
     * @param line 方法参数 {@code line}
     * @param startIndex 方法参数 {@code startIndex}
     * @param marker 方法参数 {@code marker}
     * @return 计算或处理得到的数值结果
     */
    private int countFenceLength(String line, int startIndex, char marker) {
        int fenceLength = 0;
        while (startIndex + fenceLength < line.length() && line.charAt(startIndex + fenceLength) == marker) {
            fenceLength++;
        }
        return fenceLength;
    }

    /**
     * 判断当前对象是否具有 {@code onlyTrailingWhitespace} 特征。
     *
     * @param line 方法参数 {@code line}
     * @param startIndex 方法参数 {@code startIndex}
     * @return 满足条件时返回 {@code true}，否则返回 {@code false}
     */
    private boolean hasOnlyTrailingWhitespace(String line, int startIndex) {
        for (int index = startIndex; index < line.length(); index++) {
            if (!Character.isWhitespace(line.charAt(index))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 在文档处理链路中执行 {@code appendPlainSegment}。
     * <p>
     * 实现要点：先校验输入、状态或业务边界。
     *
     * @param segments 方法参数 {@code segments}
     * @param plainLines 方法参数 {@code plainLines}
     */
    private void appendPlainSegment(List<String> segments, List<String> plainLines) {
        if (plainLines.isEmpty()) {
            return;
        }

        String normalizedPlainText = plainLines.stream()
                .map(this::normalizeLine)
                .collect(Collectors.joining("\n"));
        segments.add(EXCESSIVE_BLANK_LINES.matcher(normalizedPlainText).replaceAll("\n\n"));
        plainLines.clear();
    }

    /**
     * 表示清洗 Markdown 时识别到的代码围栏字符和长度。
     *
     * <p>仅在 {@code TextCleanupTransformer} 的实现过程中使用，用不可变数据结构收拢中间结果，避免参数和值的含义混淆。</p>
     */
    private record FenceMarker(char marker, int length) {
    }
}
