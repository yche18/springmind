package com.yche.springmind.qa.evaluation;

import java.util.*;

final class RetrievalAblationReport {
    private RetrievalAblationReport() {}

    static String render(RetrievalAblation.Comparison report) {
        StringBuilder html = new StringBuilder("""
                <!doctype html><html lang="zh"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
                <title>Northstar 单路与双路检索对照</title><style>
                body{font:16px/1.65 system-ui,sans-serif;color:#203044;background:#f2f5f8;margin:0}main{max-width:1180px;margin:auto;padding:28px}
                section{background:white;padding:24px;border:1px solid #dde4eb;border-radius:12px;margin:20px 0;overflow:auto}
                h1,h2{line-height:1.3}table{width:100%;border-collapse:collapse;font-variant-numeric:tabular-nums}th,td{text-align:left;padding:10px;border-bottom:1px solid #e2e8f0}
                th{background:#eaf1f7}small,.muted{color:#52657a}a{color:#145ca6}code{overflow-wrap:anywhere}li{margin:5px 0}.notice{border-left:4px solid #176b78;padding-left:16px}
                </style><main><h1>单路与双路检索：30 题对照</h1>
                <p class="notice">同一套人工确认的 v2 标注；关键词与向量使用真实本地服务采集。只关闭指定通道，复用生产融合与候选组织逻辑。
                Qwen 两轮排序来自已有实测，本次新增 Qwen／聊天请求为 0。</p>
                <p>衡量的是<strong>精排入口处候选排序的 Top 5</strong>，不是最终回答准确率。24 道有答案题参与均值；6 道无答案题不计为“拒答正确”。
                Hit / Recall / MRR 将背景 1 分也视为相关；nDCG 使用 0–3 分。多证据齐全率仅统计 6 道需要互补证据的题。</p>
                <p class="muted">限制：仅 16 篇英文短文，每题向量通道都召回全库；中文查询与英文关键词存在语言错配。
                本报告不证明大规模召回、最终答案、权限安全或生产延迟。不能据此声称双路必然优于单路。</p>
                """);
        for (var group : report.groups().entrySet()) {
            html.append("<section><h2>").append(escape(groupName(group.getKey()))).append("</h2><table><thead><tr><th>方案</th><th>有答案 / 全部</th><th>Hit@5</th><th>Recall@5</th><th>MRR@5</th><th>nDCG@5</th><th>候选召回率</th><th>多证据齐全</th></tr></thead><tbody>");
            for (var entry : group.getValue().entrySet()) {
                var value = entry.getValue();
                var m = value.metrics();
                html.append("<tr><td>").append(escape(name(entry.getKey()))).append("</td><td>").append(value.answerable()).append(" / ").append(value.questions()).append("</td>");
                for (Double number : Arrays.asList(m == null ? null : m.hit(), m == null ? null : m.recall(),
                        m == null ? null : m.mrr(), m == null ? null : m.ndcg(), value.candidateRecall())) html.append("<td>").append(format(number)).append("</td>");
                html.append("<td>").append(value.multiEvidenceQuestions() == 0 ? "—" : value.multiEvidenceComplete() + " / " + value.multiEvidenceQuestions()).append("</td></tr>");
            }
            html.append("</tbody></table></section>");
        }
        html.append("<section><h2>逐题变化：双路 RRF 对比向量单路</h2><p>按 nDCG@5 判断改善 / 退化，所有题目均保留，不只展示正面案例。</p><ul>");
        for (var row : report.rows()) {
            var vector = row.metrics().get("VECTOR");
            var hybrid = row.metrics().get("HYBRID");
            String change = vector == null ? "无答案：不评分" : hybrid.ndcg() > vector.ndcg() + 1e-12 ? "改善" : hybrid.ndcg() < vector.ndcg() - 1e-12 ? "退化" : "不变";
            html.append("<li><a href='#").append(escape(row.id())).append("'>").append(escape(row.id())).append("</a> · ").append(change)
                    .append(" · ").append(escape(row.query())).append("</li>");
        }
        html.append("</ul></section>");
        for (var row : report.rows()) {
            html.append("<section id='").append(escape(row.id())).append("'><h2>").append(escape(row.id() + " · " + row.query()))
                    .append("</h2><p>关键词命中：").append(row.keywordHits()).append("；向量命中：").append(row.vectorHits())
                    .append("。已确认正向标注：<code>").append(escape(row.relevance().toString())).append("</code></p>")
                    .append("<table><tr><th>方案</th><th>前 5 名：来源（相关性等级）</th><th>Recall@5</th><th>nDCG@5</th><th>互补证据齐全</th></tr>");
            for (var entry : row.orders().entrySet()) {
                var metrics = row.metrics().get(entry.getKey());
                html.append("<tr><td>").append(escape(name(entry.getKey()))).append("</td><td><ol>");
                for (String id : entry.getValue().stream().limit(5).toList()) html.append("<li>").append(escape(id.substring(row.id().length() + 2)))
                        .append("（").append(row.relevance().getOrDefault(id, 0)).append("）</li>");
                html.append("</ol>").append(entry.getValue().isEmpty() ? "无命中" : "").append("</td><td>")
                        .append(format(metrics == null ? null : metrics.recall())).append("</td><td>").append(format(metrics == null ? null : metrics.ndcg()))
                        .append("</td><td>").append(row.allRequiredEvidence().get(entry.getKey()) == null ? "—" : row.allRequiredEvidence().get(entry.getKey()) ? "是" : "否").append("</td></tr>");
            }
            html.append("</table></section>");
        }
        return html.append("<p class='muted'>数据版本 SHA-256：<code>").append(escape(report.datasetSha256()))
                .append("</code>。完整排序、原始通道分数与文件哈希见 capture.json / comparison.json；方法及具体案例见 README.md。</p></main></html>").toString();
    }

    private static String groupName(String group) {
        return switch (group) {
            case "ALL" -> "总体结果"; case "zh" -> "中文问题"; case "en" -> "英文问题";
            case "direct" -> "直接提问"; case "paraphrase" -> "语义改写题";
            case "multi-evidence" -> "多证据题"; default -> "无答案题（不计入排序均值）";
        };
    }

    private static String name(String mode) {
        return switch (mode) {
            case "KEYWORD" -> "仅关键词"; case "VECTOR" -> "仅向量"; case "HYBRID" -> "双路 RRF";
            case "QWEN_2S_REUSED" -> "双路 + Qwen 2s（复用，含 3 次回退）";
            case "QWEN_5S_REUSED" -> "双路 + Qwen 5s（复用）";
            default -> mode;
        };
    }

    private static String format(Double value) { return value == null ? "—" : String.format(Locale.ROOT, "%.4f", value); }
    private static String escape(String value) { return AnnotationReview.escape(value); }
}
