package com.yche.springmind.qa.evaluation;

import java.util.*;

final class ExtensionReport {
    private ExtensionReport() {}

    static String review(ExtensionFixture fixture, ExtensionEvaluation.Corpus corpus) {
        return review(fixture, corpus, false);
    }

    static String review(ExtensionFixture fixture, ExtensionEvaluation.Corpus corpus, boolean reviewed) {
        StringBuilder html = start(reviewed ? "Harborview 扩展集：人工复核已通过" : "Harborview 扩展集：待人工复核");
        html.append("<p class='notice'>24 道新题、66 份虚构文档、").append(corpus.chunks().size())
                .append(reviewed ? " 个实际切片。<strong>人工复核已通过，全部标注保持不变。</strong>确认覆盖 24 题、2,520 个切片分级及无答案判断；不代表真实业务准确率。<a href='review-approval.json'>查看确认记录</a>。原有 30 题及其结果未修改。</p>"
                        : " 个实际切片。全部标注仍为<strong>待人工复核</strong>，不是已经确认的真实标准答案。原有 30 题及其结果未修改。</p>")
                .append(reviewed ? "<h2>已复核的范围与分级口径</h2>" : "<h2>你需要核对什么？</h2>")
                .append("<ol><li>问题是否清楚，地区、版本、环境是否明确；应答要点是否正确且完整。</li>")
                .append("<li>3 分：单个切片足以回答整题；2 分：直接支持部分答案；1 分：仅背景；0 分：无关。初稿按精确证据短句自动投影，人工复核范围包含间接支持或边界片段。</li>")
                .append("<li>核对正向标注，也检查默认 0 分，尤其是相似规则和长文相邻片段；多证据题应包含全部必要事实。</li>")
                .append("<li>e21–e24 要检查全部语料是否真的无答案，不能因为某路没检索到就认定无答案。</li></ol>")
                .append("<p>可直接反馈：<code>e04：问题限定不足……</code> 或 <code>e19：restore-guide-en--c003 改为 2，原因……</code>。")
                .append("可查看 <a href='report.html'>三组对照结果</a>；不需要编辑 JSON，也不要为了让某种方案胜出而改标签。</p>")
                .append("<p>本页标注对应：<code>").append(esc(corpus.fixtureSha256())).append("</code>。")
                .append(reviewed ? "人工确认记录绑定原始语料哈希；未重新检索或调用模型。</p><nav>" : "所有题的结论确认后再记录人工通过状态。</p><nav>");
        for (var question : fixture.cases()) html.append("<a href='#").append(question.id()).append("'>").append(question.id()).append("</a> ");
        html.append(" · <a href='#corpus'>完整原文</a></nav>");
        for (var question : fixture.cases()) {
            html.append("<section id='").append(question.id()).append("'><h2>").append(esc(question.id() + " · " + question.query())).append("</h2>")
                    .append("<p class='muted'>").append(esc(category(question.category()) + " / " + question.language()))
                    .append(reviewed ? " · 已确认</p><p><strong>应答要点：</strong>" : " · 待确认</p><p><strong>应答要点：</strong>")
                    .append(esc(question.expected())).append(reviewed ? "</p><h3>已确认的证据定位</h3>" : "</p><h3>初始证据定位（未获人工确认）</h3>");
            if (question.evidence().isEmpty()) html.append(reviewed ? "<p>已确认：本语料中无答案；不是依据某路检索失败判定。</p>" : "<p>初稿判为无答案：请核对所有文档，而非仅查看检索前五名。</p>");
            for (var evidence : question.evidence()) html.append("<blockquote><a href='#doc-").append(esc(evidence.document())).append("'>")
                    .append(esc(evidence.document())).append("</a> · 事实 ").append(esc(evidence.id())).append("<p>")
                    .append(esc(evidence.quote())).append("</p></blockquote>");
            html.append("<h3>当前正向切片</h3>");
            var grades = corpus.grades().get(question.id());
            for (var entry : corpus.chunks().entrySet()) if (grades.get(entry.getKey()) > 0) chunk(html, entry.getKey(), entry.getValue(), grades.get(entry.getKey()));
            html.append("<details><summary>检查全部 ").append(corpus.chunks().size()).append(" 个实际切片（含默认 0 分）</summary>");
            for (var entry : corpus.chunks().entrySet()) chunk(html, entry.getKey(), entry.getValue(), grades.get(entry.getKey()));
            html.append(reviewed ? "</details><p>复核结论：已确认，保持初稿。</p></section>" : "</details><p>复核结论：□ 保持初稿　□ 修改分级/证据　□ 修改有无答案判断　□ 问题需澄清</p></section>");
        }
        html.append("<section id='corpus'><h2>完整语料原文（66 份）</h2><p>标签投影基于实际切片；这里保留原文，便于检查切片边界或漏标。</p>");
        for (var entry : fixture.documents().entrySet()) html.append("<details id='doc-").append(esc(entry.getKey())).append("'><summary>")
                .append(esc(entry.getKey())).append("</summary><pre>").append(esc(entry.getValue())).append("</pre></details>");
        return html.append("</section></main></html>").toString();
    }

