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

@Configuration(proxyBeanMethods = false)
public class DocumentIngestionConfiguration {

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

    @Bean
    @ConditionalOnMissingBean(TextCleanupTransformer.class)
    TextCleanupTransformer textCleanupTransformer() {
        return new TextCleanupTransformer();
    }

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
