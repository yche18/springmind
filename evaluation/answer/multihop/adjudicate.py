"""Offline, user-approved five-question overlay. Original artifacts are immutable."""
import copy
import html
import json
from pathlib import Path
import run

DEST = run.OUT / "adjudicated-v1"
LOCAL_PAGE = run.LOCAL / "adjudicated-v1-review.html"
DECISIONS = run.HERE / "adjudication-v1.json"


def analyze(raw, decisions):
    assert decisions["status"] == "USER_APPROVED_ASSISTANT_RECOMMENDATIONS"
    edits = {q["id"]: q for q in decisions["questions"]}
    assert len(edits) == 5 and set(edits) == {"m1594", "m0735", "m1727", "m1748", "m2398"}
    rows = []
    for original in raw["rows"]:
        r = copy.deepcopy(original)
        edit = edits.get(r["id"])
        if edit:
            assert r["split"] == "FORMAL" and set(edit["correct"]) == set(run.ARMS)
            assert all(x is None for x in edit["correct"].values()) if edit["excluded"] else all(type(x) is bool for x in edit["correct"].values())
        r["adjustedCorrect"] = edit["correct"][r["arm"]] if edit else r["exact"]
        r["excluded"] = bool(edit and edit["excluded"])
        r["decisionSource"] = "USER_APPROVED_ADJUDICATION" if edit else "UNCHANGED_AUTOMATIC_SCORE"
        r["adjudicatedCoreGrounding"] = edit["coreGrounding"][r["arm"]] if edit else None
        r["adjudicationReason"] = edit["reason"] if edit else None
        rows.append(r)
    assert len(rows) == 240

    def summarize(group, keep_excluded=False):
        included = group if keep_excluded else [r for r in group if not r["excluded"]]
        value = lambda r: r["exact"] if r["excluded"] else r["adjustedCorrect"]
        answerable = [r for r in included if r["type"] != "null_query"]
        nulls = [r for r in included if r["type"] == "null_query"]
        return {"n": len(included), "correct": sum(value(r) for r in included),
                "adjustedScore": sum(value(r) for r in included) / len(included),
                "answerable": len(answerable), "answerableCorrect": sum(value(r) for r in answerable),
                "answerableScore": sum(value(r) for r in answerable) / len(answerable),
                "null": len(nulls), "correctNullRefusals": sum(r["refused"] for r in nulls),
                "includedAdjudicatedResponses": sum(r["decisionSource"] == "USER_APPROVED_ADJUDICATION" and not r["excluded"] for r in included),
                "fullyHumanVerifiedAccuracy": None, "fullyHumanVerifiedGroundedAccuracy": None}

    groups = {}
    for name, keep, remove in (("FORMAL", False, None), ("KEEP_AMBIGUOUS_ORIGINAL_SCORE", True, None),
                               ("EXCLUDE_ANSWER_LEAKING_M2521", False, "m2521")):
        groups[name] = {arm: summarize([r for r in rows if r["split"] == "FORMAL" and r["arm"] == arm and r["id"] != remove], keep)
                        for arm in run.ARMS}
    pairs = {}
    for before, after in (("VECTOR", "RRF"), ("VECTOR", "VECTOR_QWEN"), ("RRF", "RRF_QWEN"), ("VECTOR_QWEN", "RRF_QWEN"), ("VECTOR", "RRF_QWEN")):
        a = {r["id"]: r for r in rows if r["split"] == "FORMAL" and r["arm"] == before and not r["excluded"]}
        b = {r["id"]: r for r in rows if r["split"] == "FORMAL" and r["arm"] == after and not r["excluded"]}
        gains = [q for q in a if not a[q]["adjustedCorrect"] and b[q]["adjustedCorrect"]]
        losses = [q for q in a if a[q]["adjustedCorrect"] and not b[q]["adjustedCorrect"]]
        pairs[before + " -> " + after] = {"gains": gains, "losses": losses, "ties": len(a) - len(gains) - len(losses),
                                         "percentagePointChange": 100 * (len(gains) - len(losses)) / len(a)}
    pending = [r for r in rows if r["flags"] and r["decisionSource"] == "UNCHANGED_AUTOMATIC_SCORE"]
    return {"version": 1, "metricName": "PARTIALLY_ADJUDICATED_SCORE_NOT_FULL_HUMAN_ACCURACY", "groups": groups,
            "rawGroups": raw["groups"], "calibrationUnchanged": True, "questionsAdjudicated": 5, "responsesAdjudicated": 20,
            "excludedQuestions": ["m2398"], "paired": pairs, "rows": rows,
            "pendingFlagged": {split: {"questions": len({r["id"] for r in pending if r["split"] == split}),
                                      "responses": sum(r["split"] == split for r in pending)} for split in ("FORMAL", "CALIBRATION")}}


