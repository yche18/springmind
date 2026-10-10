package com.yche.springmind.qa.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;

/** A static, printable review packet; model rankings never replace human judgments. */
final class AnnotationReview {
    private AnnotationReview() {}

    static String render(RerankEvaluation.Dataset fixture, RerankEvaluation.Dataset snapshot,
            JsonNode report, Map<String, String> notes) {
        Map<String, JsonNode> rows = new LinkedHashMap<>();
        report.path("rows").forEach(row -> rows.put(row.path("id").asText(), row));
        StringBuilder html = new StringBuilder("""
                <!doctype html><html lang="zh-CN"><meta charset="utf-8">
                <meta name="viewport" content="width=device-width,initial-scale=1">
                <title>Northstar 精排评测 · 标注复核</title>
                <style>
                body{font:16px/1.65 system-ui,sans-serif;color:#172b40;background:#f2f5f8;margin:0}
                main{max-width:1100px;margin:auto;padding:32px}h1,h2,h3{line-height:1.3}
                section,.card{background:white;padding:24px;border-radius:12px;margin:20px 0;border:1px solid #dbe3eb}
                .notice{border-left:5px solid #ce8b18;background:#fff8e8;padding:18px}
                table{width:100%;border-collapse:collapse;margin:12px 0}td,th{padding:10px;border-bottom:1px solid #dbe3eb;text-align:left;vertical-align:top}
                th{background:#edf2f7}a{color:#165fa7}code{font-size:13px;word-break:break-all}
                .muted{color:#53677b}.flag{color:#9c3e13;font-weight:600}details{margin:12px 0}
                summary{cursor:pointer;font-weight:600}blockquote{margin:8px 0;padding:12px 16px;background:#f5f7fa;white-space:pre-wrap}
                nav a{display:inline-block;padding:3px 8px}.metric td{font-variant-numeric:tabular-nums}
                @media print{body{background:white}main{padding:0}section{break-inside:avoid}details{display:block}}
                </style><main><h1>Northstar 精排评测：待复核标注</h1>
                <p class="notice"><strong>初步结果，标签尚未经过人工确认。</strong>语料是虚构公司制度；候选顺序来自项目真实检索。
                固定原始问题，没有调用查询改写或答案生成模型。不是生产效果，也不是最终回答正确率。</p>
                <p>你不需要编辑 JSON。可直接反馈：<code>q16：group-access 改为 0；q25：确认无答案</code>。
                也可使用配套 review-template.json 记录修改。先复核证据，再看排序；不要为了让结果更好而修改标签。</p>
                <div class="card"><h2>这轮实际测到了什么</h2>
                """);
        html.append("<p>模型：<code>").append(escape(report.path("model").asText())).append("</code>；截止 k=")
                .append(report.path("k").asInt()).append("；超时 ").append(report.path("timeoutMs").asInt())
                .append(" ms；调用状态：<code>").append(escape(report.path("summary").path("statuses").toString())).append("</code></p>");
        if ("OFFLINE_RELABEL".equals(report.path("mode").asText())) {
            html.append("<p class='notice'>当前采用统一分级 v2：q17–q21、q24 的两份必要证据均为 2 分。")
                    .append("只更新标签并离线重算，未再次调用 Qwen；排名、状态和耗时均沿用原始实测。")
                    .append("评分口径已按用户要求统一，但不表示其余标签全部经过人工确认。</p>");
        }
        html.append("<table class='metric'><tr><th>指标（").append(report.path("summary").path("judgedAnswerableCases").asInt())
                .append(" 道有答案题）</th><th>原始 RRF</th><th>Qwen 后</th><th>差值</th></tr>");
        for (String metric : List.of("hit", "recall", "mrr", "ndcg")) {
            var summary = report.path("summary");
            html.append("<tr><td>").append(escape(metric)).append("@").append(report.path("k").asInt()).append("</td><td>").append(number(summary.path("baseline").path(metric)))
                    .append("</td><td>").append(number(summary.path("reranked").path(metric))).append("</td><td>")
                    .append(number(summary.path("delta").path(metric))).append("</td></tr>");
        }
        html.append("</table><p>耗时 P50 / P95：").append(number(report.path("summary").path("latencyP50Ms")))
                .append(" / ").append(number(report.path("summary").path("latencyP95Ms"))).append(" ms。</p>")
                .append("<p>1～3 分在当前 Hit/Recall/MRR 中都计为相关；nDCG 区分等级。6 道无答案题不计入这些平均分，也未测拒答率。")
                .append("候选仅 16 份短英文资料，全部召回不是大规模检索能力的证明。费用以服务商账单为准。</p></div>")
                .append("<div class='card'><h2>复核口径</h2><p>3：单份证据足以完整回答整个问题；2：直接支持部分答案，完整回答还需要其他证据；1：仅提供背景，不能直接支持答案；0：无关。")
                .append("不能因检索到多份证据就自动降分：若单份已能完整回答，仍为 3 分。所有默认 0 分项也需要复核。</p>")
                .append("<p class='flag'>优先检查 q16 的背景证据；q25–q30 需核对整个语料确实无答案。多证据题的分级已按统一口径修订。</p><nav>");
        for (var example : snapshot.cases()) html.append("<a href='#").append(escape(example.id())).append("'>").append(escape(example.id())).append("</a>");
        html.append("</nav></div>");
        for (var example : snapshot.cases()) {
            JsonNode row = rows.get(example.id());
            if (row == null || !notes.containsKey(example.id())) throw new IllegalArgumentException("Missing report row or review note");
            List<String> output = new ArrayList<>();
            row.path("outputOrder").forEach(id -> output.add(id.asText()));
            html.append("<section id='").append(escape(example.id())).append("'><h2>").append(escape(example.id())).append(" · ")
                    .append(escape(example.query())).append("</h2><p><strong>待确认：</strong>").append(escape(notes.get(example.id())))
                    .append("</p><p class='muted'>分类：").append(escape(example.category())).append("；精排状态：")
                    .append(escape(row.path("status").asText())).append("。本页没有模型生成的答案。</p>")
                    .append("<table><tr><th>当前正相关证据</th><th>当前分数</th><th>RRF 排名</th><th>精排排名</th></tr>");
            if (example.relevance().isEmpty()) html.append("<tr><td colspan='4'>当前判断：无答案，全部证据为 0 分。需人工确认。</td></tr>");
            example.relevance().forEach((id, grade) -> html.append("<tr><td>").append(escape(sourceId(id))).append("</td><td>")
                    .append(grade).append("</td><td>").append(rank(example.candidates(), id)).append("</td><td>")
                    .append(rank(output, id)).append("</td></tr>"));
            html.append("</table><details><summary>查看精排前后 Top 5（只用于定位，不用于决定标签）</summary><p>RRF：")
                    .append(escape(example.candidates().stream().limit(5).map(AnnotationReview::sourceId).toList().toString()))
                    .append("</p><p>Qwen：").append(escape(output.stream().limit(5).map(AnnotationReview::sourceId).toList().toString()))
                    .append("</p></details><details><summary>复核全部 16 份原文与当前标签（包含默认 0 分项）</summary>");
            for (var source : fixture.documents().entrySet()) {
                String id = RetrievalSnapshot.key(example.id(), source.getKey());
                int grade = example.relevance().getOrDefault(id, 0);
                html.append("<h3>").append(escape(source.getKey())).append(" · 当前 ").append(grade).append(" 分</h3><blockquote>")
                        .append(escape(source.getValue())).append("</blockquote>");
            }
            html.append("</details><p>复核结论：□ 保持初稿　□ 修改相关性　□ 修改有/无答案判断　□ 问题需要重写</p>")
                    .append("<p class='muted'>请反馈题号、来源 ID、修改后的分数及原因；无需重新调用模型即可重算指标。</p></section>");
        }
        return html.append("</main></html>").toString();
    }

    static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
    private static String number(JsonNode node) { return node.isNumber() ? String.format(Locale.ROOT, "%.4f", node.asDouble()) : "未测量"; }
    private static String rank(List<String> ids, String id) { int rank = ids.indexOf(id); return rank < 0 ? "未召回" : Integer.toString(rank + 1); }
    private static String sourceId(String id) { return id.substring(id.indexOf("__") + 2); }
}
