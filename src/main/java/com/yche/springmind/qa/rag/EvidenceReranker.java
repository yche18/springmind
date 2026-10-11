package com.yche.springmind.qa.rag;

import org.springframework.ai.document.Document;
import java.util.List;

public interface EvidenceReranker {
    Result rerank(String question, List<Document> documents);

    record Result(List<Document> documents, String status) {
        public Result { documents = List.copyOf(documents); }
    }
}
