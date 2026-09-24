package com.yche.springmind.ingestion.parser.support;

import com.yche.springmind.common.exception.BusinessException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * 为文本类解析器提供 BOM 识别、UTF 编码检测和安全解码能力。
 *
 * <p>位于文档解析阶段：把特定格式的原始文件转换成统一文本，为后续清洗与切片提供输入。</p>
 */
public final class TextDecodingSupport {

    private static final byte[] UTF_8_BOM = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final byte[] UTF_16_LE_BOM = new byte[]{(byte) 0xFF, (byte) 0xFE};
    private static final byte[] UTF_16_BE_BOM = new byte[]{(byte) 0xFE, (byte) 0xFF};
    private static final Charset GB18030 = Charset.forName("GB18030");
    private static final List<Charset> FALLBACK_CHARSETS = List.of(StandardCharsets.UTF_8, GB18030);

    /**
     * 创建并初始化 {@link TextDecodingSupport}，保存该组件运行所需的依赖与配置。
     */
    private TextDecodingSupport() {
    }

    /**
     * 在文档处理链路中执行 {@code decode}。
     * <p>
     * 实现要点：捕获依赖异常并转换、记录或执行降级策略。
     *
     * @param inputStream 待读取或上传的数据流
     * @param failureMessage 方法参数 {@code failureMessage}
     * @return 处理后得到的字符串结果
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    public static String decode(InputStream inputStream, String failureMessage) {
        try {
            byte[] bytes = inputStream.readAllBytes();
            if (hasBom(bytes, UTF_8_BOM)) {
                return decodeStrict(bytes, UTF_8_BOM.length, StandardCharsets.UTF_8);
            }
            if (hasBom(bytes, UTF_16_LE_BOM)) {
                return decodeStrict(bytes, UTF_16_LE_BOM.length, StandardCharsets.UTF_16LE);
            }
            if (hasBom(bytes, UTF_16_BE_BOM)) {
                return decodeStrict(bytes, UTF_16_BE_BOM.length, StandardCharsets.UTF_16BE);
            }
            return decodeWithoutBom(bytes, failureMessage);
        } catch (IOException exception) {
            throw new BusinessException(failureMessage, exception);
        }
    }

    /**
     * 判断当前对象是否具有 {@code bom} 特征。
     *
     * @param bytes 方法参数 {@code bytes}
     * @param bom 方法参数 {@code bom}
     * @return 满足条件时返回 {@code true}，否则返回 {@code false}
     */
    private static boolean hasBom(byte[] bytes, byte[] bom) {
        return bytes.length >= bom.length && Arrays.equals(Arrays.copyOf(bytes, bom.length), bom);
    }

    /**
     * 在文档处理链路中执行 {@code decodeStrict}。
     *
     * @param bytes 方法参数 {@code bytes}
     * @param offset 方法参数 {@code offset}
     * @param charset 方法参数 {@code charset}
     * @return 处理后得到的字符串结果
     * @throws CharacterCodingException 当输入、状态或依赖不满足方法约束时抛出
     */
    private static String decodeStrict(byte[] bytes, int offset, Charset charset) throws CharacterCodingException {
        CharsetDecoder decoder = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        CharBuffer charBuffer = decoder.decode(ByteBuffer.wrap(bytes, offset, bytes.length - offset));
        return charBuffer.toString();
    }

    /**
     * 在文档处理链路中执行 {@code decodeWithoutBom}。
     * <p>
     * 实现要点：捕获依赖异常并转换、记录或执行降级策略。
     *
     * @param bytes 方法参数 {@code bytes}
     * @param failureMessage 方法参数 {@code failureMessage}
     * @return 处理后得到的字符串结果
     * @throws CharacterCodingException 当输入、状态或依赖不满足方法约束时抛出
     */
    private static String decodeWithoutBom(byte[] bytes, String failureMessage) throws CharacterCodingException {
        CharacterCodingException lastException = null;
        for (Charset charset : FALLBACK_CHARSETS) {
            try {
                return decodeStrict(bytes, 0, charset);
            } catch (CharacterCodingException exception) {
                lastException = exception;
            }
        }
        if (lastException != null) {
            throw lastException;
        }
        throw new CharacterCodingException();
    }
}
