package com.yche.springmind.qa.service;

import com.yche.springmind.ai.config.AiChatModelProvider;
import com.yche.springmind.common.exception.BusinessException;
import com.yche.springmind.qa.model.QueryPlanResult;
import com.yche.springmind.qa.model.QueryPlanStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Produces bounded retrieval queries and falls back to the original question
 * whenever model planning is unavailable or invalid.
 */
@Service
public class QueryPlanningService {

    private static final Logger log = LoggerFactory.getLogger(QueryPlanningService.class);
    private static final int MAX_QUERY_COUNT = 3;

    private final PromptTemplate queryPlanningUserPromptTemplate;
    private final AiChatModelProvider chatModelProvider;

    public QueryPlanningService(
            @Qualifier("queryPlanningUserPromptTemplate") PromptTemplate queryPlanningUserPromptTemplate,
            AiChatModelProvider chatModelProvider
    ) {
        this.queryPlanningUserPromptTemplate = queryPlanningUserPromptTemplate;
        this.chatModelProvider = chatModelProvider;
    }

    public QueryPlanResult plan(String question) {
        return plan(null, question);
    }

    /**
     * Plan up to three retrieval queries, preserving the original question for
     * direct and rewrite strategies and falling back on any model failure.
     */
    public QueryPlanResult plan(Long userId, String question) {
        String normalizedQuestion = requireQuestion(question);
        if (userId == null || userId <= 0) {
            return QueryPlanResult.fallback(normalizedQuestion);
        }
        try {

            Prompt planPrompt = queryPlanningUserPromptTemplate.create(Map.of("question", normalizedQuestion));

            QueryPlanResult rawResult = chatClient().prompt(planPrompt)
                    .call()
                    .entity(QueryPlanResult.class);
            return validatePlan(rawResult, normalizedQuestion);
        } catch (RuntimeException exception) {
            log.warn("Query planning failed, fallback to direct query. question={}", normalizedQuestion, exception);
            return QueryPlanResult.fallback(normalizedQuestion);
        }
    }

    private ChatClient chatClient() {
        return ChatClient.builder(chatModelProvider.getChatModel()).build();
    }

    private QueryPlanResult validatePlan(QueryPlanResult rawResult, String originalQuestion) {
        if (rawResult == null || rawResult.strategy() == null) {
            return QueryPlanResult.fallback(originalQuestion);
        }
        Set<String> normalizedQueries = normalizeQueries(rawResult.queries());
        if (normalizedQueries.isEmpty()) {
            return QueryPlanResult.fallback(originalQuestion);
        }
        List<String> finalQueries = switch (rawResult.strategy()) {
            case DIRECT -> List.of(originalQuestion);
            case REWRITE -> buildRewriteQueries(originalQuestion, normalizedQueries);
            case DECOMPOSE -> limitQueries(normalizedQueries);
        };
        if (finalQueries.isEmpty()) {
            return QueryPlanResult.fallback(originalQuestion);
        }
        return new QueryPlanResult(rawResult.strategy(), finalQueries);
    }

    private List<String> buildRewriteQueries(String originalQuestion, Set<String> normalizedQueries) {
        LinkedHashSet<String> rewriteQueries = new LinkedHashSet<>();
        rewriteQueries.add(originalQuestion);
        rewriteQueries.addAll(normalizedQueries);
        return limitQueries(rewriteQueries);
    }

    private Set<String> normalizeQueries(List<String> queries) {
        LinkedHashSet<String> normalizedQueries = new LinkedHashSet<>();
        if (queries == null) {
            return normalizedQueries;
        }
        for (String query : queries) {
            if (!StringUtils.hasText(query)) {
                continue;
            }
            String normalized = query.replaceAll("\\s+", " ").trim();
            if (StringUtils.hasText(normalized)) {
                normalizedQueries.add(normalized);
            }
        }
        return normalizedQueries;
    }

    private List<String> limitQueries(Set<String> queries) {
        return queries.stream()
                .limit(MAX_QUERY_COUNT)
                .toList();
    }

    private String requireQuestion(String question) {
        if (!StringUtils.hasText(question)) {
            throw new BusinessException("问题不能为空");
        }
        return question.replaceAll("\\s+", " ").trim();
    }
}
