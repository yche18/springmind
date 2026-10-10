package com.yche.springmind.qa.evaluation;

import com.yche.springmind.qa.rag.EvidenceReranker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.ai.document.Document;
import java.nio.file.*;
import java.util.*;
import static com.yche.springmind.qa.evaluation.RetrievalAblation.require;
import static com.yche.springmind.qa.evaluation.MultiHopReportRun.esc;
import static com.yche.springmind.qa.evaluation.MultiHopReportRun.pct;

/** Offline report and independent reconstruction of every reranked production context. */
@EnabledIfSystemProperty(named = "eval.multihopRerankReport", matches = "true")
class MultiHopRerankReportRun {
    @Test void report() throws Exception {
        var capture = MultiHopData.M.readValue(MultiHopRerank.OUT.resolve("capture.json").toFile(), MultiHopRerank.Capture.class);
        var report = MultiHopRerankAnalysis.analyze(capture);
        var corpus = MultiHopData.corpus(); MultiHopData.verify(corpus);
        var sample = MultiHopData.sample(); var baseline = MultiHopRerank.baseline(); var inputs = MultiHopRerank.inputs();
        Map<String, List<Document>> candidates = new LinkedHashMap<>(); int[] cursor = {0};
        for (int i = 0; i < sample.questions().size(); i++) {
            var q = sample.questions().get(i); var old = baseline.rows().get(i); var saved = capture.rows().get(i);
            var reproduced = MultiHopReplay.run(corpus, q, old.keyword(), old.vector(), (query, docs) -> {
                try {
                    var input = inputs.get(cursor[0]++);
                    require(input.equals(MultiHopRerank.input(q.id(), input.mode(), query, docs)), "Input drift on replay");
                    candidates.put(q.id() + "/" + input.mode(), docs);
                    return MultiHopRerank.replay(saved.outcomes().get(input.mode()), docs);
                } catch (Exception e) { throw new IllegalStateException(e); }
            });
            require(reproduced.equals(saved.contexts()), "Context replay mismatch: " + q.id());
        }
        ChallengeData.write(MultiHopRerank.OUT.resolve("comparison.json"), report);
        Files.writeString(MultiHopRerank.OUT.resolve("report.html"), html(report, capture, corpus, candidates, false), StandardOpenOption.CREATE_NEW);
        Path local = Path.of("target/multihop-rerank-review"); Files.createDirectories(local);
        Files.writeString(local.resolve("review.html"), html(report, capture, corpus, candidates, true), StandardOpenOption.CREATE_NEW);
        Map<String, String> hashes = new TreeMap<>();
        for (String file : List.of("capture.json", "comparison.json", "report.html", "request-ledger-hashes.json"))
            hashes.put(file, RetrievalAblation.hash(MultiHopRerank.OUT.resolve(file)));
        hashes.put("local-review.html", RetrievalAblation.hash(local.resolve("review.html")));
        for (String source : List.of("LiveMultiHopRerankRun", "MultiHopRerankAnalysis", "MultiHopRerankReportRun"))
            hashes.put(source + ".java", RetrievalAblation.hash(Path.of("src/test/java/com/yche/springmind/qa/evaluation", source + ".java")));
        ChallengeData.write(MultiHopRerank.OUT.resolve("verification.json"), Map.of("contextsReproduced", 180, "newProviderCalls", 0, "hashes", hashes));
        System.out.println(MultiHopData.M.writerWithDefaultPrettyPrinter().writeValueAsString(report.groups()));
    }
    static String html(MultiHopRerankAnalysis.Report report, MultiHopRerank.Capture capture, MultiHopData.Corpus corpus,
                       Map<String, List<Document>> candidates, boolean review) throws Exception {
        var sample = MultiHopData.sample(); var baseline = MultiHopRerank.baseline(); var inputs = MultiHopRerank.inputs();
        StringBuilder h = new StringBuilder("""
                <!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
                <title>MultiHop-RAG · Qwen精排前后对照</title><style>
                body{font:16px/1.75 system-ui,"Microsoft YaHei",sans-serif;color:#172c3d;background:#f3f6f8;max-width:1200px;margin:30px auto;padding:0 22px}
                h1{line-height:1.25}.card,details{background:white;border:1px solid #d7e2e9;border-radius:12px;padding:18px 22px;margin:18px 0}
                .note{background:#fff4d9;border-left:4px solid #df9c29;padding:14px}table{border-collapse:collapse;width:100%;font-variant-numeric:tabular-nums}
                th,td{border-bottom:1px solid #dbe4e9;padding:8px;text-align:left}th{background:#eef3f7}.scroll{overflow:auto}
                a{color:#11658d}summary{cursor:pointer;font-weight:650}pre{white-space:pre-wrap;overflow-wrap:anywhere;font:inherit;background:#f6f8fa;padding:12px}
                blockquote{border-left:3px solid #9ebccc;padding:8px 16px;margin:12px 0}.small{font-size:13px;color:#546b78}input,select{padding:10px;border:1px solid #b9ccd6;border-radius:6px;margin:0 10px 10px 0}
                </style><h1>MultiHop-RAG · Qwen 精排前后对照</h1>
                <p>复用50题正式集 + 10题校准集 · 已有召回不变 · 三种检索 × 精排开/关 · 未生成答案</p>
                <p class=note>这里统计证据覆盖，不是答案准确率。沿用官方标注，用户部分抽查未发现问题，不代表60题全部人工复核。
                这轮新增调用仅为 Qwen 精排；失败回退也纳入指标。旧版“云端调用0”仅描述先前的本地检索实验。</p>
                """);
        h.append("<p><a href=\"").append(review ? "../../evaluation/rerank/results/multihop-2026-10-11/report.html" : "../../../../target/multihop-rerank-review/review.html")
                .append("\">").append(review ? "返回指标报告" : "详细证据与模型分数（本地复核页）").append("</a> · <a href=\"")
                .append(review ? "../../evaluation/retrieval/results/multihop-2026-10-11/report.html" : "../../../retrieval/results/multihop-2026-10-11/report.html")
                .append("\">原始召回报告（保留不改）</a></p>");
        h.append("<p class=small>模型：").append(esc(report.model())).append(" · 请求尝试：").append(report.providerAttempts())
                .append(" · 生成调用：0 · 评测超时10秒（非生产2秒SLA） · 实际计费未知，请查服务商账单。</p>");
        for (String split : List.of("FORMAL", "CALIBRATION")) {
            h.append("<section class=card><h2>").append(split.equals("FORMAL") ? "正式集：50题 / 44题有证据" : "校准集：10题 / 9题有证据").append("</h2>")
                    .append("<div class=scroll><table><tr><th>检索方式</th><th>最终事实召回：前 → 后</th><th>全部事实找齐：前 → 后</th><th>改善 / 持平 / 退步</th><th>成功 / 回退</th><th>延迟 p50 / p95</th></tr>");
            report.groups().get(split).forEach((mode, s) -> h.append("<tr><td>").append(mode).append("</td><td>").append(pct(s.recallBefore())).append(" → ")
                    .append(pct(s.recallAfter())).append("</td><td>").append(s.completeBefore()).append(" → ").append(s.completeAfter()).append(" / ").append(s.answerable())
                    .append("</td><td>").append(s.improved()).append(" / ").append(s.tied()).append(" / ").append(s.worsened()).append("</td><td>").append(s.applied()).append(" / ")
                    .append(s.fallback()).append("</td><td>").append(s.p50Ms()).append(" / ").append(s.p95Ms()).append(" ms</td></tr>"));
            h.append("</table></div><h3>候选可用性与排序阶段</h3><div class=scroll><table><tr><th>方式</th><th>候选窗口全集事实覆盖 / 找齐</th><th>前5窗口覆盖：前 → 后</th><th>上下文平均 tokens：前 → 后</th><th>被截断预览的窗口数</th></tr>");
            report.groups().get(split).forEach((mode, s) -> h.append("<tr><td>").append(mode).append("</td><td>").append(pct(s.candidateRecall())).append(" / ")
                    .append(s.candidateComplete()).append("</td><td>").append(pct(s.top5RecallBefore())).append(" → ").append(pct(s.top5RecallAfter())).append("</td><td>")
                    .append(Math.round(s.meanTokensBefore())).append(" → ").append(Math.round(s.meanTokensAfter())).append("</td><td>").append(s.clippedWindows()).append("</td></tr>"));
            h.append("</table></div><p class=small>“前5窗口”已含邻接扩展，尚未经过4096-token预算；不是原始切片Recall@5。候选全集覆盖是可用性上限，不是可实现的答案准确率。</p></section>");
        }
        h.append("<section class=card><h2>如何解读</h2><ul><li>精排只能重排已有候选窗口，找不回候选池之外的证据。</li>")
                .append("<li>三组的候选池不同，所以先看每组自身的前后变化，再比较完整链路；改善不代表仅由某一通道带来。</li>")
                .append("<li>沿用4096字符预览、5窗口和4096 tokens限制。被截断的后半段可能影响模型判断；大窗口靠前也可能挤掉后续证据。</li>")
                .append("<li>无答案题不计召回分母，相关性分数不能替代拒答评测。最终得分不是正确回答率，也不表示模型没有幻觉。</li>")
                .append("<li>正式集已暴露，样本很小；不据此反复调参或声称统计显著。下列好坏案例及原先4道互补题都保留。</li></ul></section>")
                .append("<h2>逐题前后对照</h2><input id=search placeholder=\"搜索题号或问题\"><select id=filter><option value=all>全部60题</option><option value=FORMAL>正式集</option><option value=CALIBRATION>校准集</option><option value=gain>RRF精排改善</option><option value=loss>RRF精排退步</option><option value=mutual>原先4道互补题</option><option value=null_query>无答案题</option></select>");
        int cursor = 0;
        for (int i = 0; i < report.rows().size(); i++) {
            var row = report.rows().get(i); var q = sample.questions().get(i); var saved = capture.rows().get(i); var old = baseline.rows().get(i);
            var rrf = row.modes().get("RRF"); String tag = q.split() + " " + q.type();
            if (rrf.before().factRecall() != null) tag += rrf.after().factRecall() > rrf.before().factRecall() ? " gain" : rrf.after().factRecall() < rrf.before().factRecall() ? " loss" : " tie";
            if (Set.of("m0335", "m1682", "m1797", "m0640").contains(q.id())) tag += " mutual";
            h.append("<details class=question id=\"").append(q.id()).append("\" data-tags=\"").append(tag).append("\"><summary>").append(q.id()).append(" · ")
                    .append(esc(q.query())).append("</summary><p class=small>").append(tag).append("</p><p>官方答案（未发送给精排模型）：<b>").append(esc(q.answer())).append("</b></p>");
            if (q.id().equals("m2521")) h.append("<p class=note>原题已透露答案实体Meta；以后答对不能单独归因于检索。</p>");
            for (String mode : MultiHopRerank.MODES) {
                var pair = row.modes().get(mode); var input = inputs.get(cursor++); var outcome = saved.outcomes().get(mode);
                h.append("<h3>").append(mode).append("：").append(pct(pair.before().factRecall())).append(" → ").append(pct(pair.after().factRecall())).append("</h3><p>新增事实 ")
                        .append(esc(pair.gainedFacts())).append("；丢失事实 ").append(esc(pair.lostFacts())).append("；状态 ").append(pair.status()).append("；").append(pair.elapsedMs()).append("ms。</p>")
                        .append("<p class=small>窗口前5：").append(esc(input.candidates().stream().limit(5).map(MultiHopRerank.Candidate::id).toList()))
                        .append(" → ").append(esc(outcome.order().stream().limit(5).toList())).append("。窗口ID仅在当前题目/方式内有效。</p>");
                if (review) {
                    Map<String, Document> docs = new HashMap<>(); candidates.get(q.id() + "/" + mode).forEach(d -> docs.put(d.getId(), d));
                    h.append("<details><summary>全部候选窗口与模型分数</summary>");
                    for (String id : outcome.order()) h.append("<p><b>").append(id).append("</b> · score=").append(outcome.scores().get(id)).append("</p><pre>")
                            .append(esc(MultiHopRerank.preview(docs.get(id).getText()))).append("</pre>");
                    h.append("</details>");
                    for (String phase : List.of("BEFORE", "AFTER")) {
                        var context = phase.equals("BEFORE") ? old.contexts().get(mode) : saved.contexts().get(mode);
                        h.append("<details><summary>").append(phase).append(" · 最终证据切片 · ").append(context.tokens()).append(" tokens</summary>");
                        for (long id : context.chunks()) h.append("<p class=small>chunk ").append(id).append("</p><pre>").append(esc(corpus.chunks().get(id).text())).append("</pre>");
                        h.append("</details>");
                    }
                }
            }
            h.append("<h3>官方参考事实</h3>");
            for (var fact : q.facts()) h.append("<p>").append(fact.id()).append(" · document ").append(fact.documentId()).append(" · span ").append(esc(fact.alternatives()))
                    .append("</p><blockquote>").append(esc(fact.quote())).append("</blockquote>");
            h.append("</details>");
        }
        h.append("""
                <script>const search=document.getElementById('search'),filter=document.getElementById('filter');function apply(){document.querySelectorAll('.question').forEach(e=>e.hidden=!(e.textContent.toLowerCase().includes(search.value.toLowerCase())&&(filter.value==='all'||e.dataset.tags.split(' ').includes(filter.value))));}search.addEventListener('input',apply);filter.addEventListener('change',apply);function anchor(){let e=document.getElementById(location.hash.slice(1));if(e&&e.matches('.question')){e.open=true;e.hidden=false;e.scrollIntoView();}}addEventListener('hashchange',anchor);anchor();</script></html>
                """);
        return h.toString();
    }
}
