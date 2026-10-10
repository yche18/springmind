package com.yche.springmind.qa.evaluation;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;

/** Offline report, including model regressions and historical retrieval drift. */
class EmbeddingComparisonReportRun {
    @Test
    void generate() throws Exception {
        Path root = EmbeddingComparison.output();
        var comparison = EmbeddingComparison.compare(root);
        RerankEvaluationRun.datasetMapper().writerWithDefaultPrettyPrinter().writeValue(root.resolve("comparison.json").toFile(), comparison);
        Files.writeString(root.resolve("report.html"), render(root, comparison));
        System.out.println("Local multilingual model report: " + root.toAbsolutePath());
        System.out.println(RerankEvaluationRun.datasetMapper().writeValueAsString(comparison.groups().get("ALL")));
    }

    static String render(Path root, EmbeddingComparison.Comparison comparison) throws Exception {
        var fixture = ExtensionFixture.read();
        var corpus = FilenameAblation.corpus();
        var mapper = RerankEvaluationRun.datasetMapper();
        var html = new StringBuilder("""
                <!doctype html><html lang="zh"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
                <title>Harborview：BGE-M3 本地向量模型对照</title><style>
                body{font:16px/1.65 system-ui,sans-serif;background:#f1f5f9;color:#203044;margin:0}main{max-width:1280px;margin:auto;padding:28px}
                section{background:white;padding:24px;border:1px solid #dce4ed;border-radius:12px;margin:24px 0;overflow:auto}
                table{border-collapse:collapse;width:100%;font-variant-numeric:tabular-nums}th,td{padding:10px;border-bottom:1px solid #dce4ed;text-align:left}th{background:#eaf1f7}
                pre{white-space:pre-wrap;overflow-wrap:anywhere;background:#f7fafc;padding:12px}code{overflow-wrap:anywhere}a{color:#145ca6}
                details{border:1px solid #dce4ed;border-radius:6px;padding:10px;margin:10px 0}summary{cursor:pointer}.notice{background:#fff8e8;border-left:4px solid #c28314;padding:14px}
                </style><main><h1>Harborview：BGE-M3 本地向量模型对照</h1>
                <p class="notice">复用已人工确认的 24 题、105 个切片及完整标注。默认模型、原索引、旧结果均未修改。全部向量在本地生成，云端调用为 0。</p>
                <p>主对照：当前 512 维模型 vs BGE-M3 1024 维，仅输入正文与原始问题，两组均在独立 PostgreSQL 表中按余弦距离完整排序。
                每题都用 Java 对全部 105 个向量独立校验；不把近似索引漏召回混入模型主对照。附加 RRF 固定参数并复用原关键词命中，不运行精排或生成。</p>
                <p>20 道有答案题计入排序均值；4 道无答案题不算正确拒答。本页是原始切片排名，不是最终窗口或答案准确率。
                <a href="../harborview-2026-10-10/review.html">已确认标注</a> · <a href="README.md">详细结论与局限</a>。</p>
                """);
        html.append("<section><h2>运行与复现</h2><p>本地编码输入 ").append(comparison.localEmbeddingRequests())
                .append(" 条（每个模型 105 段正文＋24 个问题）。历史向量完整前50排名复现：").append(comparison.historicalVectorOrderMatches())
                .append("/24；前50集合平均重合：").append(number(comparison.historicalVectorTop50Overlap())).append("。</p>");
        for (String mode : EmbeddingComparison.MODELS) {
            var vectors = mapper.readValue(root.resolve(mode.toLowerCase(Locale.ROOT) + "-vectors.json").toFile(), EmbeddingComparison.Vectors.class);
            double total = vectors.embeddingMs().values().stream().mapToDouble(Double::doubleValue).sum();
            var queryTimes = vectors.embeddingMs().entrySet().stream().filter(entry -> entry.getKey().startsWith("query:")).map(Map.Entry::getValue).sorted().toList();
            html.append("<p><strong>").append(esc(vectors.model().tag())).append("</strong>：").append(vectors.model().precision())
                    .append(" / ").append(vectors.model().dimensions()).append(" 维；编码请求总耗时 ").append(number(total / 1000)).append(" 秒；查询编码中位数 ")
                    .append(number((queryTimes.get(11) + queryTimes.get(12)) / 2)).append(" ms（本机诊断值，非 SLA）。</p><details><summary>实际加载位置、内存及模型元数据</summary><pre>")
                    .append(esc(vectors.loadedRuntime().toPrettyString())).append("</pre></details>");
        }
        html.append("</section>");
        for (var group : comparison.groups().entrySet()) {
            html.append("<section><h2>").append(category(group.getKey())).append("</h2><table><tr><th>方案</th><th>有答案/总数</th><th>Hit@5</th><th>Recall@5</th><th>MRR@5</th><th>nDCG@5</th><th>Recall@30</th><th>Recall@50*</th><th>事实覆盖@5</th><th>多事实齐全@5</th></tr>");
            for (String mode : EmbeddingComparison.MODES) {
                var summary = group.getValue().get(mode);
                var metric = summary.metrics();
                html.append("<tr><td>").append(name(mode)).append("</td><td>").append(summary.answerable()).append("/").append(summary.cases()).append("</td>");
                for (Double value : Arrays.asList(metric == null ? null : metric.hit(), metric == null ? null : metric.recall(), metric == null ? null : metric.mrr(),
                        metric == null ? null : metric.ndcg(), summary.recall30(), summary.recall50(), summary.factCoverage5())) html.append("<td>").append(number(value)).append("</td>");
                html.append("<td>").append(summary.complementaryCases() == 0 ? "—" : summary.completeComplementaryCases() + "/" + summary.complementaryCases()).append("</td></tr>");
            }
            html.append("</table><p>* RRF 候选池最多 30，故不报告它的 Recall@50；不是 0。</p></section>");
        }
        html.append("<section><h2>逐题变化：BGE-M3 相对当前模型</h2><table><tr><th>题号</th><th>向量 nDCG@5</th><th>双路 nDCG@5</th><th>历史与本轮当前向量排名</th></tr>");
        for (var row : comparison.rows()) {
            html.append("<tr><td><a href='#").append(row.id()).append("'>").append(row.id()).append("</a></td><td>")
                    .append(change(row, "VECTOR")).append("</td><td>").append(change(row, "HYBRID")).append("</td><td>")
                    .append(row.historicalVectorOrderMatches() ? "前50完全一致" : "有差异，单独诊断").append("</td></tr>");
        }
        html.append("</table></section>");
        var original = FilenameAblation.original();
        for (int i = 0; i < fixture.cases().size(); i++) {
            var question = fixture.cases().get(i);
            var row = comparison.rows().get(i);
            var historical = original.rows().get(i).vector().stream().map(ExtensionEvaluation.Hit::chunk).toList();
            html.append("<section id='").append(question.id()).append("'><h2>").append(esc(question.id() + " · " + question.query())).append("</h2><p>应答要点：")
                    .append(esc(question.expected())).append("</p><table><tr><th>相关切片</th><th>分级</th><th>历史向量</th>");
            for (String mode : EmbeddingComparison.MODES) html.append("<th>").append(name(mode)).append("</th>");
            html.append("</tr>");
            for (var label : corpus.grades().get(question.id()).entrySet()) if (label.getValue() > 0) {
                html.append("<tr><td><code>").append(esc(label.getKey())).append("</code></td><td>").append(label.getValue()).append("</td><td>")
                        .append(rank(historical, label.getKey())).append("</td>");
                for (String mode : EmbeddingComparison.MODES) html.append("<td>").append(rank(row.rankings().get(mode), label.getKey())).append("</td>");
                html.append("</tr>");
            }
            html.append("</table><p>— 表示不在对应候选池：向量/关键词最多50，RRF最多30。</p>");
            if (question.evidence().isEmpty()) html.append("<p>无答案题：不计算相关性指标，也没有运行拒答或生成。</p>");
            for (String mode : EmbeddingComparison.MODES) {
                html.append("<details><summary>").append(name(mode)).append("：实际前5证据</summary><ol>");
                for (String key : row.rankings().get(mode).stream().limit(5).toList()) html.append("<li><code>").append(esc(key)).append("</code> · 分级 ")
                        .append(corpus.grades().get(question.id()).get(key)).append("<pre>").append(esc(corpus.chunks().get(key).text())).append("</pre></li>");
                html.append("</ol></details>");
            }
            html.append("</section>");
        }
        return html.append("<p>两个实际模型配置的对照，不是只改变语言能力的因果实验。模型大小、架构、精度和维度均不同；该合成集已经被分析过，不是独立保留测试集。报告保留所有退化案例。</p></main></html>").toString();
    }

