package com.yche.springmind.qa.evaluation;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;

/** Reads fixed local artifacts only; never starts Spring or contacts retrieval/model services. */
class RetrievalCrossReportRun {
    @Test
    void generate() throws Exception {
        var comparison = RetrievalCrossComparison.compare();
        Files.createDirectories(RetrievalCrossComparison.OUTPUT);
        var mapper = RerankEvaluationRun.datasetMapper();
        mapper.writerWithDefaultPrettyPrinter().writeValue(RetrievalCrossComparison.OUTPUT.resolve("comparison.json").toFile(), comparison);
        Files.writeString(RetrievalCrossComparison.OUTPUT.resolve("report.html"), render(comparison));
        System.out.println(mapper.writeValueAsString(comparison.groups().get("ALL")));
        System.out.println(mapper.writeValueAsString(comparison.effects().get("ALL")));
    }

    static String render(RetrievalCrossComparison.Comparison result) throws Exception {
        var fixture = ExtensionFixture.read();
        var corpus = FilenameAblation.corpus();
        var html = new StringBuilder("""
                <!doctype html><html lang="zh"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
                <title>Harborview：模型 × 文件名 × RRF 离线交叉对照</title><style>
                body{font:16px/1.65 system-ui,sans-serif;background:#f1f5f9;color:#203044;margin:0}main{max-width:1400px;margin:auto;padding:28px}
                section{background:white;padding:24px;border:1px solid #dce4ed;border-radius:12px;margin:24px 0;overflow:auto}
                table{border-collapse:collapse;width:100%;font-variant-numeric:tabular-nums}th,td{padding:10px;border-bottom:1px solid #dce4ed;text-align:left}th{background:#eaf1f7}
                pre{white-space:pre-wrap;overflow-wrap:anywhere;background:#f7fafc;padding:12px}code{overflow-wrap:anywhere}a{color:#145ca6}
                details{border:1px solid #dce4ed;border-radius:6px;padding:10px;margin:10px 0}summary{cursor:pointer}.notice{background:#fff8e8;border-left:4px solid #c28314;padding:14px}
                </style><main><h1>Harborview：模型 × 文件名 × RRF 离线交叉对照</h1>
                <p class="notice">完全复用已保存的结果。新增模型调用 0，检索调用 0；原资料、人工确认标注、旧结果与默认配置均未修改。</p>
                <p>8 组：2 种关键词单路＋2 种向量单路＋4 种交叉 RRF。固定等权、常数 60、每路最多50、融合最多30、评估前5。
                20 道有答案题计入均值，4 道无答案题不算正确拒答；原始切片指标不等于答案准确率或最终窗口覆盖。</p>
                <p>文件名效应：固定向量模型及融合方式，只改变关键词输入。RRF 效应：相对于向量单路，加入指定关键词路并采用当前等权融合的净变化；不能推广成所有融合算法的固有效果。
                两种向量都只编码正文；该实验复用不同时间采集的冻结结果，不是一次新端到端运行。</p>
                <p><a href="README.md">结论与具体案例</a> · <a href="../harborview-2026-10-10/review.html">已确认标注</a> ·
                <a href="../harborview-bgem3-2026-10-10/report.html">原模型对照</a> · <a href="../harborview-filename-2026-10-10/report.html">原文件名消融</a></p>
                <section><h2>结论：文件名与 RRF 的影响需要分开看</h2>
                <p>加入文件名：关键词单路 Recall@5 +15 个百分点；原模型 RRF 与 BGE-M3 RRF 均 +5 个百分点。
                这里文件名总体有益，不能把混合检索的退化直接归咎于文件名。</p>
                <p>加入关键词并等权 RRF：原模型 Recall@5 从 77.5% 提升至 80%（正文）或 85%（正文＋文件名）；
                BGE-M3 却从 100% 降至 92.5% 或 97.5%。收益取决于两路的质量与互补性，并非多一路就一定更好。</p>
                <ul><li><a href="#e15">e15</a>：BGE-M3 单路两份证据排第 1、3；正文 RRF 降到 13、14，加入文件名后回到 2、4。
                文件名缓解了退化，但未恢复向量单路的排序质量。</li>
                <li><a href="#e16">e16</a>：截止日期证据从 BGE-M3 单路第 1 降到两种 RRF 的第 6，丢出前5；去掉文件名不能解决。
                两路都命中的非相关切片可以获得双份排名分数，超过仅有一侧高排名的正确证据。</li>
                <li><a href="#e12">e12</a>：正确切片从 BGE-M3 第 5 提升到两种 RRF 的第 2，证明关键词仍有互补价值。</li>
                <li><a href="#e19">e19</a>：原模型正文 RRF 的批准证据排第 2，加入文件名后排第 3。
                两种关键词的正确证据排名均为 1、2，但无关切片 c015 的关键词排名从 5 升至 4，融合后反超批准证据。
                文件名并非逐题无害；本题 Recall 不变但 nDCG 下降。</li></ul>
                <p>这些是同一小型合成集上的条件性观察，不是统计显著性或生产准确率结论。本轮不调整权重、不换题、不改默认模型、不运行精排。</p></section>
                """);
        for (var group : result.groups().entrySet()) {
            html.append("<section><h2>").append(category(group.getKey())).append("</h2><table><tr><th>方案</th><th>有答案/全部</th><th>Hit@5</th><th>Recall@5</th><th>MRR@5</th><th>nDCG@5</th><th>Recall@30</th><th>Recall@50*</th><th>多事实齐全@5</th></tr>");
            for (String mode : RetrievalCrossComparison.MODES) {
                var summary = group.getValue().get(mode);
                var m = summary.metrics();
                html.append("<tr><td>").append(name(mode)).append("</td><td>").append(summary.answerable()).append("/").append(summary.cases()).append("</td>");
                for (Double value : Arrays.asList(m == null ? null : m.hit(), m == null ? null : m.recall(), m == null ? null : m.mrr(), m == null ? null : m.ndcg(), summary.recall30(), summary.recall50()))
                    html.append("<td>").append(number(value)).append("</td>");
                html.append("<td>").append(summary.complementaryCases() == 0 ? "—" : summary.completeComplementaryCases() + "/" + summary.complementaryCases()).append("</td></tr>");
            }
            html.append("</table><p>* RRF 只有最多30个候选，Recall@50 不适用。完整独立事实覆盖见 JSON。</p><h3>固定其他因素后的变化</h3><table><tr><th>对照（前→后）</th><th>ΔRecall@5</th><th>ΔnDCG@5</th><th>改善/退化/不变（按每题 nDCG）</th></tr>");
            for (var effect : result.effects().get(group.getKey()).values()) {
                html.append("<tr><td>").append(name(effect.before())).append(" → ").append(name(effect.after())).append("</td><td>")
                        .append(number(effect.delta() == null ? null : effect.delta().recall())).append("</td><td>")
                        .append(number(effect.delta() == null ? null : effect.delta().ndcg())).append("</td><td>")
                        .append(effect.improved().size()).append(" / ").append(effect.regressed().size()).append(" / ").append(effect.unchanged().size())
                        .append("<details><summary>题号</summary><p>改善：").append(links(effect.improved())).append("</p><p>退化：")
                        .append(links(effect.regressed())).append("</p><p>不变：").append(links(effect.unchanged())).append("</p></details></td></tr>");
            }
            html.append("</table></section>");
        }
        for (int i = 0; i < fixture.cases().size(); i++) {
            var question = fixture.cases().get(i);
            var row = result.rows().get(i);
            html.append("<section id='").append(question.id()).append("'><h2>").append(esc(question.id() + " · " + question.query())).append("</h2><p>已确认应答要点：")
                    .append(esc(question.expected())).append("</p><table><tr><th>相关切片（分级）</th>");
            for (String mode : RetrievalCrossComparison.MODES) html.append("<th>").append(name(mode)).append("</th>");
            html.append("</tr>");
            for (var grade : corpus.grades().get(question.id()).entrySet()) if (grade.getValue() > 0) {
                html.append("<tr><td><code>").append(esc(grade.getKey())).append("</code>（").append(grade.getValue()).append("）</td>");
                for (String mode : RetrievalCrossComparison.MODES) {
                    int rank = row.rankings().get(mode).indexOf(grade.getKey()) + 1;
                    html.append("<td>").append(rank == 0 ? "—" : Integer.toString(rank)).append("</td>");
                }
                html.append("</tr>");
            }
            html.append("</table><p>—：不在对应候选池；不代表文档不存在。单路最多50，RRF最多30。</p>");
            if (question.evidence().isEmpty()) html.append("<p>已确认为无答案，不计算相关性指标；未运行拒答或生成。</p>");
            for (String mode : RetrievalCrossComparison.MODES) {
                html.append("<details><summary>").append(name(mode)).append("：前5证据 / nDCG ")
                        .append(number(row.scores().get(mode).metrics() == null ? null : row.scores().get(mode).metrics().ndcg())).append("</summary><ol>");
                for (String key : row.rankings().get(mode).stream().limit(5).toList()) {
                    html.append("<li><code>").append(esc(key)).append("</code> · 分级 ").append(corpus.grades().get(question.id()).get(key));
                    if (mode.endsWith("RRF")) {
                        String vector = mode.startsWith("CURRENT") ? "CURRENT_VECTOR" : "BGE_M3_VECTOR";
                        String keyword = mode.contains("FILENAME") ? "KEYWORD_FILENAME" : "KEYWORD_BODY";
                        int v = row.rankings().get(vector).indexOf(key) + 1, k = row.rankings().get(keyword).indexOf(key) + 1;
                        html.append(" · 向量排名 ").append(v == 0 ? "未命中" : v).append(" / 关键词排名 ").append(k == 0 ? "未命中" : k)
                                .append(" / RRF ").append(String.format(Locale.ROOT, "%.6f", (v == 0 ? 0 : 1.0 / (60 + v)) + (k == 0 ? 0 : 1.0 / (60 + k))));
                    }
                    html.append("<pre>").append(esc(corpus.chunks().get(key).text())).append("</pre></li>");
                }
                html.append("</ol></details>");
            }
            html.append("</section>");
        }
        return html.append("<p>同一小型合成诊断集，没有独立新题验证。文件名是实际可用信息，其收益和加入弱关键词路的影响会随模型与问题变化；不据此自动修改生产配置。</p></main></html>").toString();
    }
    private static String links(List<String> ids) { return ids.isEmpty() ? "无" : String.join("、", ids.stream().map(id -> "<a href='#" + id + "'>" + id + "</a>").toList()); }
    private static String name(String mode) { return switch (mode) {
        case "KEYWORD_BODY" -> "关键词·仅正文"; case "KEYWORD_FILENAME" -> "关键词·正文＋文件名";
        case "CURRENT_VECTOR" -> "原模型·向量"; case "CURRENT_BODY_RRF" -> "原模型＋正文关键词 RRF";
        case "CURRENT_FILENAME_RRF" -> "原模型＋文件名关键词 RRF"; case "BGE_M3_VECTOR" -> "BGE-M3·向量";
        case "BGE_M3_BODY_RRF" -> "BGE-M3＋正文关键词 RRF"; default -> "BGE-M3＋文件名关键词 RRF";
    }; }
    private static String category(String category) { return switch (category) {
        case "ALL" -> "总体"; case "zh" -> "中文问题"; case "en" -> "英文问题"; case "exact" -> "精确匹配";
        case "semantic" -> "同义表达"; case "cross-language" -> "跨语言"; case "multi-evidence" -> "多证据";
        case "long-document" -> "长文分块"; default -> "无答案";
    }; }
    private static String number(Double n) { return n == null ? "—" : String.format(Locale.ROOT, "%.4f", n); }
    private static String esc(String text) { return AnnotationReview.escape(text); }
}
