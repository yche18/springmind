package com.yche.springmind.qa.model.vo;

import com.yche.springmind.qa.model.QueryPlanResult;
import java.util.Map;

public record RetrievalTrace(QueryPlanResult queryPlan, Map<String, Long> timingsMs,
                             int vectorHits, int keywordHits, int candidateCount) {
    public RetrievalTrace {
        timingsMs = Map.copyOf(timingsMs);
    }
}
