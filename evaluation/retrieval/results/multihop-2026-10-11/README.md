# MultiHop-RAG: local retrieval diagnostic

[Interactive report](report.html) · [Local detailed review](../../../../target/multihop-review/review.html)
· [Frozen protocol](../../multihop/README.md) · [Reproduction](../../multihop/RUNBOOK.md)

## Outcome

The channels are complementary on this fixed sample, but candidate complementarity
is only partially retained in final contexts. This is evidence retrieval, NOT
answer accuracy. Official annotations remain `OFFICIAL_REFERENCE_PENDING_LOCAL_REVIEW`.

The full 609-article corpus produces 26,110 production chunks. The formal set has
50 questions (44 answerable, 6 null); calibration has 10 (9 answerable, 1 null).
All selected official facts mapped to one or more complete chunk spans. No question
was selected using retrieval scores. No cloud inference, query planner, generation
or reranker was invoked. The isolated local GPU collection took about 7.2 minutes.

## Formal results

Fact recall is macro-averaged over the 44 answerable questions. Complete coverage
requires ALL official reference facts; this is stricter than finding a relevant
article and may be stricter than the minimum evidence sufficient to answer.

| Stage | Keyword | BGE-M3 | RRF / union |
|---|---:|---:|---:|
| Fact recall @30 | 44.89% | 49.62% | RRF 54.36% |
| All facts @30 | 8/44 | 8/44 | RRF 13/44 |
| Fact recall @50 | 55.68% | 60.61% | RRF 61.74% |
| All facts @50 | 14/44 | 13/44 | RRF 16/44 |
| Final-context fact recall | 43.18% | 39.58% | RRF 47.54% |
| Final-context all facts | 10/44 | 6/44 | RRF 11/44 |

At top50, keyword has exclusive facts on 11 formal questions; vector has exclusive
facts on 17. Four questions have facts exclusive to BOTH channels and become complete
only in the union: **m0335, m1682, m1797, m0640**.

- U50 (each channel's top50, <=100 unique candidates): 71.21% fact recall,
  23/44 complete, average 82.8 chunks. Its larger capacity is a confound.
- U25 (each top25, <=50): 59.47% fact recall, 14/44 complete, average 42.46 chunks.
  It does not outperform the vector top50 on mean fact recall. There is no claim
  that simply combining two channels is always superior at equal maximum capacity.
- RRF final context improves mean fact recall by 4.36 percentage points over
  keyword and 7.95 over vector. But only one additional question is fully covered
  versus the stronger keyword-context baseline (11 versus 10).
- On individual questions, RRF final-context recall exceeds BOTH single channels
  on 3 questions and is below the better single channel on 5. Preserve both sides.
- Calibration final-context recall is keyword 49.07%, vector 47.22%, RRF 62.04%;
  complete counts 0/9, 2/9, 2/9. These nine answerable questions are not merged into
  the formal score and are far too few for a general performance claim.

## Concrete cases

### m0335 — complementary pool, incomplete final context

The question combines Chelsea's US expansion, a home defeat and Reece James's
fitness check. Keyword top50 uniquely covers the US expansion fact (chunks
5208+5209); vector uniquely covers the fitness check (5261). Union covers all three
facts, but the final RRF context lacks 5261 and covers only two. A larger candidate
pool does not guarantee the final evidence windows preserve its value.

### m1682 — partial complementarity survives

The Google question combines search monetization, default-search deals and harm
to publishers. Keyword uniquely finds the default-deal facts f2/f3; vector uniquely
finds publishers' revenue harm f4. Single-channel contexts each cover 2/4 facts;
RRF context covers 3/4. The missing f4 span 1143+1144 was available in the union.
This is a genuine partial gain, not full evidence recovery.

### m0179 — final-context complete-coverage gain

The cricket reporting comparison needs three official facts across two articles.
Keyword context retains f3; vector retains f1/f2; RRF context retains all three:
chunks 11852, 11855 and 6336. Final-context coverage is 1/3, 2/3 and 3/3 respectively.
This does NOT prove the official answer "Yes" is correct or that every reference
fact is necessary; the review still needs to verify their connection to the question.

### m1797 / m2521 — do not hide failures or annotation caveats

For m1797 (FTX customer-money access), each channel has a different official fact
within top50; union covers both, yet all three final contexts miss both exact facts.
The RRF context uses only 1,883/4,096 tokens but has reached its five-window limit.
This illustrates ranking/window allocation loss, not necessarily exhausted token
budget. Also, the answer is "no": one counterexample may logically suffice, so
ALL-reference coverage must not be described as answer correctness.

For m2521 (Meta), both single-channel contexts cover one fact; RRF covers none,
despite complete RRF@30 coverage. The question explicitly names the answer entity
"Meta". Preserve this official question, flag it for later closed-book/grounded-answer
controls, and do not present answering it correctly as proof of retrieval success.

## Interpretation and next step

The sample now exposes both complementarity and failure modes; there is no reason
to replace it merely to increase hybrid's win rate. Before paid end-to-end runs,
review the flagged cases for reference sufficiency, semantic alternative evidence
and null answers. Freeze any label correction as a separate revision.

The current development chunk budget is 240/320/32 using a conservative character
estimator, not true English tokenization. Very small body chunks, repeated title/
source/date prefixes, title boosts and the five-window cutoff are plausible factors.
They are hypotheses, not isolated causal findings. A later separately frozen
chunk/window ablation can use calibration data; do not tune against these exposed
formal results and continue calling them an untouched test set.

Exact-cosine BGE-M3 is an evaluation override, not a default model change. The keyword
arm uses production IK/phrase/title scoring, not the earlier BEIR English analyzer.
Scores are not directly comparable to the paper's benchmark. Public data may be
in model training; no unseen-data or statistical-significance claim is made.

## Verification and retained artifacts

- Java 21: 86 offline evaluation tests passed; previous experiment artifacts unchanged.
- All 180 production contexts independently replayed offline, including chunk IDs,
  token counts and context-text hashes (`context-verification.json`).
- `capture.json`: local ranks, selected contexts, model identity and cache hashes.
- `comparison.json`: reproducible split/type metrics and per-question facts.
- `artifact-hashes.json`: report/capture/source hashes; complete article text and
  vector binaries remain ignored, not committed.
- Dedicated index `springmind_eval_multihop_20261011` retained write-blocked;
  evaluation GPU container stopped. Application indexes, defaults and services unchanged.