def render(result, detailed=False):
    esc = lambda x: html.escape(str(x), quote=True)
    sample = {q["id"]: q for q in run.read(run.SAMPLE)["questions"]}
    edits = {q["id"]: q for q in run.read(DECISIONS)["questions"]}
    inputs = {(r["id"], r["arm"]): r for r in run.inputs()} if detailed else {}
    report_link = "../../evaluation/answer/results/multihop-2026-10-11/adjudicated-v1/report.html" if detailed else "report.html"
    review_link = "adjudicated-v1-review.html" if detailed else "../../../../../target/multihop-answer/adjudicated-v1-review.html"
    raw_link = "../../evaluation/answer/results/multihop-2026-10-11/report.html" if detailed else "../report.html"
    h = ['<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>MultiHop · 用户确认裁定版 v1</title>',
         '<style>body{max-width:1200px;margin:32px auto;padding:0 22px;background:#f3f6f8;color:#193247;font:16px/1.75 system-ui,"Microsoft YaHei"}details,section{background:white;border:1px solid #d6e0e7;border-radius:10px;padding:18px;margin:18px 0}summary{cursor:pointer;font-weight:bold}table{border-collapse:collapse;width:100%}td,th{padding:9px;border-bottom:1px solid #ddd;text-align:left}pre{white-space:pre-wrap;overflow-wrap:anywhere;font:inherit;background:#f6f8fa;padding:12px}.note{background:#fff0cc;padding:15px}select,input,button{padding:9px;margin:5px}a{color:#00698e}.scroll{overflow:auto}</style>',
         '<h1>MultiHop-RAG · 用户确认裁定版 v1</h1><p class="note">仅5题／20份回答按用户确认口径裁定，其余沿用原始自动评分。排除歧义题 m2398 后分母49题（43题有答案＋6题无答案）。不是全量人工验证准确率，也不是忠实性。原始标签、回答、严格匹配和逐字引用指标全部保留。新增模型调用：0。</p>',
         f'<p><a href="{report_link}">裁定版报告</a> · <a href="{review_link}">裁定版详细回答与证据</a> · <a href="{raw_link}">原始50题报告</a></p>',
         '<section><h2>原始与调整后指标</h2><div class="scroll"><table><tr><th>方式</th><th>原始严格分数</th><th>调整后（部分裁定）</th><th>有答案题</th><th>无答案正确拒答</th></tr>']
    for arm in run.ARMS:
        s = result["groups"]["FORMAL"][arm]; old = result["rawGroups"]["FORMAL"][arm]
        h.append(f'<tr><td>{arm}</td><td>{old["correct"]}/50 ({old["em"]:.2%})</td><td>{s["correct"]}/49 ({s["adjustedScore"]:.2%})</td><td>{s["answerableCorrect"]}/43 ({s["answerableScore"]:.2%})</td><td>{s["correctNullRefusals"]}/6</td></tr>')
    h.append('</table></div><p>分子变化：VECTOR +3，RRF +1，两组精排各+2；歧义题原先四组均未得分，统一移出分母。校准集10题不变且不计入正式指标。</p></section>')
    h.append('<section><h2>结论变化与适用范围</h2><p>向量与RRF调整后均为26/49；两组精排均为28/49。完整链路相对向量单路由原始46%→52%（+6个百分点），变为53.06%→57.14%（+4.08个百分点，净多答对2题）。不能把这4.08个百分点全部归因于双路融合，向量＋精排也达到相同分数。</p><p>不是全量人工语义准确率；不宣称显著提升、线上用户效果或57.14%忠实性。其余题仍按自动规则计分。</p></section>')
    h.append('<section><h2>敏感性与配对对照</h2><p>不排除歧义题、该题保留原分：26/50、26/50、28/50、28/50（52%、52%、56%、56%）。再排除泄露答案的 m2521：25/48、25/48、27/48、27/48。差异不是只靠移除一道题创造出来。</p>')
    for pair, stats in result["paired"].items():
        h.append(f'<p>{esc(pair)}：改善{len(stats["gains"])}题／退步{len(stats["losses"])}题／持平{stats["ties"]}题，变化{stats["percentagePointChange"]:+.2f}个百分点。</p>')
    h.append('</section><section><h2>复核进度</h2><p>本次5题已裁定，不再混入待复核列表。正式集仍有24道自动标记题／96份回答，校准集另5题／20份回答待确认；未标记不等于人工通过。</p><p>已确认裁定为只读。其余回答可填写意见并导出；浏览器草稿不会覆盖本次裁定，也不自动改变指标。原始引用连续匹配失败不会因为语义裁定通过就被删除。</p></section>')
    h.append('<input id="search" placeholder="搜索题号或问题"><select id="filter"><option value="decided">本次已裁定5题</option><option value="pending">其余待复核</option><option value="all">全部60题</option><option value="FORMAL">正式集</option><option value="CALIBRATION">校准集</option></select><button onclick="download()">导出后续复核意见</button>')
    for qid, q in sample.items():
        rows = sorted([r for r in result["rows"] if r["id"] == qid], key=lambda r: run.ARMS.index(r["arm"]))
        decided = qid in edits
        pending = not decided and any(r["flags"] for r in rows)
        h.append(f'<details class="question" id="{qid}" data-split="{q["split"]}" data-decided="{str(decided).lower()}" data-pending="{str(pending).lower()}"><summary>{qid} · {esc(q["query"])}</summary><p>原官方答案：{esc(q["answer"])} · {q["split"]}</p>')
        if decided:
            edit = edits[qid]
            h.append(f'<p class="note">已确认：{esc(edit["action"])}。裁定答案：{esc(edit["adjudicatedAnswer"])}。{esc(edit["reason"])}</p>')
        else:
            h.append('<p>未作人工裁定；当前分数继承自动严格匹配。</p>')
        for r in rows:
            meaning = "不计分（歧义题）" if r["excluded"] else ("正确" if r["adjustedCorrect"] else "错误／未答出")
            h.append(f'<h3>{r["arm"]}</h3><p>原始严格匹配：{r["exact"]} → 调整得分：{meaning}；依据：{esc(r["decisionSource"])}</p>')
            if decided:
                h.append(f'<p>核心命题的语义证据支持：{esc(r["adjudicatedCoreGrounding"])}（不是逐字引用检查）。</p>')
            h.append(f'<p>原始引用逐字匹配：{r["validCitationCount"]}/{r["citationCount"]}；原始参考支持代理：{r["referenceSupportedExactProxy"]}，均未改动。</p><pre>{esc(json.dumps(r["output"], ensure_ascii=False, indent=2))}</pre>')
            if not decided:
                key = qid + '-' + r["arm"]
                h.append(f'<label>语义答案<select data-key="{key}/answer"><option value="">待确认</option><option>正确</option><option>错误</option><option>标注存疑</option></select></label><label>核心结论有证据支持<select data-key="{key}/grounded"><option value="">待确认</option><option>是</option><option>否</option><option>不适用（拒答）</option></select></label><input data-key="{key}/note" placeholder="备注">')
            if detailed:
                h.append('<details><summary>实际输入证据（本题本组）</summary>')
                for e in inputs[qid, r["arm"]]["evidence"]:
                    h.append('<pre>' + esc(e["text"]) + '</pre>')
                h.append('</details>')
        if detailed:
            h.append('<details><summary>原始官方参考事实（保留）</summary>')
            for f in q["facts"]:
                h.append('<blockquote>' + esc(f["quote"]) + '</blockquote>')
            h.append('</details>')
        h.append('</details>')
    h.append('''<script>
const prefix='multihop-answer-adjudicated-v1/';let records={};try{records=JSON.parse(localStorage.getItem(prefix)||'{}')}catch(e){}
document.querySelectorAll('[data-key]').forEach(e=>{e.value=records[e.dataset.key]||'';e.addEventListener('change',()=>{records[e.dataset.key]=e.value;try{localStorage.setItem(prefix,JSON.stringify(records))}catch(e){alert('保存失败，请导出记录')}})});
function download(){const a=document.createElement('a');a.href=URL.createObjectURL(new Blob([JSON.stringify({version:1,base:'adjudicated-v1',status:'PENDING_FOLLOWUP_REVIEW',records},null,2)],{type:'application/json'}));a.download='multihop-followup-review.json';a.click();URL.revokeObjectURL(a.href)}
const search=document.getElementById('search'),filter=document.getElementById('filter');function apply(){document.querySelectorAll('.question').forEach(e=>e.hidden=!(e.textContent.toLowerCase().includes(search.value.toLowerCase())&&(filter.value==='all'||filter.value==='decided'&&e.dataset.decided==='true'||filter.value==='pending'&&e.dataset.pending==='true'||e.dataset.split===filter.value)))}search.addEventListener('input',apply);filter.addEventListener('change',apply);apply();function anchor(){const e=document.getElementById(location.hash.slice(1));if(e&&e.classList.contains('question')){e.hidden=false;e.open=true;e.scrollIntoView()}}addEventListener('hashchange',anchor);anchor();</script></html>''')
    return '\n'.join(h)


