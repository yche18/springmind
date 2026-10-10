package com.yche.springmind.qa.evaluation;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;

/** Renders provisional evidence review and metrics entirely from frozen local files. */
class ChallengeReportRun {
    @Test
    void generate() throws Exception {
        var result = ChallengeEvaluation.compare();
        var mapper = RerankEvaluationRun.datasetMapper();
        mapper.writerWithDefaultPrettyPrinter().writeValue(ChallengeData.OUTPUT.resolve("comparison.json").toFile(), result);
        Files.writeString(ChallengeData.OUTPUT.resolve("report.html"), report(result));
        Files.writeString(ChallengeData.OUTPUT.resolve("review.html"), review(result));
        Map<String, Object> template = new LinkedHashMap<>();
        template.put("status", "PENDING_HUMAN_REVIEW");
        template.put("fixtureSha256", RetrievalAblation.hash(ChallengeData.ROOT.resolve("fixture.json")));
        template.put("corpusSha256", RetrievalAblation.hash(ChallengeData.ROOT.resolve("corpus.json")));
        template.put("instructions", "Review expected answers, all positive/negative grades and fact alternatives. Record corrections separately; do not edit frozen files. No approval is implied by generating this file.");
        template.put("questions", ChallengeData.fixture().cases().stream().map(q -> Map.of("id", q.id(), "confirmed", false, "notes", "", "gradeCorrections", Map.of())).toList());
        Path reviewTemplate = ChallengeData.OUTPUT.resolve("review-template.json");
        if (!Files.exists(reviewTemplate)) ChallengeData.write(reviewTemplate, template);
        System.out.println(mapper.writeValueAsString(result.groups().get("ALL")));
        System.out.println(mapper.writeValueAsString(result.unionGroups().get("ALL")));
    }
    static String report(ChallengeEvaluation.Comparison result) throws Exception {
        var html = page("Meridian 挑战集：单路、候选并集与 RRF");
        html.append("<p>96份合成资料、105个实际切片、36题（30道有答案、6道无答案）。282次本地非空 embedding、72次本地关键词搜索，云端调用0。</p>")
                .append("<p>原模型与 BGE-M3 均只编码正文；关键词分别搜正文和正文＋文件名。RRF 等权、常数60，每路50，截断30，评价前5。不调权重、不删除失败题、不修改旧结果。</p>")
                .append("<p><a href='review.html'>先复核标注</a> · <a href='README.md'>结果分析</a> · <a href='../../challenge/README.md'>检索前冻结协议</a> · <a href='comparison.json'>完整指标与排名</a></p>")
                .append("<section><h2>本轮观察（标注待复核）</h2><p>新集不再是 BGE-M3 Recall@5 满分，但没有出现总体融合获胜：BGE-M3 单路 Recall@5 为98.33%、nDCG@5为0.9017；两种 RRF 均为86.67%、0.8002。原模型 Recall@5 融合前后均为86.67%，nDCG 从0.7677升至0.7967。</p>")
                .append("<p>在每路前25和前50两个深度，关键词独有的初始相关切片数均为0；并集没有扩大对应向量单路的相关证据集合。这里的收益主要可能来自重排，而不是补回向量候选中完全不存在的证据。</p>")
                .append("<p><a href='#c28'>c28</a> 是互补排序收益：两份证据从 BGE-M3 第8、4名变为RRF第5、1名，前五事实齐全率从5/6提高到6/6。");
        html.append("但 <a href='#c08'>c08</a>、<a href='#c11'>c11</a>、<a href='#c12'>c12</a>、<a href='#c24'>c24</a> 的跨语言证据被挤出前五；<a href='#c03'>c03</a> 与 <a href='#c15'>c15</a> 则把相似但适用范围不对的切片排到了正确证据之前。</p>")
                .append("<p>仍只有105个切片，每路取50已覆盖接近半库，候选召回仍接近天花板。不能把这次合成挑战集称为大规模真实流量基准，也不能据此断言混合检索普遍无效。</p></section>");
        for (var slice : result.groups().entrySet()) {
            html.append("<section><h2>").append(esc(slice.getKey())).append("</h2><div class='scroll'><table><tr><th>有序方案</th><th>计分/全部题</th><th>Hit@5</th><th>Recall@5</th><th>MRR@5</th><th>nDCG@5</th><th>Recall@30</th><th>Recall@50</th><th>事实覆盖@5</th><th>多事实齐全@5</th></tr>");
            for (var entry : slice.getValue().entrySet()) {
                var s = entry.getValue(); var m = s.metrics();
                html.append("<tr><td>").append(esc(entry.getKey())).append("</td><td>").append(s.answerable()).append("/").append(s.cases()).append("</td>");
                for (Double n : Arrays.asList(m == null ? null : m.hit(), m == null ? null : m.recall(), m == null ? null : m.mrr(), m == null ? null : m.ndcg(), s.recall30(), s.recall50(), s.factCoverage5())) td(html, n);
                html.append("<td>").append(s.complementaryCases() == 0 ? "—" : s.completeComplementaryCases() + "/" + s.complementaryCases()).append("</td></tr>");
            }
            html.append("</table></div><h3>无序候选并集：不能解读为前5排序</h3><p>U25＝每路前25并集，最多50；与上表单路前50比较原始候选名额。U50＝每路前50并集，最多100，只用于观察更大预算的补漏上限。实际平均候选数包含无答案题；召回/事实均值排除无答案题。</p><div class='scroll'><table><tr><th>候选集合</th><th>平均数量</th><th>Recall</th><th>事实覆盖</th><th>仅向量/仅关键词相关数</th><th>被RRF30/前5丢弃</th></tr>");
            for (var entry : result.unionGroups().get(slice.getKey()).entrySet()) {
                var u = entry.getValue();
                html.append("<tr><td>").append(esc(entry.getKey())).append("</td>"); td(html, u.meanSize()); td(html, u.recall()); td(html, u.factCoverage());
                html.append("<td>").append(u.vectorExclusiveRelevant()).append("/").append(u.keywordExclusiveRelevant()).append("</td><td>").append(u.lostAtRrf30()).append("/").append(u.lostAtRrf5()).append("</td></tr>");
            }
            html.append("</table></div><p>相关数按题-切片对累计，不是去重文档数。丢弃数针对来自对应并集的相关证据；RRF 始终使用50＋50输入，并非 U25 的融合结果。</p></section>");
        }
        html.append("<section><h2>逐题改善与退化：相对对应向量单路</h2>");
        for (String model : EmbeddingComparison.MODELS) for (String field : List.of("BODY", "FILENAME")) {
            String arm = model + "_" + field + "_RRF";
            List<String> wins = new ArrayList<>(), losses = new ArrayList<>(), ties = new ArrayList<>();
            for (var row : result.rows()) {
                var a = row.scores().get(model + "_VECTOR").metrics(); var b = row.scores().get(arm).metrics();
                if (a == null) continue;
                double delta = b.ndcg() - a.ndcg();
                (delta > 1e-12 ? wins : delta < -1e-12 ? losses : ties).add(row.id());
            }
            html.append("<h3>").append(arm).append("</h3><p>改善 ").append(wins.size()).append("：").append(links(wins)).append("</p><p>退化 ").append(losses.size()).append("：").append(links(losses)).append("</p><p>不变 ").append(ties.size()).append("：").append(links(ties)).append("</p>");
        }
        html.append("</section>");
        var corpus = ChallengeData.corpus();
        for (var row : result.rows()) {
            html.append("<section id='").append(row.id()).append("'><h2>").append(row.id()).append("</h2><a href='review.html#").append(row.id()).append("'>问题、证据和完整标注复核</a>");
            for (var arm : row.rankings().entrySet()) {
                html.append("<details><summary>").append(arm.getKey()).append(" · Recall@5 ").append(number(row.scores().get(arm.getKey()).metrics() == null ? null : row.scores().get(arm.getKey()).metrics().recall())).append("</summary><ol>");
                for (String id : arm.getValue().stream().limit(5).toList()) html.append("<li>").append(id).append(" · 分级 ").append(corpus.grades().get(row.id()).get(id)).append("<pre>").append(esc(corpus.chunks().get(id).text())).append("</pre></li>");
                html.append("</ol></details>");
            }
            html.append("<details><summary>候选并集：独有相关证据和融合损失</summary>");
            for (var u : row.unions().entrySet()) html.append("<h4>").append(u.getKey()).append("：数量 ").append(u.getValue().size()).append(" / Recall ").append(number(u.getValue().recall())).append("</h4><p>向量独有：").append(u.getValue().vectorExclusiveRelevant())
                    .append("；关键词独有：").append(u.getValue().keywordExclusiveRelevant()).append("；RRF30丢失：").append(u.getValue().lostAtRrf30()).append("；RRF前5丢失：").append(u.getValue().lostAtRrf5()).append("</p>");
            html.append("</details></section>");
        }
        return html.append("</main></html>").toString();
    }
    static String review(ChallengeEvaluation.Comparison result) throws Exception {
        var fixture = ChallengeData.fixture(); var corpus = ChallengeData.corpus();
        var html = page("Meridian 挑战集：人工标注复核");
        html.append("<p>所有题目、引用和初始分级均在检索前冻结；下列仅展示结果，不自动修改标注。请逐题确认应答要点、全部正例、疑似漏标和无答案判断。</p>")
                .append("<p>3＝独立完整支持；2＝必要的部分支持；1＝有用但不足的背景；0＝无关、错误适用范围或不适用的历史版本。不能因为检索排名高就改成相关。</p>")
                .append("<p>c24 两份证据是同一事实的替代支持，不要求同时召回。c25–c30 是互补事实，需检查是否全部找齐。c32/c35 的会议纪要只能支持‘未批准’，不能提供保险额度/供应商：请确认这类题应维持无实质答案还是另设拒答证据标签。</p>")
                .append("<section><h2>建议优先复核</h2><ul><li><a href='#c23'>c23</a>：d75--c000 旧规明确写了7月15日前签发为180天，对本题旧证书仍适用；初始0分可能漏标，请判定能否独立支持或部分支持。</li>")
                .append("<li><a href='#c12'>c12</a>：d42--c000 也说明设置不会重建已删备份，但没有独立导出的恢复建议。初始0分是否应改为部分支持？</li>")
                .append("<li><a href='#c16'>c16</a>：d55--c000 说明 Export Operator 的下载权限，但讨论的是 Auditor 而非 Viewer。初始0分是否应保留，或属于1/2分背景支持？</li>")
                .append("<li><a href='#c24'>c24</a>：两份替代证据是否都可独立标3？它们是同一事实，不能按两个必要事实计算。</li>")
                .append("<li><a href='#c25'>c25–c30</a>：互补事实均标2，检查是否有相邻切片也能支持、是否应增加替代证据。</li>")
                .append("<li><a href='#c32'>c32/c35</a>：会议纪要明确说未批准；‘没有实质答案’与‘可以据此拒答’需区分，本次未测拒答质量。</li></ul><p>以上均为复核建议，尚未改动冻结分级或据此重算成绩。其余题目的零分与正例同样需要确认。</p></section>")
                .append("<p>每题展开“全库初始分级”可检查全部105个切片（共3780个题-切片判断）。零分但被检索命中的列表只是复核线索，不代表标错。正文位于页末，可点击证据编号跳转。</p>")
                .append("<p><a href='report.html'>指标报告（暂定）</a> · <a href='review-template.json'>复核记录模板</a></p><nav>");
        for (var q : fixture.cases()) html.append("<a href='#").append(q.id()).append("'>").append(q.id()).append("</a> ");
        html.append("</nav>");
        for (int i = 0; i < fixture.cases().size(); i++) {
            var q = fixture.cases().get(i); var row = result.rows().get(i); var labels = corpus.grades().get(q.id());
            html.append("<section id='").append(q.id()).append("'><h2>").append(q.id()).append(" · ").append(esc(q.query())).append("</h2><p>类别：").append(q.category()).append(" / ").append(q.language()).append("</p><p>应答要点：").append(esc(q.expected())).append("</p>");
            for (var e : q.evidence()) html.append("<blockquote>事实 ").append(esc(e.id())).append(" · 文档 ").append(e.document()).append(" · 初始分级 ").append(e.grade()).append("<pre>").append(esc(e.quote())).append("</pre></blockquote>");
            if (q.evidence().isEmpty()) html.append("<p>初始判断：库中没有实质答案。返回任何候选均不代表正确拒答；请核对是否其实存在可回答的证据。</p>");
            html.append("<div class='scroll'><table><tr><th>正例切片</th><th>分级</th>");
            for (String mode : ChallengeEvaluation.MODES) html.append("<th>").append(mode).append("</th>");
            html.append("</tr>");
            for (var grade : labels.entrySet()) if (grade.getValue() > 0) {
                html.append("<tr><td>").append(chunkLink(grade.getKey())).append("</td><td>").append(grade.getValue()).append("</td>");
                for (String mode : ChallengeEvaluation.MODES) { int rank = row.rankings().get(mode).indexOf(grade.getKey()) + 1; html.append("<td>").append(rank == 0 ? "未入候选" : rank).append("</td>"); }
                html.append("</tr>");
            }
            html.append("</table></div><details><summary>各路前10命中的零分切片：检查有无遗漏支持</summary><ul>");
            Set<String> suspicious = new TreeSet<>();
            row.rankings().values().forEach(rank -> rank.stream().limit(10).filter(id -> labels.get(id) == 0).forEach(suspicious::add));
            for (String id : suspicious) html.append("<li>").append(chunkLink(id)).append(" · ").append(esc(fixture.documents().get(corpus.chunks().get(id).document()).filename())).append("</li>");
            html.append("</ul></details><details><summary>全库初始分级（包含未被任何方法召回的切片）</summary><p>");
            for (var grade : labels.entrySet()) html.append(chunkLink(grade.getKey())).append("＝").append(grade.getValue()).append("； ");
            html.append("</p></details><p>复核反馈可直接发：题号＋切片编号＋建议分级＋理由；全部无误时请明确确认。此页面本身不代表已签收。</p></section>");
        }
        html.append("<h2>完整切片库</h2>");
        for (var chunk : corpus.chunks().entrySet()) html.append("<section id='").append(chunk.getKey()).append("'><h3>").append(chunk.getKey()).append(" · ").append(esc(fixture.documents().get(chunk.getValue().document()).filename())).append("</h3><pre>").append(esc(chunk.getValue().text())).append("</pre><a href='#'>回到题号导航</a></section>");
        return html.append("</main></html>").toString();
    }
    private static StringBuilder page(String title) {
        return new StringBuilder("<!doctype html><html lang='zh'><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'><title>")
                .append(esc(title)).append("</title><style>body{font:16px/1.65 system-ui,sans-serif;background:#f1f5f9;color:#243247;margin:0}main{max-width:1450px;margin:auto;padding:24px}section{background:white;padding:22px;margin:22px 0;border:1px solid #dbe3ef;border-radius:10px}.scroll{overflow:auto}table{border-collapse:collapse;width:100%;font-variant-numeric:tabular-nums}td,th{padding:9px;text-align:left;border-bottom:1px solid #ddd}th{background:#eaf1f7}pre{white-space:pre-wrap;overflow-wrap:anywhere;background:#f7f9fc;padding:12px}details{border:1px solid #ddd;padding:10px;margin:10px 0}summary{cursor:pointer}a{color:#155eab}.notice{padding:16px;background:#fff2ca;border-left:4px solid #b57700}nav a{display:inline-block;margin:5px}blockquote{border-left:3px solid #9ac;padding-left:15px}</style><main><h1>")
                .append(esc(title)).append("</h1><p class='notice'>待人工复核 · PENDING_HUMAN_REVIEW。当前指标仅基于冻结的初始标注，不是已确认的项目成果。合成诊断集，不代表真实流量或统计显著性。</p>");
    }
    private static String chunkLink(String id) { return "<a href='#" + id + "'>" + id + "</a>"; }
    private static String links(List<String> ids) { return ids.isEmpty() ? "无" : String.join("、", ids.stream().map(id -> "<a href='#" + id + "'>" + id + "</a>").toList()); }
    private static String esc(String text) { return AnnotationReview.escape(text); }
    private static String number(Double n) { return n == null ? "—" : String.format(Locale.ROOT, "%.4f", n); }
    private static void td(StringBuilder html, Double n) { html.append("<td>").append(number(n)).append("</td>"); }
}
