"""Bounded paid generation and offline scoring of frozen cached contexts (stdlib only)."""
import argparse
import collections
import datetime
import hashlib
import html
import json
import os
from pathlib import Path
import re
import statistics
import time
import unicodedata
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[3]
HERE = ROOT / "evaluation/answer/multihop"
OUT = ROOT / "evaluation/answer/results/multihop-2026-10-11"
LOCAL = ROOT / "target/multihop-answer"
CACHE = LOCAL / "cache"
SAMPLE = ROOT / "evaluation/retrieval/multihop/sample.json"
ARMS = ("VECTOR", "RRF", "VECTOR_QWEN", "RRF_QWEN")
ENDPOINT = "https://api.deepseek.com/chat/completions"
OPTIONS = {"model": "deepseek-flash", "temperature": 0, "max_tokens": 900,
           "thinking": {"type": "disabled"}, "response_format": {"type": "json_object"}, "stream": False}


def read(path):
    return json.loads(path.read_text(encoding="utf-8"))


def encoded(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")


def sha(value):
    return hashlib.sha256(value).hexdigest()


def filehash(path):
    return sha(path.read_bytes())


def write_new(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    # Exclusive create + fsync: ambiguous interrupted files are never silently retried.
    with path.open("x", encoding="utf-8", newline="\n") as stream:
        json.dump(value, stream, ensure_ascii=False, indent=2)
        stream.write("\n")
        stream.flush()
        os.fsync(stream.fileno())


def now():
    return datetime.datetime.now(datetime.timezone.utc).isoformat()


def body(row):
    return dict(OPTIONS, messages=[{"role": "system", "content": (HERE / "prompt.txt").read_text(encoding="utf-8")},
                                  {"role": "user", "content": "Question:\n" + row["query"] + "\n\nEvidence:\n" + row["context"]}])


def inputs():
    rows = read(LOCAL / "inputs.json")
    questions = {q["id"]: q for q in read(SAMPLE)["questions"]}
    assert len(rows) == 240 and len(questions) == 60
    assert len({(r["id"], r["arm"]) for r in rows}) == 240
    for row in rows:
        q = questions[row["id"]]
        assert row["arm"] in ARMS and row["split"] == q["split"] and row["query"] == q["query"]
        assert sha(row["context"].encode("utf-8")) == row["contextSha256"]
        assert len(row["evidence"]) <= 5
    return rows


def source_hashes():
    paths = [SAMPLE, HERE / "prompt.txt", HERE / "README.md", HERE / "run.py", LOCAL / "inputs.json",
             ROOT / "src/test/java/com/yche/springmind/qa/evaluation/MultiHopAnswerExportRun.java",
             ROOT / "evaluation/retrieval/multihop/freeze.json",
             ROOT / "evaluation/retrieval/results/multihop-2026-10-11/capture.json",
             ROOT / "evaluation/rerank/results/multihop-2026-10-11/capture.json"]
    return {p.relative_to(ROOT).as_posix(): filehash(p) for p in paths}


def manifest():
    rows = inputs()
    return {"version": 1, "endpoint": ENDPOINT, "options": OPTIONS, "maxAttempts": 240,
            "hashes": source_hashes(), "inputs": [{"id": r["id"], "arm": r["arm"], "split": r["split"],
            "contextSha256": r["contextSha256"], "requestSha256": sha(encoded(body(r)))} for r in rows]}


def verify():
    frozen = read(HERE / "freeze.json")
    assert frozen == manifest(), "Frozen protocol/input drift"
    return frozen


def config():
    values = {}
    envfile = ROOT / ".env"
    if envfile.exists():
        for line in envfile.read_text(encoding="utf-8-sig").splitlines():
            key, sep, value = line.partition("=")
            if sep and key in ("AI_BASE_URL", "AI_CHAT_MODEL", "AI_API_KEY"):
                values[key] = value.strip().strip('"').strip("'")
    for key in ("AI_BASE_URL", "AI_CHAT_MODEL", "AI_API_KEY"):
        if os.environ.get(key):
            values[key] = os.environ[key]
    assert values.get("AI_BASE_URL", "").rstrip("/") + "/chat/completions" == ENDPOINT, "Configured endpoint drift"
    assert values.get("AI_CHAT_MODEL") == OPTIONS["model"], "Configured model drift"
    assert values.get("AI_API_KEY"), "Missing generation credential"
    return values


def collect(split, confirmed):
    assert confirmed, "Paid generation must be explicitly confirmed"
    verify()
    if split == "FORMAL":
        decision = read(HERE / "calibration.json")
        assert decision["approved"] is True and decision["freezeSha256"] == filehash(HERE / "freeze.json")
        assert decision["captureSha256"] == filehash(OUT / "calibration-capture.json")
    output = OUT / (split.lower() + "-capture.json")
    assert not output.exists(), "Completed capture is immutable"
    settings = config()
    CACHE.mkdir(parents=True, exist_ok=True)
    freeze_hash = filehash(HERE / "freeze.json")
    failures = 0
    outcomes = []
    rows = [r for r in inputs() if r["split"] == split]
    # Rotate arm order per question to avoid always giving one arm later service/cache conditions.
    order = {q: i for i, q in enumerate(dict.fromkeys(r["id"] for r in rows))}
    rows.sort(key=lambda r: (order[r["id"]], (ARMS.index(r["arm"]) - order[r["id"]]) % 4))
    for index, row in enumerate(rows):
        name = row["id"] + "-" + row["arm"]
        intent = CACHE / (name + ".intent.json")
        saved = CACHE / (name + ".json")
        payload = encoded(body(row))
        request_hash = sha(payload)
        if saved.exists():
            result = read(saved)
            assert result["requestSha256"] == request_hash and result["freezeSha256"] == freeze_hash
        else:
            assert failures < 3, "Three consecutive provider errors: stop"
            assert not intent.exists(), "Unmatched prior intent; investigate before any retry: " + name
            assert len(list(CACHE.glob("*.intent.json"))) < 240, "Attempt budget exhausted"
            write_new(intent, {"startedAt": now(), "requestSha256": request_hash, "freezeSha256": freeze_hash})
            request = urllib.request.Request(ENDPOINT, data=payload, headers={"Content-Type": "application/json",
                "Authorization": "Bearer " + settings["AI_API_KEY"]}, method="POST")
            start = time.monotonic()
            result = {"id": row["id"], "arm": row["arm"], "split": split, "requestSha256": request_hash,
                      "freezeSha256": freeze_hash, "startedAt": now()}
            try:
                # Reject redirects so the credential cannot be forwarded to another origin.
                class NoRedirect(urllib.request.HTTPRedirectHandler):
                    def redirect_request(self, req, fp, code, msg, headers, newurl):
                        return None
                with urllib.request.build_opener(NoRedirect).open(request, timeout=120) as response:
                    raw = json.loads(response.read())
                result.update(status="OK", response=raw)
            except urllib.error.HTTPError as exc:
                result.update(status="HTTP_ERROR", httpStatus=exc.code)
            except (urllib.error.URLError, TimeoutError, OSError, ValueError) as exc:
                result.update(status="TRANSPORT_ERROR", errorType=type(exc).__name__)
            result["elapsedMs"] = round((time.monotonic() - start) * 1000)
            write_new(saved, result)
        failures = failures + 1 if result["status"] != "OK" else 0
        outcomes.append(result)
        print(f"{split} {index+1}/{len(rows)} {name} {result['status']} {result['elapsedMs']}ms", flush=True)
    compact = []
    for result in outcomes:
        raw = result.get("response", {})
        choice = (raw.get("choices") or [{}])[0]
        compact.append({k: v for k, v in result.items() if k != "response"} | {
            "content": choice.get("message", {}).get("content", ""), "finishReason": choice.get("finish_reason"),
            "model": raw.get("model"), "fingerprint": raw.get("system_fingerprint"), "usage": raw.get("usage"),
            "responseId": raw.get("id"), "rawSha256": sha(encoded(raw))})
    verify()
    write_new(output, {"completedAt": now(), "freezeSha256": freeze_hash, "rows": compact})


def normalize(text):
    text = unicodedata.normalize("NFKC", text).casefold()
    text = "".join(c for c in text if not unicodedata.category(c).startswith("P"))
    return " ".join(re.sub(r"\b(a|an|the)\b", " ", text).split())


def quote_norm(text):
    return " ".join(unicodedata.normalize("NFKC", text).split())


def token_f1(predicted, expected):
    a, b = normalize(predicted).split(), normalize(expected).split()
    if not a or not b:
        return float(a == b)
    shared = sum((collections.Counter(a) & collections.Counter(b)).values())
    return 2 * shared / (len(a) + len(b))


def parse(result):
    if result["status"] != "OK" or result.get("finishReason") != "stop":
        return None
    try:
        value = json.loads(result["content"])
        assert isinstance(value, dict) and set(value) == {"answered", "answer", "explanation", "citations", "reason"}
        assert type(value["answered"]) is bool
        assert all(isinstance(value[k], str) for k in ("answer", "explanation", "reason"))
        assert isinstance(value["citations"], list)
        for c in value["citations"]:
            assert isinstance(c, dict) and set(c) == {"evidenceId", "quote"}
            assert all(isinstance(x, str) for x in c.values())
        if value["answered"]:
            assert value["answer"].strip() and value["explanation"].strip() and value["citations"] and not value["reason"].strip()
        else:
            assert not value["answer"].strip() and not value["explanation"].strip() and not value["citations"] and value["reason"].strip()
        return value
    except (ValueError, TypeError, AssertionError):
        return None


def score(q, row, result):
    value = parse(result)
    null = q["type"] == "null_query"
    answered = bool(value and value["answered"])
    refused = bool(value and not value["answered"])
    exact = bool(value and (refused if null else answered and normalize(value["answer"]) == normalize(q["answer"])))
    evidence = {e["id"]: e for e in row["evidence"]}
    citations = value["citations"] if answered else []
    valid = [c["evidenceId"] in evidence and bool(quote_norm(c["quote"]))
             and quote_norm(c["quote"]) in quote_norm(evidence[c["evidenceId"]]["text"]) for c in citations]
    cited_chunks = {chunk for c, ok in zip(citations, valid) if ok for chunk in evidence[c["evidenceId"]]["chunks"]}
    supported_facts = [f["id"] for f in q["facts"] if any(set(span) <= cited_chunks for span in f["alternatives"])]
    reference_proxy = bool(not null and exact and valid and all(valid) and len(supported_facts) == len(q["facts"]))
    flags = []
    if not value:
        flags.append("FORMAT_OR_PROVIDER_FAILURE")
    if answered and not exact:
        flags.append("NONEXACT_ANSWER")
    if answered and (not valid or not all(valid)):
        flags.append("INVALID_CITATION")
    if answered and exact and not null and not reference_proxy:
        flags.append("EXACT_BUT_SUPPORT_PENDING")
    if q["id"] == "m2521":
        flags.append("ANSWER_ENTITY_IN_QUESTION")
    return {"id": q["id"], "arm": row["arm"], "split": q["split"], "type": q["type"], "validSchema": value is not None,
            "answered": answered, "refused": refused, "exact": exact,
            "f1": token_f1(value["answer"], q["answer"]) if answered and not null else 0.0,
            "validCitationCount": sum(valid), "citationCount": len(valid), "referenceSupportedExactProxy": reference_proxy,
            "citedReferenceFacts": supported_facts, "flags": flags, "output": value,
            "elapsedMs": result["elapsedMs"], "usage": result.get("usage"), "semanticReview": "PENDING"}


def summarize(rows):
    answerable = [r for r in rows if r["type"] != "null_query"]
    nulls = [r for r in rows if r["type"] == "null_query"]
    citations = sum(r["citationCount"] for r in rows)
    times = sorted(r["elapsedMs"] for r in rows)
    usage = collections.Counter()
    for r in rows:
        usage.update({k: v for k, v in (r["usage"] or {}).items() if isinstance(v, int)})
    return {"n": len(rows), "answerable": len(answerable), "null": len(nulls),
            "correct": sum(r["exact"] for r in rows), "em": statistics.mean(r["exact"] for r in rows),
            "answerableCorrect": sum(r["exact"] for r in answerable),
            "answerableEM": statistics.mean(r["exact"] for r in answerable) if answerable else None,
            "answerableTokenF1": statistics.mean(r["f1"] for r in answerable) if answerable else None,
            "correctNullRefusals": sum(r["refused"] for r in nulls),
            "answerableRefusals": sum(r["refused"] for r in answerable),
            "referenceSupportedExactProxyCount": sum(r["referenceSupportedExactProxy"] for r in answerable),
            "referenceSupportedExactProxy": statistics.mean(r["referenceSupportedExactProxy"] for r in answerable) if answerable else None,
            "citationQuoteValidity": sum(r["validCitationCount"] for r in rows) / citations if citations else None,
            "schemaFailures": sum(not r["validSchema"] for r in rows), "p50GenerationMs": statistics.median(times),
            "p95GenerationMs": times[max(0, (95 * len(times) + 99) // 100 - 1)], "usage": dict(usage),
            "semanticAnswerAccuracy": None, "humanVerifiedGroundedAccuracy": None}


def analyze():
    verify()
    questions = {q["id"]: q for q in read(SAMPLE)["questions"]}
    by_key = {(r["id"], r["arm"]): r for r in inputs()}
    scores = []
    for split in ("CALIBRATION", "FORMAL"):
        path = OUT / (split.lower() + "-capture.json")
        if not path.exists():
            continue
        capture = read(path)
        assert capture["freezeSha256"] == filehash(HERE / "freeze.json")
        assert len(capture["rows"]) == (40 if split == "CALIBRATION" else 200)
        seen = set()
        for result in capture["rows"]:
            key = result["id"], result["arm"]
            assert key not in seen
            seen.add(key)
            row = by_key[key]
            assert row["split"] == split == result["split"]
            assert result["requestSha256"] == sha(encoded(body(row)))
            scores.append(score(questions[row["id"]], row, result))
    assert scores
    for q in questions.values():
        group = [r for r in scores if r["id"] == q["id"]]
        if len({(r["answered"], normalize(r["output"]["answer"]) if r["output"] else "INVALID") for r in group}) > 1:
            for row in group:
                row["flags"].append("CROSS_ARM_DISAGREEMENT")
    groups = {split: {arm: summarize([r for r in scores if r["split"] == split and r["arm"] == arm]) for arm in ARMS}
              for split in ("CALIBRATION", "FORMAL") if any(r["split"] == split for r in scores)}
    sensitivity = {arm: summarize([r for r in scores if r["split"] == "FORMAL" and r["arm"] == arm and r["id"] != "m2521"])
                   for arm in ARMS} if "FORMAL" in groups else {}
    pairs = {}
    formal = {(r["id"], r["arm"]): r for r in scores if r["split"] == "FORMAL"}
    for before, after in (("VECTOR", "RRF"), ("VECTOR", "VECTOR_QWEN"), ("RRF", "RRF_QWEN"), ("VECTOR_QWEN", "RRF_QWEN")):
        counts = collections.Counter()
        cases = {"gains": [], "losses": []}
        for q in questions.values():
            if (q["id"], before) not in formal:
                continue
            a, b = formal[q["id"], before]["exact"], formal[q["id"], after]["exact"]
            label = "gains" if b and not a else "losses" if a and not b else "ties"
            counts[label] += 1
            if label != "ties":
                cases[label].append(q["id"])
        pairs[before + " -> " + after] = dict(counts) | cases
    return {"groups": groups, "excludingAnswerLeakingQuestion": sensitivity, "pairedEM": pairs,
            "reviewStatus": "PENDING_SEMANTIC_ADJUDICATION", "rows": scores}


def render(report, detailed):
    esc = lambda value: html.escape(str(value), quote=True)
    questions = {q["id"]: q for q in read(SAMPLE)["questions"]}
    by_key = {(r["id"], r["arm"]): r for r in inputs()}
    h = ['<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">',
         '<title>MultiHop-RAG · 最后一轮答案评测</title><style>body{font:16px/1.7 system-ui,"Microsoft YaHei";max-width:1200px;margin:32px auto;padding:0 20px;background:#f3f6f8;color:#193247}table{border-collapse:collapse;width:100%}td,th{padding:9px;border-bottom:1px solid #ddd;text-align:left}details,.card{background:white;padding:18px;margin:18px 0;border:1px solid #d6e0e7;border-radius:10px}summary{cursor:pointer;font-weight:650}pre{white-space:pre-wrap;overflow-wrap:anywhere;font:inherit;background:#f6f8fa;padding:12px}.note{background:#fff0cc;padding:16px}select,input,button{padding:9px;margin:5px}a{color:#006894}.scroll{overflow:auto}</style>',
         '<h1>MultiHop-RAG · 四组答案对照</h1><p>60题 · 缓存上下文 · DeepSeek Flash · 未重新检索或精排</p>',
         '<p class="note">当前是自动评分，语义正确性和引用蕴含尚待复核。严格匹配不等于完整语义准确率；引用原文存在不等于论证成立。“参考支持代理”还要求引用窗口覆盖全部官方事实，可能过严，也不替代人工核验。不要将代理指标写成最终忠实性。</p>']
    if not detailed:
        h.append('<p><a href="../../../../target/multihop-answer/review.html">打开本地回答与证据复核页</a></p>')
    for split, groups in report["groups"].items():
        h.append('<section class="card"><h2>' + esc(split) + '</h2><div class="scroll"><table><tr><th>组别</th><th>全题严格正确（含拒答）</th><th>有答案 EM</th><th>参考支持代理</th><th>无答案正确拒答</th><th>有答案却拒答</th><th>生成 p50 / p95</th></tr>')
        for arm, s in groups.items():
            h.append(f'<tr><td>{arm}</td><td>{s["correct"]}/{s["n"]} ({s["em"]:.1%})</td><td>{s["answerableCorrect"]}/{s["answerable"]} ({s["answerableEM"]:.1%})</td><td>{s["referenceSupportedExactProxyCount"]}/{s["answerable"]}</td><td>{s["correctNullRefusals"]}/{s["null"]}</td><td>{s["answerableRefusals"]}</td><td>{s["p50GenerationMs"]:.0f} / {s["p95GenerationMs"]} ms</td></tr>')
        h.append('</table></div><p>生成延迟不含检索和精排，不是完整在线请求耗时。正式集44题有答案＋6题无答案；校准集9＋1，不合并。</p></section>')
    h.append('<section class="card"><h2>正式集配对变化（严格匹配）</h2>')
    for pair, stats in report["pairedEM"].items():
        h.append('<p>' + esc(pair) + '：改善 ' + esc(stats["gains"]) + '；退步 ' + esc(stats["losses"]) + '；持平 ' + esc(stats.get("ties", 0)) + '</p>')
    h.append('</section><h2>回答复核</h2><p>优先看有争议的题目。每题四组并排顺序一致，点开可看全部答案；正确拒答、失败和退步均保留。标记只保存在当前浏览器，可导出复核记录，不会自动改动指标。</p><input id="search" placeholder="搜索题号或问题"><select id="filter"><option value="flagged">待复核题目</option><option value="all">全部题目</option><option value="FORMAL">正式集</option><option value="CALIBRATION">校准集</option></select><button onclick="download()">导出复核记录</button>')
    for qid, q in questions.items():
        group = sorted([r for r in report["rows"] if r["id"] == qid], key=lambda r: ARMS.index(r["arm"]))
        if not group:
            continue
        flags = sorted({flag for r in group for flag in r["flags"]})
        h.append(f'<details class="question" id="{qid}" data-split="{q["split"]}" data-flagged="{str(bool(flags)).lower()}"><summary>{qid} · {esc(q["query"])}</summary><p>官方答案：<b>{esc(q["answer"])}</b> · {q["split"]}</p><p>复核原因：{esc(flags)}</p>')
        for r in group:
            key = qid + '-' + r["arm"]
            h.append('<h3>' + r["arm"] + '</h3><p>严格匹配：' + str(r["exact"]) + '；参考支持代理：' + str(r["referenceSupportedExactProxy"]) + '</p><pre>' + esc(json.dumps(r["output"], ensure_ascii=False, indent=2)) + '</pre>')
            h.append(f'<label>语义答案<select data-key="{key}/answer"><option value="">待确认</option><option>正确</option><option>错误</option><option>标注存疑</option></select></label><label>回答有充分引用支持<select data-key="{key}/grounded"><option value="">待确认</option><option>是</option><option>否</option><option>不适用（拒答）</option></select></label><input data-key="{key}/note" placeholder="备注">')
            if detailed:
                h.append('<details><summary>本组实际输入的全部证据</summary>')
                for e in by_key[qid, r["arm"]]["evidence"]:
                    h.append('<pre>' + esc(e["text"]) + '</pre>')
                h.append('</details>')
        if detailed:
            h.append('<details><summary>官方参考事实（未发送给生成模型）</summary>')
            for f in q["facts"]:
                h.append('<p>' + esc(f["id"]) + '</p><blockquote>' + esc(f["quote"]) + '</blockquote>')
            h.append('</details>')
        h.append('</details>')
    h.append('''<script>
const prefix='multihop-answer-v1/';let records={};try{records=JSON.parse(localStorage.getItem(prefix)||'{}')}catch(e){}
document.querySelectorAll('[data-key]').forEach(e=>{e.value=records[e.dataset.key]||'';e.addEventListener('change',()=>{records[e.dataset.key]=e.value;try{localStorage.setItem(prefix,JSON.stringify(records))}catch(e){alert('无法保存，请导出记录')}})});
function download(){let a=document.createElement('a');a.href=URL.createObjectURL(new Blob([JSON.stringify({version:1,status:'USER_REVIEW_EXPORT',records},null,2)],{type:'application/json'}));a.download='multihop-answer-review.json';a.click();URL.revokeObjectURL(a.href)}
const search=document.getElementById('search'),filter=document.getElementById('filter');function apply(){document.querySelectorAll('.question').forEach(e=>{e.hidden=!(e.textContent.toLowerCase().includes(search.value.toLowerCase())&&(filter.value==='all'||filter.value==='flagged'&&e.dataset.flagged==='true'||e.dataset.split===filter.value))})}search.addEventListener('input',apply);filter.addEventListener('change',apply);apply();function anchor(){const e=document.getElementById(location.hash.slice(1));if(e&&e.classList.contains('question')){e.hidden=false;e.open=true;e.scrollIntoView()}}addEventListener('hashchange',anchor);anchor();</script></html>''')
    return '\n'.join(h)


def report():
    result = analyze()
    phase = "formal" if "FORMAL" in result["groups"] else "calibration"
    write_new(OUT / (phase + "-metrics.json"), result)
    queue = [{"id": r["id"], "arm": r["arm"], "split": r["split"], "flags": r["flags"],
              "semanticCorrect": None, "grounded": None, "note": ""} for r in result["rows"] if r["flags"]]
    write_new(OUT / (phase + "-review-queue.json"), queue)
    name = "report.html" if phase == "formal" else "calibration.html"
    with (OUT / name).open("x", encoding="utf-8", newline="\n") as stream:
        stream.write(render(result, False))
    with (LOCAL / ("review.html" if phase == "formal" else "calibration-review.html")).open("x", encoding="utf-8", newline="\n") as stream:
        stream.write(render(result, True))
    if phase == "formal":
        hashes = {p.relative_to(ROOT).as_posix(): filehash(p) for p in sorted(CACHE.glob("*.json"))}
        write_new(OUT / "request-ledger-hashes.json", hashes)
    print(json.dumps(result["groups"], ensure_ascii=False, indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("command", choices=("freeze", "collect", "report", "verify"))
    parser.add_argument("--split", choices=("CALIBRATION", "FORMAL"))
    parser.add_argument("--confirm-paid", action="store_true")
    args = parser.parse_args()
    if args.command == "freeze":
        write_new(HERE / "freeze.json", manifest())
    elif args.command == "collect":
        assert args.split
        collect(args.split, args.confirm_paid)
    elif args.command == "verify":
        verify()
    else:
        report()
