package com.yche.springmind.qa.evaluation;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;

/** Recompute all results from the saved two-arm capture, entirely offline. */
class FilenameAblationReportRun {
    @Test
    void generate() throws Exception {
        var output = FilenameAblation.output();
        var mapper = RerankEvaluationRun.datasetMapper();
        var capture = mapper.readValue(output.resolve("capture.json").toFile(), FilenameAblation.Capture.class);
        var comparison = FilenameAblation.compare(capture, RetrievalAblation.hash(output.resolve("capture.json")));
        mapper.writerWithDefaultPrettyPrinter().writeValue(output.resolve("comparison.json").toFile(), comparison);
        Files.writeString(output.resolve("report.html"), render(capture, comparison));
        System.out.println("Filename ablation report: original rank matches=" + comparison.originalOrderMatches()
                + "/24; original score matches=" + comparison.originalScoreMatches() + "/24; " + output.toAbsolutePath());
    }

    static String render(FilenameAblation.Capture capture, FilenameAblation.Comparison comparison) throws Exception {
        var fixture = ExtensionFixture.read();
        var corpus = FilenameAblation.corpus();
        var html = new StringBuilder("""
                <!doctype html><html lang="zh"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
                <title>Harborview：文件名消融实验</title><style>
                body{font:16px/1.65 system-ui,sans-serif;background:#f1f5f9;color:#203044;margin:0}
                main{max-width:1200px;margin:auto;padding:28px}section{background:white;padding:24px;border:1px solid #dce4ed;border-radius:12px;margin:24px 0;overflow:auto}
                table{border-collapse:collapse;width:100%;font-variant-numeric:tabular-nums}th,td{padding:10px;border-bottom:1px solid #dce4ed;text-align:left}th{background:#eaf1f7}
                pre{white-space:pre-wrap;overflow-wrap:anywhere;background:#f7fafc;padding:12px}code{overflow-wrap:anywhere}a{color:#145ca6}
                details{border:1px solid #dce4ed;border-radius:6px;padding:10px;margin:10px 0}summary{cursor:pointer}.notice{background:#fff8e8;border-left:4px solid #c28314;padding:14px}
                </style><main><h1>Harborview：文件名消融实验</h1>
                <p class="notice">复用人工确认的 24 题、66 份虚构资料、105 个实际切片；没有调整标注。两组共 48 次真实 Elasticsearch 检索；Qwen、聊天与嵌入模型调用均为 0。</p>
                <p>仅改变文件名字段：正文组从初始查询和重评分两处移除 fileName 条件，正文权重、分词、短语匹配、过滤与候选数均保持不变。
                两组共享同一个时间点搜索视图，未修改索引。结果是窗口扩展前的切片排名，不是答案准确率、拒答率或最终上下文质量。</p>
                <p>20 道有答案题计入排序均值；4 道无答案题单列且不计作正确拒答。完整标注见
                <a href="../harborview-2026-10-10/review.html">已确认复核页</a>。<a href="README.md">实验结论与局限</a>。</p>
                """);
        html.append("<p>正文＋文件名组与上次记录一致：完整前 50 排名 ").append(comparison.originalOrderMatches())
                .append("/24 题，排名及原始分数 ").append(comparison.originalScoreMatches()).append("/24 题。</p>");
        for (var group : comparison.groups().entrySet()) {
            html.append("<section><h2>").append(category(group.getKey())).append("</h2><table><tr><th>方案</th><th>有答案/总数</th><th>Hit@5</th><th>Recall@5</th><th>MRR@5</th><th>nDCG@5</th><th>Recall@30</th><th>Recall@50</th><th>事实覆盖@5</th><th>多事实齐全@5</th></tr>");
            for (String mode : FilenameAblation.MODES) {
                var value = group.getValue().get(mode);
                var metric = value.metrics();
                html.append("<tr><td>").append(name(mode)).append("</td><td>").append(value.answerable()).append("/").append(value.cases()).append("</td>");
                for (Double n : Arrays.asList(metric == null ? null : metric.hit(), metric == null ? null : metric.recall(),
                        metric == null ? null : metric.mrr(), metric == null ? null : metric.ndcg(), value.recall30(), value.recall50(), value.factCoverage5()))
                    html.append("<td>").append(number(n)).append("</td>");
                html.append("<td>").append(value.complementaryCases() == 0 ? "—" : value.completeComplementaryCases() + "/" + value.complementaryCases()).append("</td></tr>");
            }
            html.append("</table></section>");
        }
        int wins = 0, losses = 0, ties = 0;
        html.append("<section><h2>全部题目：加上文件名后发生了什么？</h2><ul>");
        for (var row : comparison.rows()) {
            var before = row.scores().get("BODY_ONLY").metrics();
            var after = row.scores().get("BODY_AND_FILENAME").metrics();
            String change = "无答案，不评分";
            if (before != null) {
                double delta = after.ndcg() - before.ndcg();
                if (delta > 1e-12) { wins++; change = "改善"; }
                else if (delta < -1e-12) { losses++; change = "退化"; }
                else { ties++; change = "不变"; }
            }
            html.append("<li><a href='#").append(row.id()).append("'>").append(row.id()).append("</a>：").append(change).append("</li>");
        }
        html.append("</ul><p>按每题 nDCG@5：改善 ").append(wins).append("，退化 ").append(losses).append("，不变 ").append(ties).append("；不包含 4 道无答案题。</p></section>");
        for (int i = 0; i < fixture.cases().size(); i++) {
            var question = fixture.cases().get(i);
            var row = comparison.rows().get(i);
            var saved = capture.rows().get(i);
            html.append("<section id='").append(question.id()).append("'><h2>").append(esc(question.id() + " · " + question.query())).append("</h2><p>")
                    .append(category(question.category())).append(" · ").append(question.language()).append("</p><p>已确认应答要点：").append(esc(question.expected()))
                    .append("</p><table><tr><th>相关切片</th><th>分级</th><th>仅正文排名</th><th>正文＋文件名排名</th></tr>");
            for (var label : corpus.grades().get(question.id()).entrySet()) if (label.getValue() > 0) {
                html.append("<tr><td><code>").append(esc(label.getKey())).append("</code></td><td>").append(label.getValue()).append("</td>");
                for (String mode : FilenameAblation.MODES) {
                    var ranking = saved.hits().get(mode).stream().map(ExtensionEvaluation.Hit::chunk).toList();
                    int rank = ranking.indexOf(label.getKey()) + 1;
                    html.append("<td>").append(rank == 0 ? "未进入前50" : Integer.toString(rank)).append("</td>");
                }
                html.append("</tr>");
            }
            html.append("</table>");
            if (question.evidence().isEmpty()) html.append("<p>本语料已确认为无答案。召回相似片段不等于系统已经生成错误答案；本实验不运行生成或拒答。</p>");
            for (String mode : FilenameAblation.MODES) {
                var metric = row.scores().get(mode).metrics();
                html.append("<h3>").append(name(mode)).append("</h3><p>命中数量：").append(saved.hits().get(mode).size()).append("；nDCG@5：")
                        .append(number(metric == null ? null : metric.ndcg())).append("</p><ol>");
                for (var hit : saved.hits().get(mode).stream().limit(5).toList()) html.append("<li><code>").append(esc(hit.chunk())).append("</code> · 分级 ")
                        .append(corpus.grades().get(question.id()).get(hit.chunk())).append("<details><summary>证据正文</summary><pre>")
                        .append(esc(corpus.chunks().get(hit.chunk()).text())).append("</pre></details></li>");
                html.append("</ol><details><summary>实际查询（不含临时搜索上下文 ID）</summary><pre>")
                        .append(esc(saved.requests().get(mode).toPrettyString())).append("</pre></details>");
            }
            html.append("</section>");
        }
        return html.append("<p>结论仅适用于当前已审阅的合成资料和配置。保留了文件名中的英文别名，不能推断关键词具备跨语言语义能力。没有调参、改题或挑选重复运行。</p></main></html>").toString();
    }

    private static String name(String mode) { return mode.equals("BODY_ONLY") ? "仅正文" : "正文＋文件名"; }
    private static String category(String category) { return switch (category) {
        case "ALL" -> "总体"; case "zh" -> "中文问题"; case "en" -> "英文问题"; case "exact" -> "精确匹配";
        case "semantic" -> "同义表达"; case "cross-language" -> "跨语言"; case "multi-evidence" -> "多证据";
        case "long-document" -> "长文分块"; default -> "无答案";
    }; }
    private static String number(Double value) { return value == null ? "—" : String.format(Locale.ROOT, "%.4f", value); }
    private static String esc(String text) { return AnnotationReview.escape(text); }
}
