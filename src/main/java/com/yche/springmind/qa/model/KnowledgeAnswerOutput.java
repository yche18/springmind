package com.yche.springmind.qa.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record KnowledgeAnswerOutput(
        boolean answered,
        String answer,
        String reasonCode,
        String reasonMessage
) {
}