    private static String change(EmbeddingComparison.Row row, String suffix) {
        var before = row.scores().get("CURRENT_" + suffix).metrics();
        var after = row.scores().get("BGE_M3_" + suffix).metrics();
        if (before == null) return "无答案，不评分";
        double delta = after.ndcg() - before.ndcg();
        return (delta > 1e-12 ? "改善 " : delta < -1e-12 ? "退化 " : "不变 ") + number(before.ndcg()) + " → " + number(after.ndcg());
    }
    private static String rank(List<String> ranking, String key) { int rank = ranking.indexOf(key) + 1; return rank == 0 ? "—" : Integer.toString(rank); }
    private static String name(String mode) { return switch (mode) {
        case "CURRENT_VECTOR" -> "当前模型·向量"; case "BGE_M3_VECTOR" -> "BGE-M3·向量";
        case "CURRENT_HYBRID" -> "当前模型·RRF"; case "BGE_M3_HYBRID" -> "BGE-M3·RRF"; default -> "关键词·历史参照";
    }; }
    private static String category(String category) { return switch (category) {
        case "ALL" -> "总体"; case "zh" -> "中文问题"; case "en" -> "英文问题"; case "exact" -> "精确匹配";
        case "semantic" -> "同义表达"; case "cross-language" -> "跨语言"; case "multi-evidence" -> "多证据"; case "long-document" -> "长文分块"; default -> "无答案";
    }; }
    private static String number(Double value) { return value == null ? "—" : String.format(Locale.ROOT, "%.4f", value); }
    private static String esc(String text) { return AnnotationReview.escape(text); }
}
