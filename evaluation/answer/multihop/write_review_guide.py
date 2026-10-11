"""Post-run review priorities, separate from immutable automatic scoring."""
import html
import run

PRIORITIES = [
    ("m1594", "优先：官方答案疑似标错", "官方答案是 Apple，但三条官方参考事实分别说 Google 是搜索选项、Google 举办硬件活动、Google 被指侵害出版商收入。向量组回答 Google。请判断是否属于数据集标签错误；本轮仍按原标签计分，没有改成对模型有利的答案。"),
    ("m0735", "优先：True / Yes 同义表达", "官方答案 True，四组都回答 Yes。严格匹配全部失败，但语义可能一致。请连同解释和证据确认；若接受，四组采用同一规则，不能只修正某一组。"),
    ("m1727", "优先：短答案与解释矛盾", "官方答案 no，四组的 answer 都是 Yes，但解释均说明 TechCrunch 的报道早于 The Verge，否定了问题中的 after。不能仅凭解释正确就自动把整个回答计为正确。"),
    ("m1748", "优先：否定问句的极性", "问题问是否没有变化，官方 no。向量及两组精排回答 Yes, there was a change；后半句表达存在变化，却与开头 Yes 对否定问句的回应冲突。请按完整回答判断，不要用 yes/no 前缀提取自动改分。"),
    ("m2398", "优先：题意有歧义", "官方 Yes 表示报道仍一致；三组回答 No，理解为报道范围从全球豪华雪场变成加拿大雪场。consistent 究竟指主题、范围还是报道倾向不明确。建议先标记题意/标签存疑，不急着改正确答案。"),
]
CASES = [
    ("m1273", "正例：证据覆盖转化为回答", "向量单路缺少第二篇报道而拒答；RRF、向量＋精排、RRF＋精排均答 Consistent，并通过完整参考事实支持代理。这是比只看实体命中更扎实的改善案例，仍可抽查引用含义。"),
    ("m0179", "反例：精排后丢失另一来源", "RRF 能比较两篇板球报道并答 Yes；RRF＋精排只保留后一来源而拒答。此前已经观察到这题精排丢失一条官方事实，本次影响延续到了答案。"),
    ("m2449", "警惕：答对实体不等于完整论证", "两组未精排都拒答；两组精排答对 Sam Bankman-Fried，但解释主动承认题目中多项条件无证据。严格匹配改善，不应直接宣传为完整、有依据的正确回答改善。"),
    ("m2488", "精排补齐来源，但引用仍需核验", "RRF＋精排的解释分别覆盖两个 TechCrunch 主张和 The Verge 的市场定义。自动引用检查仍有失败，因此不能直接计为人工验证的忠实回答。RRF 未精排虽然也答 Yes，却承认缺少一个指定来源条件。"),
    ("m0196", "警惕：严格分数退步不一定更不可靠", "向量单路回答 Yes，但说明 TechCrunch 证据缺失；精排后拒答，严格匹配反而失分。需要区分答案命中与遵守证据约束。"),
    ("m1987", "待确认：替代证据与推理稳定性", "RRF＋精排答 Yes，其他组拒答；向量组拒答理由还同时提到10月31日文章、又说没有11月5日前文章。官方参考集合含另一个来源，完整参考事实代理不通过，需看实际引用是否已足够。"),
    ("m2521", "固定保留：题目泄露答案实体", "题目直接写出 Meta，四组均命中。排除本题后的49题严格得分分别22、24、25、25；相对顺序不变。不把本题答对当作检索带来的证据。"),
]


def main():
    report = run.read(run.OUT / "formal-metrics.json")
    sample = {q["id"]: q for q in run.read(run.SAMPLE)["questions"]}
    esc = lambda value: html.escape(str(value), quote=True)
    h = ['<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>答案评测 · 复核入口</title>',
         '<style>body{max-width:1050px;margin:36px auto;padding:0 22px;background:#f3f6f8;color:#163347;font:16px/1.8 system-ui,"Microsoft YaHei"}section{background:white;padding:20px;border:1px solid #d7e2e9;border-radius:12px;margin:18px 0}a{color:#00698e}table{border-collapse:collapse;width:100%}td,th{padding:10px;text-align:left;border-bottom:1px solid #ddd}.note{background:#fff0cc;padding:16px}</style>',
         '<h1>最后一轮答案评测：先看这5题</h1><p>四组 × 60题已完成；没有重新检索或精排。自动指标与人工复核分开保存。</p>',
         '<p class="note">本次发现官方标签疑似错误、Yes/True 同义表达，以及答案和解释冲突。以下均是待确认建议，不是已经人工修正后的分数。原始题目、标签、回答和所有分数保持不变。</p>',
         '<p><a href="report.html">完整自动指标</a> · <a href="../../../../target/multihop-answer/review.html">全部回答和证据复核页</a> · <a href="README.md">实验分析</a></p>',
         '<section><h2>正式集原始严格匹配</h2><table><tr><th>组别</th><th>全50题（含正确拒答）</th><th>有答案44题</th><th>无答案6题</th></tr>']
    for arm in run.ARMS:
        s = report["groups"]["FORMAL"][arm]
        h.append(f'<tr><td>{arm}</td><td>{s["correct"]}/50 ({s["em"]:.0%})</td><td>{s["answerableCorrect"]}/44 ({s["answerableEM"]:.2%})</td><td>{s["correctNullRefusals"]}/6</td></tr>')
    h.append('</table><p>这里是冻结规则下的严格匹配，不是人工裁定的语义正确率，也不是忠实性。50题只有小样本观察，不宣称显著或普遍提升。</p></section><h2>第一优先级：5题判分/标注问题</h2>')
    for entries in (PRIORITIES, CASES):
        for qid, title, note in entries:
            q = sample[qid]
            h.append(f'<section id="{qid}"><h3>{qid} · {esc(title)}</h3><p>{esc(note)}</p><p>{esc(q["query"])}</p><p>官方答案：<b>{esc(q["answer"])}</b></p><a href="../../../../target/multihop-answer/review.html#{qid}">查看本题四组回答、解释与证据，并填写复核意见 →</a></section>')
        if entries is PRIORITIES:
            h.append('<h2>第二优先级：改善、退步与证据边界案例</h2>')
    h.append('<section><h2>怎么复核，不必重标资料</h2><ol><li>先看上面5题，判断语义答案、标签是否有问题；特别留意否定问句，不只看 Yes/No。</li><li>再按需看正反例。复核页每组有“语义答案”和“引用支持”两项，正确拒答的引用支持选择不适用。</li><li>改动只记录在浏览器里，完成后点“导出复核记录”，把 JSON 文件发回。不要自行编辑冻结标签或原始结果。</li><li>正式集共29道题、116份回答被自动标记，校准集另5道题、20份回答。优先5题并不等于完成全部忠实性审计；要报告全体人工验证准确率，仍需完成相应范围的裁定。</li></ol><p>严格 EM 可直接复现；语义/忠实性得分保持待确认，不能因为引用文字存在或模型自称有依据就判通过。</p></section></html>')
    with (run.OUT / "review-guide.html").open("x", encoding="utf-8", newline="\n") as stream:
        stream.write('\n'.join(h))
    run.write_new(run.OUT / "review-priorities.json", {"status": "PENDING_USER_REVIEW", "labelChanges": 0,
        "primary": [{"id": q, "title": title, "note": note} for q, title, note in PRIORITIES],
        "cases": [{"id": q, "title": title, "note": note} for q, title, note in CASES]})


if __name__ == "__main__":
    main()
