package com.yche.springmind.qa.rag;

import com.yche.springmind.qa.model.EvidenceLevel;
import org.springframework.ai.document.Document;

import java.util.List;

/**
 * 封装检索证据、证据等级和检索诊断信息，供回答阶段统一消费。
 *
 * <p>位于 RAG 检索链路：把问题转换为受知识组权限约束的证据集合，供回答生成阶段使用。</p>
 */
public record RetrievedEvidenceBundle(
        List<Document> documents,
        EvidenceLevel evidenceLevel,
        String evidenceGuidance
) {

    /**
     * 执行 {@code empty} 对应的业务步骤。
     *
     * @return 方法执行结果，具体结构由返回类型 {@code RetrievedEvidenceBundle} 表示
     */
    public static RetrievedEvidenceBundle empty() {
        return new RetrievedEvidenceBundle(
                List.of(),
                EvidenceLevel.NONE,
                "当前没有可用证据，必须直接拒答。"
        );
    }
}
