package com.yche.springmind.qa.model.vo;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AskQuestionResponse(
        boolean answered,
        String answer,
        String reasonCode,
        String reasonMessage,
        List<Citation> citations,
        AnswerTrace trace
) {

    public AskQuestionResponse(boolean answered, String answer, String reasonCode, String reasonMessage,
                               List<Citation> citations) {
        this(answered, answer, reasonCode, reasonMessage, citations, null);
    }

    public AskQuestionResponse withTrace(AnswerTrace trace) {
        return new AskQuestionResponse(answered, answer, reasonCode, reasonMessage, citations, trace);
    }

    public static AskQuestionResponse answered(String answer, List<Citation> citations) {
        return new AskQuestionResponse(true, answer, null, null, citations);
    }

    public static AskQuestionResponse unanswered(
            String reasonCode,
            String reasonMessage,
            List<Citation> citations
    ) {
        return new AskQuestionResponse(false, null, reasonCode, reasonMessage, citations);
    }

    public record Citation(
            Long documentId,
            Long chunkId,
            Integer chunkIndex,
            String fileName,
            double score,
            String snippet
    ) {
    }
}
