# MultiHop-RAG: final four-arm cached-context answer evaluation

[Start with five review questions](review-guide.html) · [All automatic metrics](report.html)
· [Full local answers/evidence](../../../../target/multihop-answer/review.html)
· [Frozen protocol](../../multihop/README.md)

## Result, with a narrow claim

All240 generations completed successfully using the configured `deepseek-flash`:
40 calibration responses, then200 formal responses after the calibration decision
was committed. No retries, HTTP failures, schema failures or truncated outputs.
No retrieval, embedding, reranking or paid judge calls were made in this round.
All240 existing context hashes match the earlier recorded contexts.

This is a controlled answer-generation experiment over cached production-selected
evidence. The short-answer/citation prompt and schema are evaluation-only, not the
unchanged application prompt. Do not call these live API/SLA measurements or claim
the production user interface has a52% human-verified accuracy rate.

### Formal50: original frozen exact-match scoring

| Arm | All50, including correct null refusals | Answerable44 EM | Correct null refusals | Answerable refusals | Reference-supported EM proxy |
|---|---:|---:|---:|---:|---:|
| BGE-M3 vector |23/50 =46%|17/44 =38.64%|6/6|23|2/44 =4.55%|
| RRF hybrid |25/50 =50%|19/44 =43.18%|6/6|22|4/44 =9.09%|
| Vector + Qwen |26/50 =52%|20/44 =45.45%|6/6|20|4/44 =9.09%|
| RRF + Qwen |26/50 =52%|20/44 =45.45%|6/6|20|3/44 =6.82%|

On the same formal questions, hybrid without reranking improves full-set strict
score by4 percentage points over vector alone. Vector reranking adds6 points;
hybrid reranking adds2 points. The complete hybrid+rerank arm is6 points above
vector alone, but **ties vector+rerank**, not a unique winner. These are small
observed differences, not significance/generalization claims. On answerable-only
questions, the respective scores are38.64%,43.18%,45.45%,45.45%; final vs vector
gain is6.82 percentage points. Never combine those denominators.

Paired strict-match changes (all50, ties include both-right and both-wrong):

| Comparison | Improved | Worsened | Tied |
|---|---:|---:|---:|
| Vector → RRF |7|5|38|
| Vector → Vector+Qwen |4|1|45|
| RRF → RRF+Qwen |3|2|45|
| Vector+Qwen → RRF+Qwen |3|3|44|

Calibration full-set scores were5/10,5/10,6/10,6/10, excluded from the formal
headline. Prompt and scoring were retained after inspecting all40 calibration
outputs. No outcome-driven formal aliases, label fixes or reruns were introduced.
Excluding pre-flagged answer-leaking question m2521 yields22/49,24/49,25/49,25/49
(44.90%,48.98%,51.02%,51.02%); ordering does not change.

## What the low support proxy means

The proxy requires all of: exact answer, every citation quote matching its cited
window, and those cited windows containing every complete official fact span.
It is a deliberately strict diagnostic, **not semantic answer accuracy or
faithfulness**. Missing any official span, citing alternative sufficient text,
changing a curly quote, or a non-verbatim quotation can fail it. Conversely, a
window containing a fact does not prove the model's quoted sentence supports the
claim. This is why human-verified semantic/grounded accuracy remains null.

Per-citation verbatim validity is94.34%,86.67%,87.69%,88.89% across the four formal
arms; this is quote-location integrity only, not citation entailment precision.
High entity EM must not conceal the model's willingness to answer while explicitly
acknowledging missing conditions. Six null examples are too few for a broad
refusal/reliability claim; report counts rather than advertising100% robustness.

## Concrete improvements, regressions and scoring traps

- **m1273: meaningful evidence-to-answer gain.** Vector alone lacks the later
  point-spread article and abstains. RRF and both reranked arms answer Consistent,
  cite both news/sentiment discussions and pass the reference-supported proxy.
- **m0179: reranking can remove a needed source.** RRF answers Yes when comparing
  two cricket articles. RRF+Qwen loses the earlier source and abstains. The previous
  reranking report already recorded lost reference evidence on this question;
  the loss now affects generation. RRF's quotes still need semantic/integrity review.
- **m2449: EM gain without complete grounding.** Both baseline arms abstain;
  both reranked arms name Sam Bankman-Fried correctly while explicitly acknowledging
  missing Fortune/TechCrunch/judge-intervention evidence. Do not present this as
  fully grounded multi-hop reasoning success.
