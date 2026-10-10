package com.yche.springmind.qa.evaluation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.nio.file.*;
import java.util.*;
import static com.yche.springmind.qa.evaluation.RetrievalAblation.require;

/** Rebuilds reports from immutable local captures; never calls a model. */
@EnabledIfSystemProperty(named = "eval.multihopReport", matches = "true")
class MultiHopReportRun {
    @Test void report() throws Exception {
        var corpus = MultiHopData.corpus(); MultiHopData.verify(corpus);
        var sample = MultiHopData.sample();
        var capture = MultiHopData.M.readValue(MultiHopData.OUT.resolve("capture.json").toFile(), MultiHopEvaluation.Capture.class);
        require(capture.freezeSha256().equals(RetrievalAblation.hash(MultiHopData.ROOT.resolve("freeze.json"))), "Wrong freeze");
        var report = MultiHopEvaluation.analyze(sample, capture);
        ChallengeData.write(MultiHopData.OUT.resolve("comparison.json"), report);
        Files.writeString(MultiHopData.OUT.resolve("report.html"), html(sample, capture, report, corpus, false), StandardOpenOption.CREATE_NEW);
        Path review = Path.of("target/multihop-review"); Files.createDirectories(review);
        Files.writeString(review.resolve("review.html"), html(sample, capture, report, corpus, true), StandardOpenOption.CREATE_NEW);
        Map<String, String> hashes = new TreeMap<>();
        for (String name : List.of("capture.json", "comparison.json", "report.html")) hashes.put(name, RetrievalAblation.hash(MultiHopData.OUT.resolve(name)));
        hashes.put("local-review.html", RetrievalAblation.hash(review.resolve("review.html")));
        for (String name : List.of("MultiHopData", "LiveMultiHopRun", "MultiHopReplay", "MultiHopEvaluation", "MultiHopReportRun"))
            hashes.put(name + ".java", RetrievalAblation.hash(Path.of("src/test/java/com/yche/springmind/qa/evaluation", name + ".java")));
        ChallengeData.write(MultiHopData.OUT.resolve("artifact-hashes.json"), hashes);
        System.out.println(MultiHopData.M.writerWithDefaultPrettyPrinter().writeValueAsString(report.groups().get("FORMAL")));
    }
    static String esc(Object value) {
        return String.valueOf(value).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
    static String pct(Double value) { return value == null ? "—" : String.format(Locale.ROOT, "%.2f%%", value * 100); }
    static String html(MultiHopData.Sample sample, MultiHopEvaluation.Capture capture, MultiHopEvaluation.Report report,
                       MultiHopData.Corpus corpus, boolean review) {
        StringBuilder h = new StringBuilder("""
                <!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
                <title>MultiHop-RAG · 本地检索诊断</title><style>
                :root{font-family:system-ui,"Microsoft YaHei",sans-serif;color:#172b3a;background:#f2f5f7;line-height:1.7}
                body{max-width:1180px;margin:32px auto;padding:0 22px}h1{line-height:1.25}h2{margin-top:32px}
                .card,details{background:white;border:1px solid #dbe3e8;border-radius:12px;padding:18px 22px;margin:16px 0}
                .note{border-left:4px solid #d89229;background:#fff8e8;padding:12px 18px}.tag{background:#e6eff7;border-radius:5px;padding:3px 8px;font-size:13px}
                a{color:#126389}table{border-collapse:collapse;width:100%;font-variant-numeric:tabular-nums}th,td{padding:8px 10px;text-align:left;border-bottom:1px solid #e5ebee}
                th{background:#eef3f6}summary{cursor:pointer;font-weight:650}blockquote{border-left:3px solid #b7cdd7;margin:12px 0;padding:8px 14px;background:#f5f8fa}
                .scroll{overflow-x:auto}input,select{padding:10px;border:1px solid #bacbd3;border-radius:6px;margin-right:12px}input{min-width:250px}
                .small{font-size:13px;color:#586c78}pre{white-space:pre-wrap;overflow-wrap:anywhere;font:inherit}.missing{color:#9a3b22}
                </style><h1>MultiHop-RAG · 本地检索诊断</h1>
                <p>50题正式集 + 10题校准集 · 全量609篇文章 · 26,110切片 · 本地 BGE-M3 · 云端调用 0</p>
                <p class="note">这不是回答准确率评测。这里只测官方证据是否被检索并保留；未生成答案、未调用 Qwen 精排。
                官方标注尚待本地复核。不能把“找到相关文章”当成“找齐了回答证据”。</p>
                """);
        h.append("<p><a href=\"").append(review ? "../../evaluation/retrieval/results/multihop-2026-10-11/report.html" : "../../../../target/multihop-review/review.html")
                .append("\">").append(review ? "返回报告" : "打开详细复核页（含本地原文切片）").append("</a> · ")
                .append("<a href=\"https://huggingface.co/datasets/yixuantt/MultiHopRAG/tree/").append(MultiHopData.REVISION).append("\">官方固定版本 / ODC-BY</a></p>");
        var formal = report.groups().get("FORMAL");
        h.append("<section class=card><h2>先看两件不同的事</h2><p>候选池：关键词 / 向量 / 并集找齐全部证据的题数分别为 <b>")
                .append(formal.get("KEYWORD@50").complete()).append(" / ").append(formal.get("VECTOR@50").complete()).append(" / ")
                .append(formal.get("UNION50").complete()).append("</b>（44题）。</p><p>最终上下文：关键词 / 向量 / RRF 找齐全部证据的题数分别为 <b>")
                .append(formal.get("KEYWORD_CONTEXT").complete()).append(" / ").append(formal.get("VECTOR_CONTEXT").complete()).append(" / ")
                .append(formal.get("RRF_CONTEXT").complete()).append("</b>（44题）。</p><p>前者看“是否有可利用的互补证据”，后者看“现有融合和预算是否把它留下”。不能将两者混为一谈。</p></section>");
        for (String split : List.of("FORMAL", "CALIBRATION")) {
            var group = report.groups().get(split);
            var rows = report.rows().stream().filter(r -> r.split().equals(split)).toList();
            long ko = rows.stream().filter(r -> !r.keywordOnlyFacts().isEmpty()).count();
            long vo = rows.stream().filter(r -> !r.vectorOnlyFacts().isEmpty()).count();
            long mutual = rows.stream().filter(r -> !r.keywordOnlyFacts().isEmpty() && !r.vectorOnlyFacts().isEmpty()).count();
            long unionOnly = rows.stream().filter(MultiHopEvaluation.Scored::unionOnlyComplete).count();
            h.append("<section class=card><h2>").append(split.equals("FORMAL") ? "正式集 · 50题 / 44题有证据" : "校准集 · 10题 / 9题有证据").append("</h2>");
            h.append("<p>在 top50 候选中，关键词独有事实出现于 <b>").append(ko).append("</b> 题，向量独有事实出现于 <b>").append(vo)
                    .append("</b> 题；<b>").append(mutual).append("</b> 题两路各有独有事实。两条单路均未找齐、并集找齐：<b>").append(unionOnly).append("</b> 题。</p>");
            h.append("<div class=scroll><table><tr><th>阶段 / 模式</th><th>事实召回（宏平均）</th><th>全部事实找齐</th><th>文档召回</th><th>平均切片数</th></tr>");
            group.forEach((mode, s) -> h.append("<tr><td>").append(mode).append("</td><td>").append(pct(s.factRecall()))
                    .append("</td><td>").append(s.complete()).append("/").append(s.answerable()).append(" · ").append(pct(s.completeRate()))
                    .append("</td><td>").append(pct(s.documentRecall())).append("</td><td>").append(String.format(Locale.ROOT, "%.1f", s.averageChunks())).append("</td></tr>"));
            h.append("</table></div><p class=small>UNION25：两路各25，最多50片；UNION50：各50，最多100片，容量较大，不能把增益全归功于融合。"
                    + "CONTEXT：生产聚类 + 相邻片段 + 最多5个证据窗口 / 4096 tokens；实际 RRF 候选截断为30。U25/U50 是无序覆盖上限，不是答案系统。</p></section>");
        }
        h.append("<section class=card><h2>判读与待复核事项</h2><ul>")
                .append("<li>正式集和校准集分开统计；抽样按问题类型及2/3/4份证据分层，未按检索得分挑题。</li>")
                .append("<li>官方事实采用严格完整文本映射，跨片事实必须同时命中完整跨度；语义等价替代证据不自动算命中。</li>")
                .append("<li>所有参考事实均成功映射。请检查参考事实是否必要、是否足以支撑标准答案，以及候选中是否有同义替代证据。</li>")
                .append("<li>部分推理题在问题里已透露答案实体；是官方原题，不修改，也不能把之后的答对完全归功于检索。</li>")
                .append("<li>项目分块器使用保守的1字符≈1 token估计，240/320/32是配置预算，不是真实英文token数；小切片、重复元数据与标题加权均可能影响结果。</li>")
                .append("<li>无答案题仅保留作后续拒答测试，这轮不计算拒答准确率。公开集可能进入过模型训练，不宣称未见过。</li></ul></section>")
                .append("<p class=small>单路实验仅启用对应通道。复用生产流程中的单路倒数排名保持原有次序，并未混入另一条检索路径。</p>")
                .append("<h2>逐题复核 · 完整60题</h2><p>优先看“双路互补”“融合损失”和“共同缺失”；展开可查看标准答案、证据及各阶段结果。</p>")
                .append("<p><input id=search placeholder=\"搜索题号、问题、答案\"><select id=filter><option value=all>全部题目</option><option value=FORMAL>正式集</option><option value=CALIBRATION>校准集</option><option value=mutual>双路各有独有事实</option><option value=loss>融合上下文有损失</option><option value=miss>候选并集仍有缺失</option><option value=null_query>无答案题</option></select></p>");
        for (int i = 0; i < sample.questions().size(); i++) {
            var q = sample.questions().get(i); var row = report.rows().get(i); var captured = capture.rows().get(i);
            var s = row.scores();
            boolean mutual = !row.keywordOnlyFacts().isEmpty() && !row.vectorOnlyFacts().isEmpty();
            boolean loss = !q.facts().isEmpty() && s.get("RRF_CONTEXT").factRecall() < Math.max(s.get("KEYWORD_CONTEXT").factRecall(), s.get("VECTOR_CONTEXT").factRecall());
            boolean miss = !q.facts().isEmpty() && !s.get("UNION50").allFacts();
            String tags = q.split() + " " + q.type() + (mutual ? " mutual" : "") + (loss ? " loss" : "") + (miss ? " miss" : "");
            h.append("<details class=question id=\"").append(q.id()).append("\" data-tags=\"").append(tags).append("\"><summary>")
                    .append(q.id()).append(" · ").append(esc(q.query())).append("</summary><p><span class=tag>").append(tags).append("</span></p>")
                    .append("<p>官方答案：<b>").append(esc(q.answer())).append("</b> · 原始行号（从0起）：").append(q.sourceRow()).append("</p>");
            if (q.type().equals("inference_query") && q.query().toLowerCase(Locale.ROOT).contains(q.answer().toLowerCase(Locale.ROOT)))
                h.append("<p class=note>复核提示：官方答案实体已出现在问题中。保留原题用于证据检索诊断；以后不能仅凭答对这题证明检索有效。</p>");
            h.append("<p>关键词独有事实：").append(esc(row.keywordOnlyFacts())).append("；向量独有事实：").append(esc(row.vectorOnlyFacts()))
                    .append("；仅并集找齐：").append(row.unionOnlyComplete() ? "是" : "否").append("。</p>");
            h.append("<table><tr><th>模式</th><th>事实召回</th><th>全部找齐</th></tr>");
            s.forEach((mode, score) -> h.append("<tr><td>").append(mode).append("</td><td>").append(pct(score.factRecall())).append("</td><td>")
                    .append(score.allFacts() == null ? "不适用" : score.allFacts() ? "是" : "否").append("</td></tr>"));
            h.append("</table><h3>官方参考证据（待人工确认必要性与充分性）</h3>");
            if (q.facts().isEmpty()) h.append("<p>官方无证据 / Insufficient information；请确认问题在全量资料中确实无法回答。</p>");
            for (var f : q.facts()) {
                var doc = corpus.documents().get(f.documentId());
                h.append("<p><b>").append(f.id()).append(" · ").append(esc(doc.path("title").asText())).append("</b><br><span class=small>")
                        .append(esc(doc.path("source").asText())).append(" · ").append(esc(doc.path("published_at").asText())).append(" · document ").append(f.documentId())
                        .append("</span></p><blockquote>").append(esc(f.quote())).append("</blockquote><p class=small>完整切片跨度：").append(esc(f.alternatives())).append("<br>关键词排名：")
                        .append(esc(ranks(f, captured.keyword()))).append("；向量排名：").append(esc(ranks(f, captured.vector()))).append("</p>");
            }
            if (review) {
                h.append("<h3>最终送入回答的上下文切片（本轮未实际生成）</h3>");
                captured.contexts().forEach((mode, context) -> {
                    h.append("<details><summary>").append(mode).append(" · ").append(context.windows()).append("窗口 / ").append(context.tokens()).append(" tokens</summary>");
                    for (long id : context.chunks()) {
                        var c = corpus.chunks().get(id);
                        h.append("<p class=small>chunk ").append(id).append(" / document ").append(c.documentId()).append(" / index ").append(c.index()).append("</p><pre>").append(esc(c.text())).append("</pre>");
                    }
                    h.append("</details>");
                });
            }
            h.append("</details>");
        }
        h.append("<p class=small>采集：").append(esc(capture.startedAt())).append(" → ").append(esc(capture.completedAt())).append("<br>冻结 SHA-256：")
                .append(capture.freezeSha256()).append("<br>Elasticsearch ").append(esc(capture.elasticVersion())).append(" / Ollama ").append(esc(capture.ollamaVersion())).append("</p>");
        h.append("""
                <script>
                const search=document.getElementById('search'), filter=document.getElementById('filter');
                function apply(){const text=search.value.toLowerCase();document.querySelectorAll('.question').forEach(e=>{
                  e.hidden=!(e.textContent.toLowerCase().includes(text)&&(filter.value==='all'||e.dataset.tags.split(' ').includes(filter.value)));});}
                search.addEventListener('input',apply);filter.addEventListener('change',apply);
                function anchor(){const e=document.getElementById(location.hash.slice(1));if(e&&e.matches('.question')){e.open=true;e.hidden=false;e.scrollIntoView();}}
                addEventListener('hashchange',anchor);anchor();
                </script></html>
                """);
        return h.toString();
    }
    static List<String> ranks(MultiHopData.Fact fact, List<MultiHopEvaluation.Hit> hits) {
        List<Long> ids = MultiHopEvaluation.ids(hits, 50);
        return fact.alternatives().stream().map(span -> span.stream().map(id -> id + ":" + (ids.contains(id) ? "#" + (ids.indexOf(id) + 1) : "未进50"))
                .collect(java.util.stream.Collectors.joining(", ", "[", "]"))).toList();
    }
}
