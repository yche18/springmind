package com.yche.springmind.ingestion.transformer;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentTransformer;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 按标题、段落和代码围栏感知文档结构，在长度限制内生成带重叠的切片。
 *
 * <p>位于文档转换阶段：对解析后的文本进行清洗或结构化切分，为索引建立准备稳定数据。</p>
 */
@Component
public class StructureAwareChunkTransformer implements DocumentTransformer {
    private static final String STRATEGY = "structure-aware-token-budget-v1";
    private static final Pattern BLANK_LINES = Pattern.compile("\\n\\s*\\n+");
    private static final Pattern HEADING = Pattern.compile("(?m)^(#{1,6})\\s+(.+)$");
    private static final int CHARS_PER_TOKEN = 1;
    private final ChunkingProperties properties;

    /**
     * 创建并初始化 {@link StructureAwareChunkTransformer}，保存该组件运行所需的依赖与配置。
     *
     * @param properties 方法参数 {@code properties}
     */
    public StructureAwareChunkTransformer(ChunkingProperties properties) {
        this.properties = properties;
    }

    /**
     * 在文档处理链路中执行 {@code apply}。
     *
     * @param documents 方法参数 {@code documents}
     * @return 符合条件的结果集合；无结果时返回空集合
     */
    @Override
    public List<Document> apply(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }
        List<Document> chunks = new ArrayList<>();
        for (Document document : documents) {
            chunks.addAll(chunkDocument(document));
        }
        return chunks;
    }

    /**
     * 在文档处理链路中执行 {@code chunkDocument}。
     *
     * @param document 当前处理的文档实体
     * @return 符合条件的结果集合；无结果时返回空集合
     */
    private List<Document> chunkDocument(Document document) {
        if (document == null || document.getText() == null || document.getText().isBlank()) {
            return List.of();
        }
        String text = document.getText();
        List<ChunkRange> ranges = splitBySections(text).stream()
                .flatMap(section -> splitSection(text, section).stream())
                .toList();
        return buildDocuments(document, ranges);
    }

    /**
     * 在文档处理链路中执行 {@code splitBySections}。
     *
     * @param text 方法参数 {@code text}
     * @return 符合条件的结果集合；无结果时返回空集合
     */
    private List<Section> splitBySections(String text) {
        List<HeadingMatch> headings = collectHeadings(text);
        if (headings.isEmpty()) {
            return List.of(new Section(0, text.length(), ""));
        }
        List<Section> sections = new ArrayList<>();
        appendLeadingSection(text, headings.getFirst().start(), sections);
        for (int index = 0; index < headings.size(); index++) {
            HeadingMatch heading = headings.get(index);
            int end = index + 1 < headings.size() ? headings.get(index + 1).start() : text.length();
            appendSection(heading.start(), end, heading.title(), text, sections);
        }
        return sections;
    }

    /**
     * 在文档处理链路中执行 {@code collectHeadings}。
     *
     * @param text 方法参数 {@code text}
     * @return 符合条件的结果集合；无结果时返回空集合
     */
    private List<HeadingMatch> collectHeadings(String text) {
        List<HeadingMatch> headings = new ArrayList<>();
        Fence fence = null;
        for (int start = 0; start < text.length(); ) {
            int end = text.indexOf('\n', start);
            end = end >= 0 ? end : text.length();
            String line = text.substring(start, end);
            if (fence == null) {
                fence = openFence(line);
                if (fence == null) {
                    HeadingMatch heading = parseHeading(line, start);
                    if (heading != null) {
                        headings.add(heading);
                    }
                }
            } else if (isClosingFence(line, fence)) {
                fence = null;
            }
            start = end < text.length() ? end + 1 : text.length();
        }
        return headings;
    }

    /**
     * 在文档处理链路中执行 {@code appendLeadingSection}。
     *
     * @param text 方法参数 {@code text}
     * @param firstHeadingStart 方法参数 {@code firstHeadingStart}
     * @param sections 方法参数 {@code sections}
     */
    private void appendLeadingSection(String text, int firstHeadingStart, List<Section> sections) {
        if (firstHeadingStart <= 0) return;
        Range range = trimRange(text, 0, firstHeadingStart);
        if (range != null) sections.add(new Section(range.start(), range.end(), ""));
    }

    /**
     * 在文档处理链路中执行 {@code appendSection}。
     *
     * @param start 方法参数 {@code start}
     * @param end 方法参数 {@code end}
     * @param title 方法参数 {@code title}
     * @param text 方法参数 {@code text}
     * @param sections 方法参数 {@code sections}
     */
    private void appendSection(int start, int end, String title, String text, List<Section> sections) {
        Range range = trimRange(text, start, end);
        if (range != null) sections.add(new Section(range.start(), range.end(), title));
    }

    /**
     * 在文档处理链路中执行 {@code splitSection}。
     *
     * @param text 方法参数 {@code text}
     * @param section 方法参数 {@code section}
     * @return 符合条件的结果集合；无结果时返回空集合
     */
    private List<ChunkRange> splitSection(String text, Section section) {
        return estimateTokens(text.substring(section.start(), section.end())) <= maxTokens()
                ? List.of(section.toChunkRange())
                : mergePieces(text, splitOversizedPieces(text, section));
    }

    /**
     * 在文档处理链路中执行 {@code splitOversizedPieces}。
     *
     * @param text 方法参数 {@code text}
     * @param section 方法参数 {@code section}
     * @return 符合条件的结果集合；无结果时返回空集合
     */
    private List<ChunkRange> splitOversizedPieces(String text, Section section) {
        List<ChunkRange> pieces = new ArrayList<>();
        for (ChunkRange paragraph : splitByParagraphs(text, section)) {
            String paragraphText = text.substring(paragraph.start(), paragraph.end());
            pieces.addAll(estimateTokens(paragraphText) <= maxTokens()
                    ? List.of(paragraph)
                    : splitBySentences(text, paragraph));
        }
        return splitRemainingOversized(text, pieces);
    }

    /**
     * 在文档处理链路中执行 {@code splitByParagraphs}。
     *
     * @param text 方法参数 {@code text}
     * @param section 方法参数 {@code section}
     * @return 符合条件的结果集合；无结果时返回空集合
     */
    private List<ChunkRange> splitByParagraphs(String text, Section section) {
        Matcher matcher = BLANK_LINES.matcher(text.substring(section.start(), section.end()));
        List<ChunkRange> paragraphs = new ArrayList<>();
        int cursor = section.start();
        while (matcher.find()) {
            appendRange(text, cursor, section.start() + matcher.start(), section.path(), section.start(), paragraphs);
            cursor = section.start() + matcher.end();
        }
        appendRange(text, cursor, section.end(), section.path(), section.start(), paragraphs);
        return paragraphs;
    }

    /**
     * 在文档处理链路中执行 {@code splitBySentences}。
     *
     * @param text 方法参数 {@code text}
     * @param range 方法参数 {@code range}
     * @return 符合条件的结果集合；无结果时返回空集合
     */
    private List<ChunkRange> splitBySentences(String text, ChunkRange range) {
        List<ChunkRange> sentences = new ArrayList<>();
        int cursor = range.start();
        for (int index = range.start(); index < range.end(); index++) {
            if (isSentenceBoundary(text.charAt(index))) {
                appendRange(text, cursor, index + 1, range.path(), range.sectionStart(), sentences);
                cursor = index + 1;
            }
        }
        appendRange(text, cursor, range.end(), range.path(), range.sectionStart(), sentences);
        return sentences;
    }

    /**
     * 在文档处理链路中执行 {@code splitRemainingOversized}。
     *
     * @param text 方法参数 {@code text}
     * @param pieces 方法参数 {@code pieces}
     * @return 符合条件的结果集合；无结果时返回空集合
     */
    private List<ChunkRange> splitRemainingOversized(String text, List<ChunkRange> pieces) {
        List<ChunkRange> ranges = new ArrayList<>();
        for (ChunkRange piece : pieces) {
            boolean fits = estimateTokens(text.substring(piece.start(), piece.end())) <= maxTokens();
            ranges.addAll(fits ? List.of(piece) : splitByTokenBudget(text, piece));
        }
        return ranges;
    }

    /**
     * 在文档处理链路中执行 {@code splitByTokenBudget}。
     *
     * @param text 方法参数 {@code text}
     * @param range 方法参数 {@code range}
     * @return 符合条件的结果集合；无结果时返回空集合
     */
    private List<ChunkRange> splitByTokenBudget(String text, ChunkRange range) {
        List<ChunkRange> chunks = new ArrayList<>();
        int maxChars = Math.max(CHARS_PER_TOKEN, maxTokens() * CHARS_PER_TOKEN);
        for (int cursor = range.start(); cursor < range.end(); cursor += maxChars) {
            appendRange(text, cursor, Math.min(range.end(), cursor + maxChars), range.path(),
                    range.sectionStart(), chunks);
        }
        return chunks;
    }

    /**
     * 在文档处理链路中执行 {@code mergePieces}。
     *
     * @param text 方法参数 {@code text}
     * @param pieces 方法参数 {@code pieces}
     * @return 符合条件的结果集合；无结果时返回空集合
     */
    private List<ChunkRange> mergePieces(String text, List<ChunkRange> pieces) {
        List<ChunkRange> chunks = new ArrayList<>();
        ChunkRange current = null;
        for (ChunkRange piece : pieces) {
            if (current == null) {
                current = piece;
            } else if (canMerge(text, current, piece)) {
                current = new ChunkRange(current.start(), piece.end(), current.path(), current.sectionStart());
            } else {
                chunks.add(current);
                current = piece;
            }
        }
        if (current != null) {
            chunks.add(current);
        }
        return chunks;
    }

    /**
     * 在文档处理链路中执行 {@code canMerge}。
     *
     * @param text 方法参数 {@code text}
     * @param current 方法参数 {@code current}
     * @param next 方法参数 {@code next}
     * @return 满足条件时返回 {@code true}，否则返回 {@code false}
     */
    private boolean canMerge(String text, ChunkRange current, ChunkRange next) {
        String candidate = text.substring(current.start(), next.end());
        int currentTokens = estimateTokens(text.substring(current.start(), current.end()));
        return estimateTokens(candidate) <= targetTokens()
                || currentTokens < targetTokens() && estimateTokens(candidate) <= maxTokens();
    }

    /**
     * 在文档处理链路中执行 {@code buildDocuments}。
     *
     * @param source 方法参数 {@code source}
     * @param ranges 方法参数 {@code ranges}
     * @return 符合条件的结果集合；无结果时返回空集合
     */
    private List<Document> buildDocuments(Document source, List<ChunkRange> ranges) {
        List<Document> chunks = new ArrayList<>();
        for (ChunkRange range : ranges) {
            int start = chunks.isEmpty() ? range.start() : overlapStart(range.start(), range.sectionStart());
            Range chunkRange = trimRange(source.getText(), start, range.end());
            if (chunkRange != null) {
                chunks.add(buildDocument(source, chunkRange, range.path(), chunks.size()));
            }
        }
        return chunks;
    }

    /**
     * 在文档处理链路中执行 {@code buildDocument}。
     *
     * @param source 方法参数 {@code source}
     * @param range 方法参数 {@code range}
     * @param sectionPath 方法参数 {@code sectionPath}
     * @param chunkIndex 方法参数 {@code chunkIndex}
     * @return 方法执行结果，具体结构由返回类型 {@code Document} 表示
     */
    private Document buildDocument(Document source, Range range, String sectionPath, int chunkIndex) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (source.getMetadata() != null) {
            metadata.putAll(source.getMetadata());
        }
        metadata.put("sectionPath", sectionPath);
        metadata.put("charStart", range.start());
        metadata.put("charEnd", range.end());
        metadata.put("chunkStrategy", STRATEGY);
        String id = source.getId() == null ? null : source.getId() + ":" + chunkIndex;
        return Document.builder()
                .id(id)
                .text(source.getText().substring(range.start(), range.end()))
                .metadata(metadata)
                .build();
    }

    /**
     * 在文档处理链路中执行 {@code overlapStart}。
     *
     * @param start 方法参数 {@code start}
     * @param sectionStart 方法参数 {@code sectionStart}
     * @return 计算或处理得到的数值结果
     */
    private int overlapStart(int start, int sectionStart) {
        if (properties.getOverlapTokens() <= 0) return start;
        return Math.max(sectionStart, start - properties.getOverlapTokens() * CHARS_PER_TOKEN);
    }

    /**
     * 在文档处理链路中执行 {@code appendRange}。
     *
     * @param text 方法参数 {@code text}
     * @param start 方法参数 {@code start}
     * @param end 方法参数 {@code end}
     * @param path 方法参数 {@code path}
     * @param sectionStart 方法参数 {@code sectionStart}
     * @param ranges 方法参数 {@code ranges}
     */
    private void appendRange(String text, int start, int end, String path,
                             int sectionStart, List<ChunkRange> ranges) {
        Range range = trimRange(text, start, end);
        if (range != null) ranges.add(new ChunkRange(range.start(), range.end(), path, sectionStart));
    }

    /**
     * 在文档处理链路中执行 {@code trimRange}。
     * <p>
     * 实现要点：先校验输入、状态或业务边界。
     *
     * @param text 方法参数 {@code text}
     * @param start 方法参数 {@code start}
     * @param end 方法参数 {@code end}
     * @return 方法执行结果，具体结构由返回类型 {@code Range} 表示
     */
    private Range trimRange(String text, int start, int end) {
        int normalizedStart = Math.max(0, start);
        int normalizedEnd = Math.min(text.length(), end);
        while (normalizedStart < normalizedEnd && Character.isWhitespace(text.charAt(normalizedStart))) {
            normalizedStart++;
        }
        while (normalizedEnd > normalizedStart && Character.isWhitespace(text.charAt(normalizedEnd - 1))) {
            normalizedEnd--;
        }
        return normalizedStart < normalizedEnd ? new Range(normalizedStart, normalizedEnd) : null;
    }

    /**
     * 判断当前数据是否满足 {@code sentenceBoundary} 条件。
     *
     * @param character 方法参数 {@code character}
     * @return 满足条件时返回 {@code true}，否则返回 {@code false}
     */
    private boolean isSentenceBoundary(char character) { return "。！？；!?;".indexOf(character) >= 0; }
    /**
     * 在文档处理链路中执行 {@code cleanHeading}。
     *
     * @param title 方法参数 {@code title}
     * @return 处理后得到的字符串结果
     */
    private String cleanHeading(String title) { return title.replaceAll("\\s+#*$", "").strip(); }

    /**
     * 在文档处理链路中执行 {@code parseHeading}。
     *
     * @param line 方法参数 {@code line}
     * @param start 方法参数 {@code start}
     * @return 方法执行结果，具体结构由返回类型 {@code HeadingMatch} 表示
     */
    private HeadingMatch parseHeading(String line, int start) {
        Matcher matcher = HEADING.matcher(line);
        return matcher.matches() ? new HeadingMatch(start, cleanHeading(matcher.group(2))) : null;
    }

    /**
     * 在文档处理链路中执行 {@code openFence}。
     *
     * @param line 方法参数 {@code line}
     * @return 方法执行结果，具体结构由返回类型 {@code Fence} 表示
     */
    private Fence openFence(String line) {
        int indent = leadingSpaces(line);
        if (indent > 3 || indent == line.length()) {
            return null;
        }
        char marker = line.charAt(indent);
        int length = fenceLength(line, indent, marker);
        return (marker == '`' || marker == '~') && length >= 3 ? new Fence(marker, length) : null;
    }

    /**
     * 判断当前数据是否满足 {@code closingFence} 条件。
     *
     * @param line 方法参数 {@code line}
     * @param fence 方法参数 {@code fence}
     * @return 满足条件时返回 {@code true}，否则返回 {@code false}
     */
    private boolean isClosingFence(String line, Fence fence) {
        int indent = leadingSpaces(line);
        if (indent > 3 || indent == line.length() || line.charAt(indent) != fence.marker()) {
            return false;
        }
        int length = fenceLength(line, indent, fence.marker());
        return length >= fence.length() && line.substring(indent + length).isBlank();
    }

    /**
     * 在文档处理链路中执行 {@code leadingSpaces}。
     *
     * @param line 方法参数 {@code line}
     * @return 计算或处理得到的数值结果
     */
    private int leadingSpaces(String line) {
        int index = 0;
        while (index < line.length() && line.charAt(index) == ' ') index++;
        return index;
    }
    /**
     * 在文档处理链路中执行 {@code fenceLength}。
     *
     * @param line 方法参数 {@code line}
     * @param start 方法参数 {@code start}
     * @param marker 方法参数 {@code marker}
     * @return 计算或处理得到的数值结果
     */
    private int fenceLength(String line, int start, char marker) {
        int index = start;
        while (index < line.length() && line.charAt(index) == marker) index++;
        return index - start;
    }
    /**
     * 在文档处理链路中执行 {@code targetTokens}。
     *
     * @return 计算或处理得到的数值结果
     */
    private int targetTokens() { return Math.max(1, properties.getTargetTokens()); }
    /**
     * 在文档处理链路中执行 {@code maxTokens}。
     *
     * @return 计算或处理得到的数值结果
     */
    private int maxTokens() { return Math.max(targetTokens(), properties.getMaxTokens()); }
    /**
     * 在文档处理链路中执行 {@code estimateTokens}。
     *
     * @param text 方法参数 {@code text}
     * @return 计算或处理得到的数值结果
     */
    private int estimateTokens(String text) { return Math.max(1, text.length()); }

    /**
     * 表示文档中识别到的标题位置和标题文本。
     *
     * <p>仅在 {@code StructureAwareChunkTransformer} 的实现过程中使用，用不可变数据结构收拢中间结果，避免参数和值的含义混淆。</p>
     */
    private record HeadingMatch(int start, String title) {}
    /**
     * 表示结构切分算法中的字符起止区间。
     *
     * <p>仅在 {@code StructureAwareChunkTransformer} 的实现过程中使用，用不可变数据结构收拢中间结果，避免参数和值的含义混淆。</p>
     */
    private record Range(int start, int end) {}
    /**
     * 表示 Markdown 代码围栏的标记字符和长度。
     *
     * <p>仅在 {@code StructureAwareChunkTransformer} 的实现过程中使用，用不可变数据结构收拢中间结果，避免参数和值的含义混淆。</p>
     */
    private record Fence(char marker, int length) {}
    /**
     * 表示带层级路径的一段文档结构区间。
     *
     * <p>仅在 {@code StructureAwareChunkTransformer} 的实现过程中使用，用不可变数据结构收拢中间结果，避免参数和值的含义混淆。</p>
     */
    private record Section(int start, int end, String path) {
        /**
         * 在文档处理链路中执行 {@code toChunkRange}。
         *
         * @return 方法执行结果，具体结构由返回类型 {@code ChunkRange} 表示
         */
        private ChunkRange toChunkRange() { return new ChunkRange(start, end, path, start); }
    }
    /**
     * 表示最终切片的字符范围、结构路径和所属章节位置。
     *
     * <p>仅在 {@code StructureAwareChunkTransformer} 的实现过程中使用，用不可变数据结构收拢中间结果，避免参数和值的含义混淆。</p>
     */
    private record ChunkRange(int start, int end, String path, int sectionStart) {}
}
