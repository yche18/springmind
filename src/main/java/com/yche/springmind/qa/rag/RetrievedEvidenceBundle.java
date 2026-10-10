package com.yche.springmind.qa.rag;

import com.yche.springmind.qa.model.EvidenceLevel;
import com.yche.springmind.qa.model.vo.RetrievalTrace;
import org.springframework.ai.document.Document;

import java.util.List;

public record RetrievedEvidenceBundle(
        List<Document> documents,
        EvidenceLevel evidenceLevel,
        String evidenceGuidance,
        RetrievalTrace trace
) {

    public RetrievedEvidenceBundle(List<Document> documents, EvidenceLevel evidenceLevel, String evidenceGuidance) {
        this(documents, evidenceLevel, evidenceGuidance, null);
    }

    public static RetrievedEvidenceBundle empty() {
        return new RetrievedEvidenceBundle(
                List.of(),
                EvidenceLevel.NONE,
                "当前没有可用证据，必须直接拒答。"
        );
    }
}
