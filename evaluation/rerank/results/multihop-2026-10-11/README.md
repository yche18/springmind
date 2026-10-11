# MultiHop-RAG: Qwen fixed-pool reranking results

[Report](report.html) · [Detailed local review](../../../../target/multihop-rerank-review/review.html)
· [Frozen protocol](../../multihop/README.md) · [Runbook](../../multihop/RUNBOOK.md)

## Outcome

Qwen improves mean reference-fact coverage in all three formal-set arms, but does
not uniformly improve complete multi-evidence coverage. Dense retrieval benefits
most in this run. Do not advertise reranking or hybrid retrieval as universally
better, and do not call these numbers answer accuracy.

All 60 frozen questions and prior ranks were reused. The 180 provider attempts
all returned APPLIED, with no retries or fallbacks. No retrieval, embedding,
query planning or answer generation was run. The configured model was
`qwen3-rerank` at the endpoint recorded in the protocol; its alias does not pin
remote model weights. Evaluation timeout was10s, not the application's default2s.
Collection took about6 minutes; actual billed tokens/cost are not exposed by the
production client. The local estimate was1,395,619 input tokens, NOT billed usage.

## Formal set: 44 answerable questions, 6 null questions excluded from recall

Final contexts use the same at-most5 windows and4096-token limit before/after.
Fact recall is macro-averaged and uses complete official reference spans.

| Retrieval | Fact recall before | After Qwen | Change | All facts before → after | Improved / tied / worsened |
|---|---:|---:|---:|---:|---:|
| Keyword | 43.18% | 48.48% | +5.30 pp | 10/44 → 9/44 | 9 / 33 / 2 |
| BGE-M3 | 39.58% | 54.73% | +15.15 pp | 6/44 → 10/44 | 16 / 27 / 1 |
| RRF | 47.54% | 51.52% | +3.98 pp | 11/44 → 10/44 | 10 / 29 / 5 |

The strongest unreranked arm for fact recall was RRF (47.54%); the strongest
reranked arm was BGE-M3 (54.73%). RRF+Qwen is not the best arm on that metric here.
For RRF, two questions became complete but three previously complete questions
lost a fact, explaining the net change from11 to10 despite better average coverage.

RRF's final-context fact recall remains below the63.45% coverage available in its
assembled candidate windows. That ceiling includes neighbor expansion and is not
raw-chunk Recall@30. Reranking cannot recover material missing from those windows.

Formal request latency p50/p95: keyword1,492/3,932ms, vector1,775/3,203ms,
RRF1,654/2,920ms. All requests passed under10s, but these outcomes must not be
claimed as the results under a2s production deadline. Mean selected token counts
decreased from2,662→2,415, 2,377→2,112 and2,390→2,166 respectively.

## Calibration remains separate

Nine answerable questions plus one null question:

- Keyword recall49.07%→56.48%; complete0→2.
- Vector recall47.22%→62.04%; complete2→3.
- RRF recall62.04%→63.89%; complete2→3.

These were not merged into the formal score or used to retune settings mid-run.

## Concrete cases (RRF arm)

### m2488 — useful reranking: 1/3 → 3/3

The question compares Google's Android app-market conduct, antitrust market
definition, and publisher-revenue harm. The window supporting f1 moves from
rank8→4 (E8); f2 from6→2 (E6); f3 stays first (E1). All three fit into the final
1,703-token context. This directly demonstrates retrieval already had the missing
evidence and reranking brought it into the selected windows.

### m0310 — useful but bounded: 1/4 → 3/4

For a multi-source Sam Bankman-Fried question, supporting window E17 moves17→5
and E6 moves6→2. The final context gains f2 and f4. Fact f3 is absent from the
entire reranker candidate window pool, so reranking cannot yield4/4 under this pool.

### m1682 — a clear ranking regression: 3/4 → 0/4

All four official Google-related facts were available to RRF's candidate pool.
Qwen moves the f1 window E4 from4→8, the f2/f3 window E5 from5→6, and the f4
window E6 from6→7. All fall outside the first five selected windows. These three
windows were NOT preview-clipped. The final context uses only1,840 tokens, so
this example cannot be explained by exhausting4096 tokens. It exposes a mismatch
between individual-window relevance ranking and complete multi-fact coverage.
This diagnoses loss of official evidence, not that the generated answer is wrong:
no answer was generated, and semantic alternative evidence remains possible.

### m0179 — a previous fusion win loses completeness: 3/3 → 2/3

The cricket comparison previously found all three official facts after RRF.
The window with f3 (E5) drops5→9 under Qwen. The final context is3,064 tokens,
still below budget, and now retains only f1/f2. A successful retrieval-fusion case
does not imply that adding reranking will preserve every complementary source.

### Earlier four mutual-complement cases: not selectively omitted

| Question | RRF before → after | Fact coverage available to reranker |
|---|---:|---:|
| m0335 (Chelsea) | 2/3 → 2/3 | 2/3 |
| m1682 (Google) | 3/4 → 0/4 | 4/4 |
| m1797 (FTX funds) | 0/2 → 1/2 | 1/2 |
| m0640 (sportsbooks) | 1/3 → 1/3 | 2/3 |

These four were complete in the earlier unordered top50+top50 union. That union
is not the same as the capped, clustered windows sent to the reranker. For m0335,
the Reece James fitness-check fact never reaches this RRF pool. For m1797, E23
is promoted23→1 and recovers f2, but f1 is absent from the rerank pool.

## Limitations and next decision

- Official labels with a user spot-check, not exhaustive adjudication. Complete
  official-fact coverage can be stricter than the minimum sufficient answer evidence.
- Fifty formal questions include only44 answerable examples; no significance or
  broad production-quality claim. Public questions can be present in model training.
- Reranking happens after production clustering/neighbor expansion. The candidate
  pool, five-window limit and evidence diversity matter independently of the model.
- Fifty-six candidate previews were clipped by the existing4096-code-point
  production behavior. This is preserved and recorded, not silently removed.
- No generated answer accuracy, faithfulness, refusal rate or judge score exists
  in this run. Null-question model relevance scores are not refusal decisions.

Retain these outcomes as measured. A later generation evaluation should compare
the fixed arms rather than assume hybrid+rerank is best. If investigating evidence
selection, use a separately declared calibration experiment and a new confirmation
sample after tuning; do not silently optimize against this exposed formal set.

## Verification

All180 reranked contexts independently reproduced offline (selected IDs, token
counts and context-text hashes). `verification.json` binds capture, scores, reports
and implementations. Provider intents/outcomes were checkpointed individually;
`request-ledger-hashes.json` records their provenance. Existing retrieval artifacts
remain byte-identical and application defaults/indexes are unchanged.
