package com.yche.springmind.qa.service;

import com.yche.springmind.ai.config.AiChatModelProvider;
import com.yche.springmind.common.exception.BusinessException;
import com.yche.springmind.qa.model.KnowledgeAnswerOutput;
import com.yche.springmind.qa.model.EvidenceLevel;
import com.yche.springmind.qa.model.vo.AskQuestionResponse;
import com.yche.springmind.qa.rag.EvidenceRetriever;
import com.yche.springmind.qa.rag.ReadyChunkDocumentRetriever;
import com.yche.springmind.qa.rag.RetrievedEvidenceBundle;
import com.yche.springmind.qa.support.CitationAssembler;
import com.yche.springmind.qa.support.QaAnswerParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * 编排权限校验、查询规划、混合检索、证据分级、模型回答和引用组装的完整 RAG 流程。
 *
 * <p>位于业务服务层：编排领域操作和基础设施调用，并集中维护事务、权限校验及失败处理边界。</p>
 */
@Service
public class QaChatService {

    private static final Logger log = LoggerFactory.getLogger(QaChatService.class);
    private static final String INSUFFICIENT_CODE = "INSUFFICIENT_EVIDENCE";
    private static final String INSUFFICIENT_MESSAGE = "检索到的有效证据不足，暂不回答。";
    private static final String FORMAT_ERROR_CODE = "ANSWER_FORMAT_ERROR";
    private static final String FORMAT_ERROR_MESSAGE = "模型返回格式错误，无法解析回答。";

    private final PromptTemplate qaSystemPromptTemplate;
    private final RetrievalAugmentationAdvisor qaRetrievalAdvisor;
    private final PromptTemplate qaUserPromptTemplate;
    private final EvidenceRetriever evidenceRetriever;
    private final QaAnswerParser answerParser;
    private final CitationAssembler citationAssembler;
    private final AiChatModelProvider chatModelProvider;

    /**
     * 创建并初始化 {@link QaChatService}，保存该组件运行所需的依赖与配置。
     * <p>
     * 实现要点：执行混合检索与结果融合；根据命中证据组装可追溯引用；调用大模型完成推理或生成。
     *
     * @param qaSystemPromptTemplate 方法参数 {@code qaSystemPromptTemplate}
     * @param qaRetrievalAdvisor 方法参数 {@code qaRetrievalAdvisor}
     * @param qaUserPromptTemplate 方法参数 {@code qaUserPromptTemplate}
     * @param evidenceRetriever 方法参数 {@code evidenceRetriever}
     * @param answerParser 方法参数 {@code answerParser}
     * @param citationAssembler 方法参数 {@code citationAssembler}
     * @param chatModelProvider 方法参数 {@code chatModelProvider}
     */
    public QaChatService(
            @Qualifier("qaSystemPromptTemplate") PromptTemplate qaSystemPromptTemplate,
            @Qualifier("qaRetrievalAdvisor") RetrievalAugmentationAdvisor qaRetrievalAdvisor,
            @Qualifier("qaUserPromptTemplate") PromptTemplate qaUserPromptTemplate,
            EvidenceRetriever evidenceRetriever,
            QaAnswerParser answerParser,
            CitationAssembler citationAssembler,
            AiChatModelProvider chatModelProvider
    ) {
        this.qaSystemPromptTemplate = qaSystemPromptTemplate;
        this.qaRetrievalAdvisor = qaRetrievalAdvisor;
        this.qaUserPromptTemplate = qaUserPromptTemplate;
        this.evidenceRetriever = evidenceRetriever;
        this.answerParser = answerParser;
        this.citationAssembler = citationAssembler;
        this.chatModelProvider = chatModelProvider;
    }

    /**
     * 围绕用户问题执行检索增强问答，并返回答案、证据等级和引用来源。
     * <p>
     * 实现要点：执行混合检索与结果融合；根据命中证据组装可追溯引用。
     *
     * @param userId 用户唯一标识
     * @param groupId 群组唯一标识
     * @param question 用户提交的自然语言问题
     * @return 方法执行结果，具体结构由返回类型 {@code AskQuestionResponse} 表示
     */
    public AskQuestionResponse ask(Long userId, Long groupId, String question) {
        RetrievedEvidenceBundle evidenceBundle = evidenceRetriever.retrieve(userId, groupId, question);
        List<Document> documents = evidenceBundle.documents();
        if (documents.isEmpty()) {
            return AskQuestionResponse.unanswered(INSUFFICIENT_CODE, INSUFFICIENT_MESSAGE, List.of());
        }
        KnowledgeAnswerOutput output = getStructuredAnswer(groupId, question, evidenceBundle);
        if (output == null) {
            return AskQuestionResponse.unanswered(FORMAT_ERROR_CODE, FORMAT_ERROR_MESSAGE, List.of());
        }
        if (!output.answered() || !StringUtils.hasText(output.answer())) {
            return AskQuestionResponse.unanswered(output.reasonCode(), output.reasonMessage(), List.of());
        }
        return AskQuestionResponse.answered(
                output.answer().trim(),
                citationAssembler.assembleDocuments(documents)
        );
    }