    static String report(ExtensionFixture fixture, ExtensionEvaluation.Comparison comparison,
                         ExtensionEvaluation.Capture capture) {
        boolean reviewed = "HUMAN_REVIEWED".equals(comparison.annotationStatus());
        StringBuilder html = start(reviewed ? "Harborview 扩展集：三组对照（标注已复核）" : "Harborview 扩展集：三组对照（临时指标）");
        html.append(reviewed ? "<p class='notice'><strong>标注人工复核已通过，分级与指标不变。</strong>查看 <a href='review.html'>复核页面</a>及 <a href='review-approval.json'>确认记录</a>。仍为诊断性合成数据，不代表生产效果。三组均使用原始真实检索命中；未重新调用模型。</p>"
                        : "<p class='notice'><strong>初始标注尚待人工复核。</strong>先看 <a href='review.html'>复核页面</a>。三组均使用同一组真实检索命中；未调用 Qwen 或聊天模型。</p>")
                .append("<p>主要排名指标按<strong>聚类与窗口扩展前的切片</strong>计算：每路最多 50 个，候选池最多 30 个，评估前 5 名。")
                .append("语料和标签在查询前冻结。窗口事实覆盖单列，不与切片排名混算。</p>")
                .append("<p>20 道有答案题参与排序均值，4 道无答案题不算正确拒答。事实覆盖按不同答案要点去重；重叠切片不会增加事实数量。")
                .append("跨语言题中有 2 道、多证据题 2 道、长文题 2 道需要多个事实，共 6 道。</p>");
        for (var group : comparison.groups().entrySet()) {
            html.append("<section><h2>").append(esc(category(group.getKey()))).append("</h2><table><tr><th>方案</th><th>有答案/全部</th><th>Hit@5</th><th>Recall@5</th><th>MRR@5</th><th>nDCG@5</th><th>候选切片召回</th><th>多事实齐全@5</th></tr>");
            for (var entry : group.getValue().entrySet()) {
                var value = entry.getValue();
                var m = value.chunkMetrics();
                html.append("<tr><td>").append(name(entry.getKey())).append("</td><td>").append(value.answerable()).append(" / ").append(value.cases()).append("</td>");
                for (Double n : Arrays.asList(m == null ? null : m.hit(), m == null ? null : m.recall(), m == null ? null : m.mrr(),
                        m == null ? null : m.ndcg(), value.candidateChunkRecall())) html.append("<td>").append(number(n)).append("</td>");
                html.append("<td>").append(value.complementaryCases() == 0 ? "—" : value.completeComplementaryTop5() + " / " + value.complementaryCases()).append("</td></tr>");
            }
            html.append("</table><h3>不同阶段的独立事实覆盖</h3><table><tr><th>方案</th><th>原始命中（每路≤50）</th><th>候选切片≤30</th><th>切片前5</th><th>精排入口窗口前5</th><th>最终选中窗口</th></tr>");
            for (var entry : group.getValue().entrySet()) {
                var value = entry.getValue();
                html.append("<tr><td>").append(name(entry.getKey())).append("</td>");
                for (Double n : Arrays.asList(value.rawChannelFactCoverage(), value.candidateFactCoverage(), value.top5FactCoverage(),
                        value.windowTop5FactCoverage(), value.selectedFactCoverage())) html.append("<td>").append(number(n)).append("</td>");
                html.append("</tr>");
            }
            html.append("</table></section>");
        }
        html.append("<section><h2>全部题目的变化（双路相对向量，nDCG@5）</h2><ul>");
        for (var row : comparison.rows()) {
            var vector = row.scores().get("VECTOR").chunkMetrics();
            var hybrid = row.scores().get("HYBRID").chunkMetrics();
            String change = vector == null ? "无答案，不评分" : hybrid.ndcg() > vector.ndcg() + 1e-12 ? "改善" : hybrid.ndcg() < vector.ndcg() - 1e-12 ? "退化" : "不变";
            html.append("<li><a href='#").append(row.id()).append("'>").append(row.id()).append("</a> · ").append(change).append("</li>");
        }
        html.append("</ul></section>");
        for (int i = 0; i < fixture.cases().size(); i++) {
            var question = fixture.cases().get(i);
            var row = comparison.rows().get(i);
            var captured = capture.rows().get(i);
            html.append("<section id='").append(question.id()).append("'><h2>").append(esc(question.id() + " · " + question.query()))
                    .append("</h2><p><a href='review.html#").append(question.id()).append("'>查看应答要点与全部标注</a>；向量命中 ")
                    .append(captured.vector().size()).append("，关键词命中 ").append(captured.keyword().size()).append("。</p>");
            for (String mode : ExtensionEvaluation.MODES) {
                var score = row.scores().get(mode);
                html.append("<h3>").append(name(mode)).append("</h3><p>切片 Recall@5：")
                        .append(number(score.chunkMetrics() == null ? null : score.chunkMetrics().recall())).append("；nDCG@5：")
                        .append(number(score.chunkMetrics() == null ? null : score.chunkMetrics().ndcg())).append("；切片事实覆盖：")
                        .append(number(score.top5FactCoverage())).append("；最终窗口事实覆盖：").append(number(score.selectedFactCoverage())).append("。</p><ol>");
                for (String key : captured.orders().get(mode).stream().limit(5).toList()) html.append("<li><code>").append(esc(key)).append("</code></li>");
                html.append("</ol><details><summary>查看实际选中证据窗口</summary>");
                for (var window : captured.selectedWindows().get(mode)) html.append("<h4>").append(esc(window.document())).append("</h4><pre>")
                        .append(esc(window.text())).append("</pre>");
                html.append("</details>");
            }
            html.append("</section>");
        }
        return html.append("<p class='muted'>这是一套诊断性合成数据，不代表真实业务整体准确率；没有评测答案生成或拒答。固定输入、原始命中、窗口和哈希见 JSON 文件。</p></main></html>").toString();
    }