def main():
    decisions = run.read(DECISIONS)
    assert run.filehash(run.OUT / "formal-metrics.json") == decisions["rawMetricsSha256"]
    assert run.filehash(run.OUT / "formal-capture.json") == decisions["rawCaptureSha256"]
    # Binding the old files before/after proves the overlay did not replace them.
    originals = {p.relative_to(run.ROOT).as_posix(): run.filehash(p) for p in run.OUT.iterdir() if p.is_file()}
    originals["target/multihop-answer/review.html"] = run.filehash(run.LOCAL / "review.html")
    run.verify()
    result = analyze(run.read(run.OUT / "formal-metrics.json"), decisions)
    run.write_new(DEST / "metrics.json", result)
    run.write_new(DEST / "decisions.json", decisions)
    pending = [{"id": r["id"], "arm": r["arm"], "split": r["split"], "flags": r["flags"], "semanticCorrect": None,
                "grounded": None, "note": ""} for r in result["rows"] if r["flags"] and r["decisionSource"] == "UNCHANGED_AUTOMATIC_SCORE"]
    run.write_new(DEST / "pending-review.json", pending)
    for path, detailed in ((DEST / "report.html", False), (LOCAL_PAGE, True)):
        with path.open("x", encoding="utf-8", newline="\n") as stream:
            stream.write(render(result, detailed))
    assert all(run.filehash(run.ROOT / name) == value for name, value in originals.items())
    run.write_new(DEST / "verification.json", {"newModelCalls": 0, "originalHashesPreserved": originals,
        "decisionSha256": run.filehash(DECISIONS), "implementationSha256": run.filehash(Path(__file__)),
        "derivedHashes": {p.relative_to(run.ROOT).as_posix(): run.filehash(p) for p in [DEST / "metrics.json", DEST / "decisions.json", DEST / "pending-review.json", DEST / "report.html", LOCAL_PAGE]}})
    print(json.dumps({"groups": result["groups"], "pending": result["pendingFlagged"]}, indent=2))


if __name__ == "__main__":
    main()
