# Harborview: offline model × filename × RRF cross comparison

Open the [Chinese report](report.html) for all eight arms, paired effects,
category/language slices, positive evidence ranks and actual top-five passages.
All original results and human-approved annotations are preserved.

## Scope and controls

This is an offline replay, not eight new service runs. It adds the two missing
body-only keyword fusions and reproduces six existing arms from frozen artifacts:

- [Filename capture](../harborview-filename-2026-10-10/capture.json): body-only and
  body-plus-filename keyword rankings. The latter reproduces the original keyword
  order **and scores on all 24 queries**.
- [Embedding capture](../harborview-bgem3-2026-10-10/capture.json): current
  `qllama/bge-small-zh-v1.5:latest` and pinned `bge-m3:567m` rankings. Existing
  vector files independently reproduce exact cosine order; both embed body only.
- Same 66 documents, 105 chunks, 24 questions and reviewed chunk grades.
  Top-five averages cover 20 answerable questions; four unanswerable questions
  are excluded, not counted as correct refusals.
- Equal-weight RRF uses constant 60, up to 50 hits per channel, a 30-hit fused
  cap and numeric database chunk-ID tie-breaking. No weight, cutoff or question changes.
- **Zero new model calls, retrieval calls or cloud calls.** No Spring application,
  database or search service is started. No production/default configuration changes.

The captures were collected at different times, but fixture/corpus/approval hashes
and baseline replay checks establish their shared inputs. `comparison.json` records
source hashes. Existing source comparisons must also reproduce exactly before the
new comparison runs. This is not a latency or fresh end-to-end benchmark.

## Results

| Arm | Hit@5 | Recall@5 | MRR@5 | nDCG@5 | Recall@30 | All complementary facts@5 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Keyword: body | .7500 | .7500 | .7250 | .7315 | .9000 | 4/6 |
| Keyword: body + filename | .9000 | .9000 | .8167 | .8403 | 1.0000 | 5/6 |
| Current vector | .8000 | .7750 | .7500 | .7397 | .8500 | 3/6 |
| Current + body keyword RRF | .8000 | .8000 | .7417 | .7525 | .8750 | 4/6 |
| Current + filename keyword RRF | .8500 | .8500 | .7767 | .7863 | .9750 | 4/6 |
| BGE-M3 vector | 1.0000 | 1.0000 | .9600 | .9613 | 1.0000 | 6/6 |
| BGE-M3 + body keyword RRF | .9500 | .9250 | .8183 | .8372 | 1.0000 | 4/6 |
| BGE-M3 + filename keyword RRF | 1.0000 | .9750 | .9167 | .9254 | 1.0000 | 5/6 |

RRF Recall@50 is inapplicable because fusion retains at most 30 candidates.
These measure raw chunks before neighbor expansion or reranking, not generated
answer accuracy or final context completeness.

## Separating the effects

Positive deltas favor the arm after the change. Recall deltas are percentage
points; nDCG deltas are absolute units. Win/loss/tie counts use per-query nDCG.

| Controlled change | ΔRecall@5 (pp) | ΔnDCG@5 | Win / loss / tie |
| --- | ---: | ---: | ---: |
| Add filename to keyword alone | +15.0 | +.1087 | 4 / 0 / 16 |
| Add body keyword RRF to current vector | +2.5 | +.0128 | 1 / 1 / 18 |
| Add filename keyword RRF to current vector | +7.5 | +.0466 | 3 / 1 / 16 |
| Add filename within current-model RRF | +5.0 | +.0338 | 2 / 1 / 17 |
| Add body keyword RRF to BGE-M3 vector | -7.5 | -.1241 | 1 / 4 / 15 |
| Add filename keyword RRF to BGE-M3 vector | -2.5 | -.0359 | 1 / 2 / 17 |
| Add filename within BGE-M3 RRF | +5.0 | +.0882 | 3 / 0 / 17 |

Filename support helps overall in all three matched comparisons. In particular,
it **mitigates**, rather than explains, most of BGE-M3's fusion degradation.
However, filename support is not harmless on every query: current-model RRF
regresses on e19 even though keyword-only nDCG is unchanged.

Here, “RRF effect” means adding a specific keyword channel with the existing
equal-weight rank-fusion policy relative to vector alone. It does not isolate
the arithmetic from the channel quality, nor compare all possible fusion methods.
The same policy helps the weaker current vector overall but hurts BGE-M3 overall.
RRF rewards agreement between channels and discards original score magnitudes;
agreement can promote irrelevant passages when one channel is weak for the query.

## Concrete cases, including counterexamples

- **e15, English question over Chinese remote-work policies:** BGE-M3 ranks the
  exception/weekly evidence 1/3. Body keyword misses both, and body RRF pushes
  them to 13/14. Filename keyword ranks them 3/5; its RRF restores them to 2/4.
  Recall recovers, although ordering is still worse than vector alone.
- **e13/e14, cross-language backup queries:** BGE-M3 ranks the correct passage
  first. Body RRF moves it to 3/5; filename RRF restores rank 1 for both.
  These are further examples of filename support reducing fusion harm.
- **e16, Bristol expense evidence:** the deadline/receipt passages rank 1/2 in
  BGE-M3, versus 25/19 in either keyword input. Both RRF variants rank them 6/3.
  Deadline evidence leaves the top five; removing filename cannot fix this case.
  BGE-M3 fusion Recall@30 remains 100%: the top-five loss here is ordering, not
  disappearance from the fused candidate pool.
- **e12, urgent notification:** the relevant boundary chunk is fifth in BGE-M3,
  first in either keyword input and second in either RRF variant. Keyword
  retrieval still contributes useful complementary information without filenames.
- **e19, long restore guide:** current vector ranks approval/retention 7/1;
  body RRF improves them to 2/1. Filename RRF yields 3/1 instead. Both keyword
  inputs rank these positives 1/2, but irrelevant `restore-guide-en--c015` moves
  from keyword rank 5 to 4 while retaining vector rank 3. Its RRF score rises
  above the approval passage: a concrete loss caused by promoting a distractor.
  Recall stays 100%, while nDCG drops from 1.0000 to .9197.

## Limits and interpretation

This remains a small synthetic diagnostic corpus, repeatedly inspected during
development, not an independent holdout or evidence of statistical significance.
Filename availability is a real input difference, not automatically label leakage;
the result is conditional on these meaningful filenames. Keyword means the
project's boosted/rescored lexical chain, not an abstract bare-BM25 baseline.

The evidence supports retaining the distinction between candidate recall and
top-five ranking, and evaluating each model/channel pairing rather than claiming
that hybrid retrieval always wins. It does not justify deleting a channel or
changing defaults universally. No model selection, fusion tuning, reranking or
answer generation is performed in this replay. A later tuning experiment should
separate development and held-out questions before choosing weights or policies.

## Reproduction (Java 21, entirely offline)

```powershell
mvn "-Dtest=RetrievalCrossReportRun" test
mvn "-Dtest=RecordedRetrievalCrossTest,RecordedEmbeddingComparisonTest,RecordedFilenameAblationTest,RecordedExtensionTest" test
```

The generator writes only this new result directory. Regression tests compare
the saved JSON and HTML, verify all 96 fused rankings against independent rank
arithmetic, check paired-effect populations and retain the observed regressions.