    private static void chunk(StringBuilder html, String id, ExtensionEvaluation.Chunk chunk, int grade) {
        html.append("<details><summary><code>").append(esc(id)).append("</code> · 当前 ").append(grade).append(" 分</summary><pre>")
                .append(esc(chunk.text())).append("</pre></details>");
    }

    private static StringBuilder start(String title) {
        return new StringBuilder("<!doctype html><html lang='zh'><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'><title>")
                .append(esc(title)).append("</title><style>body{font:16px/1.65 system-ui,sans-serif;background:#f1f5f9;color:#203044;margin:0}main{max-width:1180px;margin:auto;padding:28px}section{background:#fff;padding:24px;border:1px solid #dde4eb;border-radius:12px;margin:22px 0;overflow:auto}h1,h2{line-height:1.35}table{width:100%;border-collapse:collapse;font-variant-numeric:tabular-nums}th,td{text-align:left;padding:9px;border-bottom:1px solid #dde4eb}th{background:#eaf1f7}a{color:#145ca6}details{border:1px solid #dce4ed;padding:10px;margin:9px 0;border-radius:6px}summary{cursor:pointer}pre{white-space:pre-wrap;overflow-wrap:anywhere;font:inherit;background:#f7fafc;padding:12px}code{overflow-wrap:anywhere}.muted{color:#52657a}.notice{border-left:4px solid #c28314;padding:12px;background:#fff8e8}blockquote{border-left:3px solid #87abb9;padding-left:14px}nav a{display:inline-block;padding:5px}li{margin:6px 0}</style><main><h1>")
                .append(esc(title)).append("</h1>");
    }

    private static String name(String mode) { return switch (mode) { case "KEYWORD" -> "仅关键词"; case "VECTOR" -> "仅向量"; default -> "双路 RRF"; }; }
    private static String category(String category) { return switch (category) {
        case "ALL" -> "总体"; case "zh" -> "中文问题"; case "en" -> "英文问题"; case "exact" -> "精确匹配";
        case "semantic" -> "同义表达"; case "cross-language" -> "跨语言"; case "multi-evidence" -> "多证据";
        case "long-document" -> "长文分块"; default -> "无答案";
    }; }
    private static String number(Double value) { return value == null ? "—" : String.format(Locale.ROOT, "%.4f", value); }
    private static String esc(String text) { return AnnotationReview.escape(text); }
}
