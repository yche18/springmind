# User-approved adjudication v1 (offline overlay)

[Updated report](report.html) · [Updated local review](../../../../../target/multihop-answer/adjudicated-v1-review.html)
· [Original50-question report](../report.html) · [Decisions](decisions.json)

The user explicitly accepted the five recommendations in the preceding review.
Provenance is **assistant-proposed, user-approved adjudication**, not an assertion
that the user independently read every answer/evidence window. Exactly five formal
questions /20 response rows are decided. Remaining answers retain their original
automatic scores. No new generation, retrieval, reranking, embeddings or paid judge.

## Results and the change in interpretation

| Arm | Original strict score | Adjusted score | Answerable-only adjusted |
|---|---:|---:|---:|
| Vector |23/50 =46%|26/49 =53.06%|20/43 =46.51%|
| RRF |25/50 =50%|26/49 =53.06%|20/43 =46.51%|
| Vector+Qwen |26/50 =52%|28/49 =57.14%|22/43 =51.16%|
| RRF+Qwen |26/50 =52%|28/49 =57.14%|22/43 =51.16%|

All four arms still correctly refuse6/6 null questions. Ten calibration questions
are unchanged and excluded from these scores. The full pipeline's observed
advantage over vector alone shrinks from6 points to **4.08 percentage points**
(net2 additional correct answers out of49), rather than growing when scores rise.
Hybrid alone now ties vector alone, and hybrid+rerank ties vector+rerank. Do not
attribute the entire adjusted improvement to dual-route fusion. No significance,
real-traffic, production-prompt or general reliability claim follows.

For answerable-only questions, the full pipeline rises46.51%→51.16%, a4.65-point
change across43 questions. Do not mix this denominator with49 or the raw50.

Sensitivity checks use the same decisions:

- Retaining ambiguous m2398 at its original score:26/50,26/50,28/50,28/50
  (52%,52%,56%,56%); full vs vector+4 points.
- Also removing answer-leaking m2521:25/48,25/48,27/48,27/48
  (52.08%,52.08%,56.25%,56.25%); full vs vector+4.17 points.

## Exact changes

| Question | Decision | Vector / RRF / Vector+Qwen / RRF+Qwen score changes |
|---|---|---|
| m1594 | Official Apple contradicts three Google facts; overlay reference Google |+1 /0 /0 /0|
| m0735 | Yes and True equivalent in this question |+1 /+1 /+1 /+1|
| m1727 | Bare Yes contradicts dates and explanation; retain failures |0 /0 /0 /0|
| m1748 | Explicit answer proposition “there was a change” accepted; polarity warning retained |+1 /0 /+1 /+1|
| m2398 | Undefined “consistent”; symmetrically exclude, not relabel No |Remove one zero-score question from every denominator|

No changes are inferred for other questions. The original reference labels,
question wording, generated text, exact-match booleans, reference-supported proxy,
quote validity and calibration outputs remain untouched. `metrics.json` retains
those original fields alongside `adjustedCorrect`, `decisionSource`, `excluded`
and `adjudicatedCoreGrounding`; the inherited `semanticReview` field is the raw
run's status, not the new overlay's status.

Core-grounding decisions are kept separate: m1594 Vector is correct entity but
insufficient complete support; m0735's cross-chunk quotes are semantically
supported despite failed continuous substring checks; m1727's evidence supports
No, not its Yes; m1748's core change proposition is supported with wording and
quote-integrity caveats; m2398 is unresolved. These are not a complete audit of
every explanatory sentence. Overall human-verified accuracy and faithfulness
remain null. No proxy is relabeled as human-verified grounding.

## Interpretation and evidence boundary

This is an **adjusted benchmark score**, not full manually verified semantic
accuracy, faithful-answer accuracy or live application accuracy. Keep the subset,
partial adjudication, comparator and denominator visible in supporting material.
Do not write “hybrid retrieval raised accuracy to57.14%”, “57.14% grounded accuracy”,
“all60 manually reviewed” or “significant improvement”. Raising absolute scores
by correcting grading is not a new model optimization. Existing BEIR retrieval
and MultiHop evidence-recall claims retain their separate datasets/definitions;
do not combine their percentages with these answer scores.

Supporting artifacts are `metrics.json`, raw `../formal-metrics.json`,
`decisions.json`, and `verification.json`. The approval field records a summary
with personal context omitted; the five decisions and all scores are unchanged.

## Review state and reproducibility

The new report/review versions display the five decisions read-only and show
remaining automatic flags separately:24 formal questions /96 responses and5
calibration questions /20 responses. Unflagged rows also remain unadjudicated.
The separate browser-storage namespace avoids loading stale draft decisions from
the original page. Further exported selections are pending review and cannot
silently override the accepted overlay. Original HTML files are preserved; use
the new links above for the current version.

```powershell
python evaluation/answer/multihop/adjudicate.py
python -m unittest discover -s evaluation/answer/multihop -p 'test_*.py'
```

Generation is exclusive-create and refuses to overwrite this version. Tests
recompute in memory, check all decisions/denominators, unchanged raw fields and
hashes, quote-vs-grounding separation, offline-only operation, exact HTML/metric
regeneration,60 unique question anchors and read-only decided rows. Run the
original Java21 export only if local cached inputs need reconstruction; do not
run either paid collector. `verification.json` binds every preexisting result
file and old local review page plus the new derived artifacts. The JSON decision
file and implementation are separately hashed. Visual rendering is not claimed.