- **m2488: stronger explanation, not an automatic faithfulness pass.** RRF+Qwen
  covers the required TechCrunch Android claim, Verge market definition and
  TechCrunch publisher claim; the unreranked RRF answer admits a source gap.
  Both say Yes, so EM does not reflect this explanation improvement. Quotation
  integrity still fails for the reranked answer; review the actual evidence.
- **m0196: a strict-score loss can reflect more cautious behavior.** Vector
  answers Yes while explaining that requested sources are absent; reranked arms
  abstain. EM alone favors the unsupported answer.
- **m1987: reasoning and alternative evidence matter.** RRF+Qwen answers Yes;
  other arms abstain. Vector's refusal even names an October31 article but says
  no pre-November5 article is available. Official fact coverage does not measure
  all sufficient alternatives, and the quote check also fails; inspect manually.

### Five first-priority review questions; no labels changed

1. **m1594 — suspected official label error.** Official answer Apple conflicts
   with all three official reference facts, which describe Google. Vector answers
   Google and is scored wrong under the preserved label. User adjudication needed.
2. **m0735 — True vs Yes.** Official True, all four answers Yes. Strict EM fails
   all four; semantic equivalence is likely. Any correction must apply equally.
3. **m1727 — contradictory answer and explanation.** Official no; all arms say
   Yes but explain the first article predates the second. Do not silently score
   the explanation instead of the answer field.
4. **m1748 — negated question.** Gold no to “was there no change”; three arms say
   “Yes, there was a change.” The proposition may agree but its polarity marker
   conflicts. Human judgment is preferable to extracting the first yes/no token.
5. **m2398 — ambiguous consistency.** Gold Yes; three arms interpret a change
   from global luxury resorts to Canadian resorts as No. The meaning of consistency
   is underspecified. Flag the item before changing its answer or dropping it.

These findings were discovered after the frozen formal run. The reported scores
are not corrected. A later adjudicated view must preserve this raw view, record
every change, use the same rubric across arms, and never claim those edits were
pre-registered. The public model-assisted dataset is not error-free; the user's
earlier partial spot-check did not imply exhaustive adjudication.

## Review workload and how to respond

Start at [review-guide.html](review-guide.html), which prioritizes the five items
above, then provides balanced positive/negative cases. The full local page shows
question, gold answer, four responses, explanations, actual input windows, and
official reference facts (never sent to the generator).

Automatic flags cover29 formal questions /116 response rows, plus5 calibration
questions /20 rows. Flags are a triage list, not proof all flagged answers are
incorrect; cross-arm disagreements flag all four responses. All240 responses,
including unflagged shared failures, remain inspectable. Reviewing just the first
five resolves major scoring/label issues but is **not** a complete faithfulness
audit. For a fully human-adjudicated accuracy claim, the corresponding responses
must actually be reviewed, not inferred from a spot-check.

Use each response's semantic-answer and grounding selectors, add a note, then
click “导出复核记录”. Send the JSON back for an explicit adjudicated report.
Selections remain in browser storage and do not alter committed metrics. On a
null question, an appropriate refusal is correct; on an answerable question it
is a benchmark miss even when abstaining is appropriate for the supplied context.
Judge references as evidence, not merely matching names. Do not mark an answer
grounded just because the quoted substring exists.

## Usage, latency and verification

Provider-reported aggregate usage over240 calls:

- Input644,118 tokens:121,984 cache-hit and522,134 cache-miss.
- Output29,347 tokens; total673,465. This is actual returned usage, not a currency
  invoice; cost must be checked against the provider's billing records.
- Formal generation-only p50/p95 ms: vector1308.5/1702; RRF1331/1805;
  vector+Qwen1278/1706; RRF+Qwen1338.5/1738. Cached retrieval/rerank time excluded.
- No model seed or pinned weights; temperature0 does not eliminate remote drift.

`verification.json` confirms240 intent/outcome pairs, all formal calls after
calibration approval, unchanged source hashes, raw-response hashes and exact
offline regeneration of metrics and both HTML views. Actual contexts/raw provider
responses stay under ignored `target/multihop-answer`; Git contains request
manifests, compact answers/usage, scores, reports and review priorities.

Validation: Java21 offline context export passed;8 existing recorded retrieval/
rerank regression tests passed;16 Python scorer/recorded-artifact tests passed.
HTML structure, unique anchors and full60-question coverage were checked offline;
no visual browser-render verification is claimed. The independent results verifier
makes no provider calls. Production code, defaults, indexes and all prior experiment
results remain unchanged.

Public dataset attribution/provenance and ODC-BY source information remain in
[the original MultiHop freeze](../../../retrieval/multihop/README.md). Preserve that
attribution when sharing derived results. Do not equate these public English,
model-assisted questions with real user traffic or claim unseen training data.