    /**
     * 围绕用户问题执行检索增强问答，并返回答案、证据等级和引用来源。
     *
     * @param groupId 群组唯一标识
     * @param question 用户提交的自然语言问题
     * @return 方法执行结果，具体结构由返回类型 {@code AskQuestionResponse} 表示
     * @throws IllegalStateException 当输入、状态或依赖不满足方法约束时抛出
     */
    public AskQuestionResponse ask(Long groupId, String question) {
        throw new IllegalStateException("QA 调用必须提供实际用户上下文");
    }

    /**
     * 把检索证据注入提示词并调用模型，解析为受约束的结构化回答。
     *
     * @param groupId 群组唯一标识
     * @param question 用户提交的自然语言问题
     * @param evidenceBundle 混合检索返回的证据集合及检索元数据
     * @return 查询得到的Structured回答结果
     */
    private KnowledgeAnswerOutput getStructuredAnswer(
            Long groupId,
            String question,
            RetrievedEvidenceBundle evidenceBundle
    ) {
        Prompt userPrompt = createUserPrompt(question, evidenceBundle);
        try {
            return chatClient().prompt(userPrompt)
                    .advisors(advisor -> advisor
                            .param("groupId", groupId)
                            .param(
                                    ReadyChunkDocumentRetriever.PREFETCHED_DOCUMENTS_CONTEXT_KEY,
                                    evidenceBundle.documents()
                            ))
                    .call()
                    .entity(KnowledgeAnswerOutput.class);
        } catch (BusinessException exception) {
            // AI configuration failures are business errors and must surface as-is.
            throw exception;
        } catch (RuntimeException exception) {
            log.warn(
                    "QA structured output failed, fallback to raw content. groupId={}, evidenceCount={}",
                    groupId,
                    evidenceBundle.documents().size(),
                    exception
            );
            return parseFallbackAnswer(groupId, question, evidenceBundle);
        }
    }

    /**
     * 执行 {@code parseFallbackAnswer} 对应的业务步骤。
     * <p>
     * 实现要点：调用大模型完成推理或生成；按文档类型解析正文内容；捕获依赖异常并转换、记录或执行降级策略。
     *
     * @param groupId 群组唯一标识
     * @param question 用户提交的自然语言问题
     * @param evidenceBundle 混合检索返回的证据集合及检索元数据
     * @return 方法执行结果，具体结构由返回类型 {@code KnowledgeAnswerOutput} 表示
     */
    private KnowledgeAnswerOutput parseFallbackAnswer(
            Long groupId,
            String question,
            RetrievedEvidenceBundle evidenceBundle
    ) {
        Prompt userPrompt = createUserPrompt(question, evidenceBundle);
        try {
            String rawAnswer = chatClient().prompt(userPrompt)
                    .advisors(advisor -> advisor
                            .param("groupId", groupId)
                            .param(
                                    ReadyChunkDocumentRetriever.PREFETCHED_DOCUMENTS_CONTEXT_KEY,
                                    evidenceBundle.documents()
                            ))
                    .call()
                    .content();
            log.info(
                    "QA raw answer fallback. groupId={}, evidenceCount={}, rawLength={}",
                    groupId,
                    evidenceBundle.documents().size(),
                    rawAnswer == null ? 0 : rawAnswer.length()
            );
            return answerParser.parse(rawAnswer);
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.error(
                    "QA raw answer fallback failed. groupId={}, evidenceCount={}",
                    groupId,
                    evidenceBundle.documents().size(),
                    exception
            );
            return null;
        }
    }

    /**
     * 执行 {@code chatClient} 对应的业务步骤。
     * <p>
     * 实现要点：调用大模型完成推理或生成。
     *
     * @return 方法执行结果，具体结构由返回类型 {@code ChatClient} 表示
     */
    private ChatClient chatClient() {
        return ChatClient.builder(chatModelProvider.getChatModel())
                .defaultSystem(qaSystemPromptTemplate.getTemplate())
                .defaultAdvisors(qaRetrievalAdvisor)
                .build();
    }

    /**
     * 执行 {@code createUserPrompt} 对应的业务步骤。
     *
     * @param question 用户提交的自然语言问题
     * @param evidenceBundle 混合检索返回的证据集合及检索元数据
     * @return 方法执行结果，具体结构由返回类型 {@code Prompt} 表示
     */
    private Prompt createUserPrompt(String question, RetrievedEvidenceBundle evidenceBundle) {
        EvidenceLevel evidenceLevel = evidenceBundle.evidenceLevel() == null
                ? EvidenceLevel.NONE
                : evidenceBundle.evidenceLevel();
        return qaUserPromptTemplate.create(Map.of(
                "question", question,
                "evidenceLevel", evidenceLevel.name(),
                "evidenceGuidance", evidenceBundle.evidenceGuidance()
        ));
    }
}
