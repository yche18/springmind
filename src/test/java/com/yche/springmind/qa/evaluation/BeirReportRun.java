package com.yche.springmind.qa.evaluation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.nio.file.*;
import java.util.*;
import static com.yche.springmind.qa.evaluation.RetrievalAblation.require;

/** Offline report generation; never requests embeddings or changes official labels. */
@EnabledIfSystemProperty(named = "eval.beirReport", matches = "true")
class BeirReportRun {
    @Test void generate() throws Exception {
        BeirData.verify();
        var mapper = RerankEvaluationRun.datasetMapper();
        Map<String, BeirEvaluation.Capture> captures = new LinkedHashMap<>();
        Map<String, String> hashes = new TreeMap<>();
        for (String name : BeirData.DATASETS) {
            // Preserve the first collector's uncompressed output locally during migration.
            Path legacy = BeirData.OUTPUT.resolve(name + "-capture.json");
            if (!Files.exists(BeirData.capturePath(name)) && Files.exists(legacy)) {
                var captured = mapper.readValue(legacy.toFile(), BeirEvaluation.Capture.class);
                BeirData.writeCapture(name, captured);
                require(captured.equals(BeirData.capture(name)), "Capture compression changed content");
                Files.move(legacy, Path.of("target/beir-cache", name, "original-capture.json"));
            }
            var capture = BeirData.capture(name);
            validate(capture);
            var original = BeirData.read(name);
            require(capture.rows().stream().allMatch(r -> r.qrels().equals(original.qrels().get(r.queryId()))), "Official labels changed");
            captures.put(name, capture);
            Path comparison = BeirData.OUTPUT.resolve(name + "-comparison.json");
            ChallengeData.write(comparison, BeirEvaluation.compare(capture));
            hashes.put(comparison.getFileName().toString(), RetrievalAblation.hash(comparison));
            hashes.put(BeirData.capturePath(name).getFileName().toString(), RetrievalAblation.hash(BeirData.capturePath(name)));
        }
        Path report = BeirData.OUTPUT.resolve("report.html");
        Files.writeString(report, html(captures), StandardOpenOption.CREATE_NEW);
        hashes.put("report.html", RetrievalAblation.hash(report));
        ChallengeData.write(BeirData.OUTPUT.resolve("artifact-hashes.json"), hashes);
        localCases(captures);
    }
    static void validate(BeirEvaluation.Capture c) throws Exception {
        require(c.freezeSha256().equals(RetrievalAblation.hash(BeirData.PROTOCOL.resolve("freeze.json"))), "Freeze mismatch");
        require(c.model().equals(EmbeddingComparison.models().get("BGE_M3")), "Model mismatch");
        require(c.documents() == (c.dataset().equals("scifact") ? 5183 : 3633), "Partial corpus");
        require(c.queries() == (c.dataset().equals("scifact") ? 300 : 323), "Partial query set");
        BeirEvaluation.compare(c);
    }
    static String html(Map<String, BeirEvaluation.Capture> captures) {
        StringBuilder h = new StringBuilder("""
                <!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
                <title>BEIR · 公开基准检索对照</title><style>
                body{font:16px/1.65 system-ui,sans-serif;background:#f4f6fa;color:#182b43;margin:0}main{max-width:1250px;margin:auto;padding:35px}
                h1,h2,h3{line-height:1.3}section,details,.note{background:white;padding:22px;border-radius:12px;margin:20px 0}
                table{border-collapse:collapse;width:100%;font-size:14px}th,td{padding:9px;text-align:right;border-bottom:1px solid #dde4ed}th:first-child,td:first-child{text-align:left}
                .scroll{overflow:auto}.muted{color:#53647a}a{color:#135aa4}summary{cursor:pointer;font-weight:650}code{overflow-wrap:anywhere}small{font-size:13px}
                </style><main><h1>公开 BEIR 基准：单路、候选并集与 RRF</h1>
                <p>5,183 + 3,633 篇完整文档 · 300 + 323 道官方测试题 · 本地 BGE-M3 · 无云端推理</p>
                <div class="note"><b>这不是“单路里再做 RRF”。</b>BM25 与 BGE-M3 是两个独立的单路对照；RRF 是把这两路的排名融合之后的第三种策略。
                U25/U50 只是两路候选的去重并集，没有排序。RRF 的 50/1000 是每路输入深度，不是输出质量分数。</div>
                <p>先冻结数据、模型和参数，再测完整官方 test 集，不改题、不改标注。两路都看到相同的“标题＋正文”，没有文件名加权。
                英文 BM25 默认参数；向量精确余弦检索；RRF 等权、k=60，两种输入深度同时展示。主指标为线性增益 nDCG@10。</p>
                <p><a href="../../beir/README.md">冻结协议与来源</a> · <a href="artifact-hashes.json">结果校验清单</a> ·
                <a href="../../../../target/beir-review.html">本机案例原文（不提交仓库）</a></p>
                """);
        for (var c : captures.values()) {
            var result = BeirEvaluation.compare(c);
            h.append("<section><h2>").append(esc(c.dataset())).append("</h2><p>").append(c.documents()).append(" 篇文档 / ").append(c.queries()).append(" 道题</p>");
            h.append("<div class=scroll><table><tr><th>策略</th><th>nDCG@10</th><th>MRR@10</th><th>Recall@5</th><th>Recall@10</th><th>Recall@50</th><th>Recall@100</th><th>Recall@1000</th><th>已标注@10</th></tr>");
            result.means().forEach((mode, m) -> h.append("<tr><td>").append(mode).append("</td>").append(metrics(m)).append("</tr>"));
            h.append("</table></div><h3>候选互补：同样的最大候选预算</h3><p>U25 = 两路各取25条，去重后最多50条；U50 = 两路各取50条，最多100条。并集并不免费：仍执行两路检索。下表中的独有相关项是 query–document 对数。</p>");
            h.append("<div class=scroll><table><tr><th>并集</th><th>平均候选数</th><th>并集 Recall</th><th>BM25 同预算 Recall</th><th>BGE 同预算 Recall</th><th>关键词独有相关</th><th>向量独有相关</th><th>未进 RRF50 前10的相关项</th></tr>");
            result.unions().forEach((mode, u) -> {
                var k = result.means().get("BM25"); var v = result.means().get("BGE_M3");
                h.append("<tr><td>").append(mode).append("</td><td>").append(String.format(Locale.ROOT, "%.2f", u.meanSize())).append("</td><td>").append(pct(u.recall()))
                        .append("</td><td>").append(pct(mode.equals("U25") ? k.recall50() : k.recall100())).append("</td><td>").append(pct(mode.equals("U25") ? v.recall50() : v.recall100()))
                        .append("</td><td>").append(u.keywordExclusive()).append("</td><td>").append(u.denseExclusive()).append("</td><td>").append(u.lostAtRrf10()).append("</td></tr>");
            });
            h.append("</table></div><h3>逐题配对差异</h3><p>nDCG@10 差值，单位百分点；95% 区间来自固定种子的2,000次配对 bootstrap。区间不代表其他领域的效果。</p><table><tr><th>比较</th><th>均值差</th><th>95% 区间</th><th>胜 / 负 / 平</th></tr>");
            result.pairedEffects().forEach((mode, e) -> h.append("<tr><td>").append(mode).append("</td><td>").append(pp(e.meanDelta())).append("</td><td>[")
                    .append(pp(e.lower95())).append(", ").append(pp(e.upper95())).append("]</td><td>").append(e.improved().size()).append(" / ").append(e.regressed().size()).append(" / ").append(e.tied().size()).append("</td></tr>"));
            h.append("</table><h3>具体案例：两种方向都展示</h3><p>按 RRF50 相对 BGE 的 nDCG@10 变化自动选最大提升 / 最大下降各两题，不作为整体结论。</p>");
            var sorted = result.rows().stream().sorted(Comparator.comparingDouble((BeirEvaluation.Row r) -> delta(r)).thenComparing(BeirEvaluation.Row::queryId)).toList();
            Set<String> selected = new LinkedHashSet<>();
            sorted.stream().limit(2).forEach(r -> selected.add(r.queryId()));
            sorted.reversed().stream().limit(2).forEach(r -> selected.add(r.queryId()));
            c.rows().stream().filter(r -> selected.contains(r.queryId())).forEach(r -> detail(h, r));
            h.append("<details><summary>全部官方测试题（ID、指标与证据排名）</summary>");
            c.rows().forEach(r -> detail(h, r));
            h.append("</details><p class=muted>来源：").append(esc(c.dataset())).append(" 官方 BEIR test qrels。记录：<a href=\"").append(c.dataset()).append("-comparison.json\">逐题指标</a> · <a href=\"").append(c.dataset()).append("-capture.json.gz\">完整前1000名记录（gzip）</a></p></section>");
        }
        h.append("""
                <section><h2>如何读这些结果</h2><ul>
                <li>候选互补与最终排名提升是两件事：并集可能找到更多相关文档，但 RRF 不知道相关性，只利用排名及两路一致性。</li>
                <li>缺少官方标注的文档按标准指标记0，不代表已证明它不相关。已标注@10用于显示标注覆盖率，不是另一个准确率。</li>
                <li>SciFact 检索支持或反驳科学主张的证据，不是回答正确率。NFCorpus 标注来自网页链接关系，不是逐条人工穷尽判分；仅用于本地学术研究。</li>
                <li>这是两个英文数据集，不能代表整个 BEIR、中文应用或大厂线上系统。公开数据可能进入模型训练，不声称严格零样本。</li>
                <li>本轮不做精排、生成或参数择优，不改项目默认检索策略。旧的合成评测与待复核页面全部保留。</li></ul>
                <h3>业界使用什么</h3><p>
                <a href="https://www.elastic.co/search-labs/blog/improving-information-retrieval-elastic-stack-hybrid">Elastic：BEIR 混合检索实验</a> ·
                <a href="https://opensearch.org/blog/introducing-reciprocal-rank-fusion-hybrid-search/">AWS/OpenSearch：6个 BEIR 数据集的 RRF 实验</a> ·
                <a href="https://techcommunity.microsoft.com/blog/azure-ai-foundry-blog/raising-the-bar-for-rag-excellence-query-rewriting-and-new-semantic-ranker/4302729">Microsoft：BEIR / MIRACL 等评测来源</a></p>
                <p>OpenSearch 公布的 NFCorpus 结果为 BM25 0.3065、神经检索 0.2174、RRF 0.2977（nDCG@10）：公开基准也不是双路必胜。
                不同模型、融合、精排和参数会改变结果；不能把大厂完整链路的提升全部归功于 RRF。</p></section></main></html>
                """);
        return h.toString();
    }
    private static double delta(BeirEvaluation.Row r) { return r.metrics().get("RRF_50").ndcg10() - r.metrics().get("BGE_M3").ndcg10(); }
    private static void localCases(Map<String, BeirEvaluation.Capture> captures) throws Exception {
        StringBuilder h = new StringBuilder("<!doctype html><meta charset=utf-8><title>BEIR local case review</title><style>body{max-width:1000px;margin:35px auto;font:16px/1.6 system-ui;padding:20px}section{border-bottom:1px solid #bbb;padding:20px 0}td,th{padding:8px;text-align:left}summary{cursor:pointer}</style><h1>本机案例原文</h1><p>来自本地下载的官方语料；仅限本地研究。不提交原文，不改官方标注。每个数据集选 RRF50 相对 BGE 的最大下降和提升各两题。</p>");
        for (var c : captures.values()) {
            var data=BeirData.read(c.dataset());
            var sorted=BeirEvaluation.compare(c).rows().stream().sorted(Comparator.comparingDouble(BeirReportRun::delta).thenComparing(BeirEvaluation.Row::queryId)).toList();
            Set<String> selected=new LinkedHashSet<>();
            sorted.stream().limit(2).forEach(r->selected.add(r.queryId())); sorted.reversed().stream().limit(2).forEach(r->selected.add(r.queryId()));
            for (var row:c.rows()) if(selected.contains(row.queryId())) {
                h.append("<section><h2>").append(esc(c.dataset())).append(" / ").append(esc(row.queryId())).append("</h2><p>").append(esc(data.queries().get(row.queryId()))).append("</p>");
                detail(h,row);
                row.qrels().entrySet().stream().filter(e->e.getValue()>0).sorted(Map.Entry.comparingByKey()).forEach(e->h.append("<details><summary>Doc ").append(esc(e.getKey())).append(" · ")
                        .append(esc(data.documents().get(e.getKey()).lines().findFirst().orElseThrow())).append("</summary><p>").append(esc(data.documents().get(e.getKey()))).append("</p></details>"));
                h.append("</section>");
            }
        }
        Files.writeString(Path.of("target/beir-review.html"),h.toString(),StandardOpenOption.CREATE_NEW);
    }
    private static void detail(StringBuilder h, BeirEvaluation.CapturedRow row) {
        var k = BeirEvaluation.ids(row.bm25()); var v = BeirEvaluation.ids(row.dense());
        var r50 = BeirEvaluation.rrf(k, v, 50); var r1000 = BeirEvaluation.rrf(k, v, 1000);
        h.append("<details><summary>Query ").append(esc(row.queryId())).append(" · nDCG@10：BM25 ").append(pct(BeirEvaluation.score(k, row.qrels()).ndcg10()))
                .append(" / BGE ").append(pct(BeirEvaluation.score(v, row.qrels()).ndcg10())).append(" / RRF50 ").append(pct(BeirEvaluation.score(r50, row.qrels()).ndcg10())).append("</summary>");
        h.append("<p class=muted>为遵守来源使用条款，公开报告不复制题目和摘要全文。原文在本地 target/beir-downloads 中按 Query ID / Doc ID 查询。</p>");
        h.append("<div class=scroll><table><tr><th>正相关 Doc ID</th><th>官方分级</th><th>BM25 排名</th><th>BGE 排名</th><th>RRF50 排名</th><th>RRF1000 排名</th></tr>");
        row.qrels().entrySet().stream().filter(e -> e.getValue() > 0).sorted(Map.Entry.comparingByKey()).forEach(e -> h.append("<tr><td>").append(esc(e.getKey())).append("</td><td>").append(e.getValue())
                .append("</td><td>").append(rank(k,e.getKey())).append("</td><td>").append(rank(v,e.getKey())).append("</td><td>").append(rank(r50,e.getKey())).append("</td><td>").append(rank(r1000,e.getKey())).append("</td></tr>"));
        h.append("</table></div></details>");
    }
    private static String rank(List<String> ids, String id) { int at=ids.indexOf(id); return at < 0 ? "未入结果" : Integer.toString(at+1); }
    private static String metrics(BeirEvaluation.Metrics m) {
        StringBuilder b = new StringBuilder();
        for (double d : new double[]{m.ndcg10(),m.mrr10(),m.recall5(),m.recall10(),m.recall50(),m.recall100(),m.recall1000(),m.judgedFraction10()}) b.append("<td>").append(pct(d)).append("</td>");
        return b.toString();
    }
    private static String pct(double n) { return String.format(Locale.ROOT,"%.2f%%",n*100); }
    private static String pp(double n) { return String.format(Locale.ROOT,"%+.2f",n*100); }
    private static String esc(String s) { return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;"); }
}
