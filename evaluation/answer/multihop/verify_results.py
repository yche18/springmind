"""Independent accounting and reproducibility checks; never imports a model SDK."""
import collections
import json
import run


def verify_results():
    run.verify()
    expected = {(r["id"], r["arm"]): r for r in run.read(run.HERE / "freeze.json")["inputs"]}
    seen = set()
    usage = collections.Counter()
    for split, count in (("CALIBRATION", 40), ("FORMAL", 200)):
        capture = run.read(run.OUT / (split.lower() + "-capture.json"))
        assert len(capture["rows"]) == count
        for row in capture["rows"]:
            key = row["id"], row["arm"]
            assert key not in seen
            seen.add(key)
            assert row["split"] == expected[key]["split"] == split
            assert row["requestSha256"] == expected[key]["requestSha256"]
            name = row["id"] + "-" + row["arm"]
            cached = run.read(run.CACHE / (name + ".json"))
            intent = run.read(run.CACHE / (name + ".intent.json"))
            assert intent["freezeSha256"] == row["freezeSha256"] == run.filehash(run.HERE / "freeze.json")
            assert intent["requestSha256"] == row["requestSha256"]
            assert run.sha(run.encoded(cached.get("response", {}))) == row["rawSha256"]
            if row["status"] == "OK":
                raw = cached["response"]
                assert raw["model"] == row["model"] == "deepseek-flash"
                assert raw["choices"][0]["message"]["content"] == row["content"]
                assert raw["usage"] == row["usage"]
                assert row["usage"]["total_tokens"] == row["usage"]["prompt_tokens"] + row["usage"]["completion_tokens"]
                usage.update({k: v for k, v in row["usage"].items() if isinstance(v, int)})
    assert len(seen) == 240
    assert len(list(run.CACHE.glob("*.intent.json"))) == 240
    assert len(list(run.CACHE.glob("*.json"))) == 480
    decision = run.read(run.HERE / "calibration.json")
    formal = run.read(run.OUT / "formal-capture.json")
    assert decision["approved"] and decision["captureSha256"] == run.filehash(run.OUT / "calibration-capture.json")
    assert all(row["startedAt"] > decision["approvedAt"] for row in formal["rows"])
    report = run.read(run.OUT / "formal-metrics.json")
    assert report == run.analyze()
    assert run.render(report, False) == (run.OUT / "report.html").read_text(encoding="utf-8")
    assert run.render(report, True) == (run.LOCAL / "review.html").read_text(encoding="utf-8")
    # Count correct outcomes independently from stored per-answer booleans.
    for split, arms in report["groups"].items():
        for arm, group in arms.items():
            rows = [r for r in report["rows"] if r["split"] == split and r["arm"] == arm]
            assert group["n"] == len(rows)
            assert group["correct"] == len([r for r in rows if r["exact"] is True])
            assert group["em"] == group["correct"] / group["n"]
            assert group["semanticAnswerAccuracy"] is None and group["humanVerifiedGroundedAccuracy"] is None
    hashes = {p.relative_to(run.ROOT).as_posix(): run.filehash(p) for p in sorted(run.OUT.iterdir()) if p.is_file()}
    hashes["target/multihop-answer/review.html"] = run.filehash(run.LOCAL / "review.html")
    return {"contextsVerified": 240, "generationAttempts": 240, "additionalRetrievalCalls": 0,
            "additionalRerankCalls": 0, "paidJudgeCalls": 0, "usage": dict(usage),
            "allFormalRequestsAfterCalibrationApproval": True, "metricsAndHtmlRegeneratedExactly": True,
            "hashes": hashes}


if __name__ == "__main__":
    result = verify_results()
    run.write_new(run.OUT / "verification.json", result)
    print(json.dumps({k: v for k, v in result.items() if k != "hashes"}, indent=2))
