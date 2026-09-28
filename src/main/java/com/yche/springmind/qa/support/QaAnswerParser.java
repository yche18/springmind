package com.yche.springmind.qa.support;

import com.yche.springmind.qa.model.KnowledgeAnswerOutput;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Parse and validate the model's structured answer contract. */
@Component
public class QaAnswerParser {

    private final ObjectMapper objectMapper;

    public QaAnswerParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public KnowledgeAnswerOutput parse(String rawAnswer) {
        if (!StringUtils.hasText(rawAnswer)) {
            return null;
        }
        try {
            return objectMapper.readValue(rawAnswer, KnowledgeAnswerOutput.class);
        } catch (JsonProcessingException exception) {
            return null;
        }
    }
}
