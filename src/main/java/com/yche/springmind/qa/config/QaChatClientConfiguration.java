package com.yche.springmind.qa.config;

import com.yche.springmind.qa.rag.ReadyChunkDocumentRetriever;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

/**
 * 创建专用于知识问答的 ChatClient，并注入证据约束系统提示词。
 *
 * <p>位于配置层：集中声明配置项或组装 Spring Bean，使业务代码不直接依赖组件创建细节。</p>
 */
@Configuration
public class QaChatClientConfiguration {

    /**
     * 创建或配置 {@code qaSystemPromptTemplate} 所需的 Spring 组件。
     *
     * @return 方法执行结果，具体结构由返回类型 {@code PromptTemplate} 表示
     */
    @Bean
    public PromptTemplate qaSystemPromptTemplate() {
        return PromptTemplate.builder()
                .resource(new ClassPathResource("prompts/qa/system.st"))
                .build();
    }

    /**
     * 创建或配置 {@code qaUserPromptTemplate} 所需的 Spring 组件。
     *
     * @return 方法执行结果，具体结构由返回类型 {@code PromptTemplate} 表示
     */
    @Bean
    public PromptTemplate qaUserPromptTemplate() {
        return PromptTemplate.builder()
                .resource(new ClassPathResource("prompts/qa/user.st"))
                .build();
    }

    /**
     * 创建或配置 {@code qaRagContextPromptTemplate} 所需的 Spring 组件。
     *
     * @return 方法执行结果，具体结构由返回类型 {@code PromptTemplate} 表示
     */
    @Bean
    public PromptTemplate qaRagContextPromptTemplate() {
        return PromptTemplate.builder()
                .resource(new ClassPathResource("prompts/qa/rag-context.st"))
                .build();
    }

    /**
     * 创建或配置 {@code qaRetrievalAdvisor} 所需的 Spring 组件。
     *
     * @param readyChunkDocumentRetriever 方法参数 {@code readyChunkDocumentRetriever}
     * @param qaRagContextPromptTemplate 方法参数 {@code qaRagContextPromptTemplate}
     * @return 方法执行结果，具体结构由返回类型 {@code RetrievalAugmentationAdvisor} 表示
     */
    @Bean("qaRetrievalAdvisor")
    public RetrievalAugmentationAdvisor qaRetrievalAdvisor(
            ReadyChunkDocumentRetriever readyChunkDocumentRetriever,
            @Qualifier("qaRagContextPromptTemplate") PromptTemplate qaRagContextPromptTemplate
    ) {
        return RetrievalAugmentationAdvisor.builder()
                .documentRetriever(readyChunkDocumentRetriever)
                .queryAugmenter(new ContextualQueryAugmenter.Builder()
                        .allowEmptyContext(true)
                        .promptTemplate(qaRagContextPromptTemplate)
                        .build())
                .build();
    }
}
