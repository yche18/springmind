package com.yche.springmind.ingestion.config;

import com.yche.springmind.document.mapper.DocumentMapper;
import com.yche.springmind.ingestion.chunk.ChunkService;
import com.yche.springmind.ingestion.parser.factory.DocumentParserFactory;
import com.yche.springmind.ingestion.service.DocumentIngestionProcessor;
import com.yche.springmind.ingestion.service.EtlDocumentIngestionProcessor;
import com.yche.springmind.ingestion.transformer.StructureAwareChunkTransformer;
import com.yche.springmind.ingestion.transformer.TextCleanupTransformer;
import com.yche.springmind.ingestion.vector.VectorIngestionService;
import com.yche.springmind.storage.service.ObjectStorageService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 组装文档读取、解析、清洗、切片与入库流水线所需的 Spring AI 组件。
 *
 * <p>位于配置层：集中声明配置项或组装 Spring Bean，使业务代码不直接依赖组件创建细节。</p>
 */
@Configuration(proxyBeanMethods = false)
public class DocumentIngestionConfiguration {

    /**
     * 创建或配置 {@code documentIngestionProcessor} 所需的 Spring 组件。
     * <p>
     * 实现要点：按文档类型解析正文内容；清洗并规范化解析后的文本；将正文切分并保存为可检索片段；先校验输入、状态或业务边界。
     *
     * @param documentMapperProvider 方法参数 {@code documentMapperProvider}
     * @param storageServiceProvider 方法参数 {@code storageServiceProvider}
     * @param parserFactoryProvider 方法参数 {@code parserFactoryProvider}
     * @param textCleanupTransformerProvider 方法参数 {@code textCleanupTransformerProvider}
     * @param chunkTransformerProvider 方法参数 {@code chunkTransformerProvider}
     * @param chunkServiceProvider 方法参数 {@code chunkServiceProvider}
     * @param vectorServiceProvider 方法参数 {@code vectorServiceProvider}
     * @return 方法执行结果，具体结构由返回类型 {@code DocumentIngestionProcessor} 表示
     */
    @Bean
    DocumentIngestionProcessor documentIngestionProcessor(
            ObjectProvider<DocumentMapper> documentMapperProvider,
            ObjectProvider<ObjectStorageService> storageServiceProvider,
            ObjectProvider<DocumentParserFactory> parserFactoryProvider,
            ObjectProvider<TextCleanupTransformer> textCleanupTransformerProvider,
            ObjectProvider<StructureAwareChunkTransformer> chunkTransformerProvider,
            ObjectProvider<ChunkService> chunkServiceProvider,
            ObjectProvider<VectorIngestionService> vectorServiceProvider
    ) {
        return new EtlDocumentIngestionProcessor(
                requireBean(documentMapperProvider, DocumentMapper.class),
                requireBean(storageServiceProvider, ObjectStorageService.class),
                requireBean(parserFactoryProvider, DocumentParserFactory.class),
                requireBean(textCleanupTransformerProvider, TextCleanupTransformer.class),
                requireBean(chunkTransformerProvider, StructureAwareChunkTransformer.class),
                requireBean(chunkServiceProvider, ChunkService.class),
                requireBean(vectorServiceProvider, VectorIngestionService.class)
        );
    }

    /**
     * 创建或配置 {@code textCleanupTransformer} 所需的 Spring 组件。
     * <p>
     * 实现要点：清洗并规范化解析后的文本。
     *
     * @return 方法执行结果，具体结构由返回类型 {@code TextCleanupTransformer} 表示
     */
    @Bean
    @ConditionalOnMissingBean(TextCleanupTransformer.class)
    TextCleanupTransformer textCleanupTransformer() {
        return new TextCleanupTransformer();
    }

    /**
     * 创建或配置 {@code requireBean} 所需的 Spring 组件。
     * <p>
     * 实现要点：先校验输入、状态或业务边界。
     *
     * @param <T> 方法使用的泛型类型
     * @param provider 方法参数 {@code provider}
     * @param beanType 方法参数 {@code beanType}
     * @return 方法执行结果，具体结构由返回类型 {@code T} 表示
     * @throws IllegalStateException 当输入、状态或依赖不满足方法约束时抛出
     */
    private <T> T requireBean(ObjectProvider<T> provider, Class<T> beanType) {
        T bean = provider.getIfAvailable();
        if (bean == null) {
            throw new IllegalStateException(
                    "Failed to create EtlDocumentIngestionProcessor, missing required bean: "
                            + beanType.getSimpleName()
            );
        }
        return bean;
    }
}
