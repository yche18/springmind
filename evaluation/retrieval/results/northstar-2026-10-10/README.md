# Northstar: keyword / vector / hybrid retrieval ablation

**Result: hybrid RRF improves evidence coverage on this fixture, but does not
uniformly improve ranking.** No new Qwen or chat requests were made. The reviewed
30 questions and v2 grades remain unchanged.

Open [the Chinese comparison report](report.html) for all metrics, language/type
breakdowns, improvements, regressions and top-five evidence for every question.
The [original annotation review](../../../rerank/results/northstar-2026-10-10/review.html)
contains the source passages and approved judgments.

## Headline measurements

Macro averages over 24 answerable questions; six unanswerable cases are retained
but excluded from ranking averages. Multi-evidence completeness has its own
denominator of six complementary-evidence questions.

| Strategy | Hit@5 | Recall@5 | MRR@5 | nDCG@5 | Multi-evidence complete@5 |
| --- | ---: | ---: | ---: | ---: | ---: |
| Keyword only | 0.5417 | 0.5417 | 0.5208 | 0.5229 | 3/6 |
| Vector only | 1.0000 | 0.9375 | 0.8972 | 0.8870 | 3/6 |
| Hybrid RRF | 1.0000 | 0.9583 | 0.8764 | 0.8844 | 4/6 |
| Hybrid + Qwen, recorded 2s round | 1.0000 | 1.0000 | 1.0000 | 0.9967 | 6/6 |
| Hybrid + Qwen, recorded 5s round | 1.0000 | 1.0000 | 1.0000 | 0.9967 | 6/6 |

Compared with vector-only, hybrid RRF raises Recall@5 by **2.08 percentage points**
and multi-evidence completeness from 3/6 to 4/6, but lowers MRR@5 by 0.0208 and
nDCG@5 by 0.0026. At nDCG@5, **one answerable question improves (q20), one regresses
(q06), and 22 are unchanged**. Equal metric values do not imply identical full
rankings (for example q13 and q24).

Compared with hybrid RRF, the reused Qwen rankings raise Recall@5 by 4.17 percentage
points and nDCG@5 by 0.1123. The 2s result still includes its three original
fallbacks; the 5s round applied to all 30 questions. Neither was rerun or selected
by favorable per-question outcomes.

## Language matters

| Answerable slice | Strategy | Recall@5 | nDCG@5 |
| --- | --- | ---: | ---: |
| Chinese (12) | Keyword | 0.0833 | 0.0833 |
| Chinese (12) | Vector | 0.9167 | 0.8318 |
| Chinese (12) | Hybrid | 0.9167 | 0.8318 |
| English (12) | Keyword | 1.0000 | 0.9626 |
| English (12) | Vector | 0.9583 | 0.9422 |
| English (12) | Hybrid | 1.0000 | 0.9370 |

The documents are English-only. Fourteen of 15 Chinese questions have no keyword
hits; q13 contains `P1` and does match. Among answerable Chinese questions, keyword
retrieval succeeds on only 1/12. This is a language-mismatch limitation of this
setup, not evidence that lexical retrieval is generally poor. In the English slice,
keyword-only actually has the highest pre-rerank nDCG of the three retrieval modes.

## Concrete cases, including the negative result

### q20: complementary P1/P2 evidence enters the top five

Question: "Compare the P1 acknowledgment deadline with the P2 response deadline."
Both `incident-p1` and `incident-p2` have grade 2.

- Vector: P2 rank 1, P1 rank 7; Recall@5 = 0.5, nDCG@5 = 0.6131.
- Keyword: P2 rank 1, P1 rank 3.
- Hybrid: P2 rank 1, P1 rank 3; Recall@5 = 1, nDCG@5 = 0.9197.
- Recorded Qwen: P2/P1 ranks 1/2; nDCG@5 = 1.

This accounts for the entire +2.08-point macro Recall@5 change from vector to
hybrid: one of 24 questions gains 0.5 recall. P1 was already in the vector candidate
pool; the gain is **top-five promotion**, not discovery of previously absent evidence.

### q06: rank fusion demotes the actually relevant receipt policy

Question: "Is a screenshot of my payment sufficient for an expense claim?"
Only `expense-receipt` has a positive grade (3).

- Vector: receipt rank 1, deadline rank 2; nDCG@5 = 1.
- Keyword: deadline rank 1, receipt rank 2.
- Both get the same RRF score `1/61 + 1/62`. Production breaks the tie by ascending
  chunk ID: deadline 413 before receipt 414.
- Hybrid: receipt falls to rank 2; nDCG@5 = 0.6309 and MRR@5 = 0.5.
- Recorded Qwen restores receipt to rank 1.

This illustrates the limitation of rank-only fusion: it cannot infer which policy
answers the question when two sources have symmetric channel ranks. No weight or
tie-breaker was tuned after observing this case.

### q19 and q21: adding a channel cannot help when that channel has no hits

Both are Chinese multi-evidence questions with zero keyword hits, so hybrid and
vector orders are identical.

- q19 (trash versus backup recovery): backup rank 1, document deletion rank 8;
  recorded Qwen promotes deletion to rank 2.
- q21 (receipt and filing deadline): deadline rank 5, receipt rank 8;
  recorded Qwen puts them at ranks 1/2.

Together these explain the Qwen step's +4.17-point Recall@5 gain over hybrid RRF.
They do not establish that a keyword channel was necessary for the Qwen gain.

### q24: full evidence coverage is not perfect ranking

Both required sources are in the top five for every nonempty strategy, yet the
Qwen result keeps irrelevant P2 evidence at rank 2 between P1 and support hours.
Its per-question nDCG@5 remains 0.9197, not 1. Hit/Recall/completeness alone would
hide this defect. The report retains it.

## Provenance and fair-comparison controls

- Capture completed at `2026-10-10T11:40:27.616754900Z`, tooling commit `55eb8e1`.
- Reused the original isolated group 2 and its documents 3–18. No new documents,
  groups, indexing, ETL jobs or production configuration changes.
- Real local Ollama embeddings, PostgreSQL vector search, Elasticsearch keyword
  search and PostgreSQL READY hydration. The evaluation account passes normal
  authentication/membership checks; credentials are not in these artifacts.
- Each channel is queried once per question; gates reuse the same real ordered
  hits through production fusion/assembly, disabling only the other input for
  single-channel modes. This isolates ranking effects without three independent
  captures that could vary between requests.
- Original query only, no planner/chat call. Channel top-k 50; candidate cap 30;
  RRF constant 60; neighbor window 1; evidence budget 4096; metric cutoff 5.
- Model `qllama/bge-small-zh-v1.5:latest`, digest
  `9a2067741c2aacd74a623b0e09b40ab437bc4a5ebe9cd59a776376e414931c89`.
- Reviewed dataset SHA-256:
  `6fc5869de0b06c3f7f9b00ff498ada9c085785131a0a38810c291bdaf4d46d35`.
- All 30 hybrid candidate texts, identities and **full input orderings** exactly
  matched the frozen snapshot before reusing either historical Qwen result.
- Original input texts, labels, Qwen outputs, statuses and latencies are unchanged.
  Capture and source-report SHA-256 values are stored in `comparison.json`.

## Limits and interpretation

The 16 short, one-chunk documents are below the per-channel limit of 50; vector
retrieval returns all of them on every question. Candidate recall is therefore
100% for vector and hybrid. This experiment isolates ordering within a small,
fully retrieved corpus, not large-corpus recall improvement.

There is no `vector + rerank` arm. The candidate sets happen to be identical, but
their input order differs, so the existing Qwen results must not be relabeled as
a measured vector-only rerank experiment. The result does not establish that
hybrid is necessary or optimal once a reranker is present.

Single-pass channel timings are retained only as diagnostics. Vector timings
include local embedding and database retrieval; the first call can be cold.
Historical Qwen timings are from another run. These are not comparable end-to-end
latency samples and must not be added together to invent a full-pipeline P95.

No final answers, citations or refusal behavior were evaluated. Six all-zero
judgment questions are not six successful refusals. Ranking treats any positive
grade as relevant, preserving the existing metric definition; multi-evidence
completeness excludes optional background and uses only six complementary cases.
No significance/generalization claim is made from 24 answerable synthetic questions.

The experiment provides a reproducible channel ablation, measures a
coverage/ranking trade-off, and traces both a fusion benefit and a tie-induced
regression. Hybrid does not improve every metric; these percentages do not
describe production answer accuracy.

## Reproduce offline

```bash
mvn "-Dtest=RetrievalAblationReportRun" "-Deval.ablationDir=evaluation/retrieval/results/northstar-2026-10-10" test
mvn "-Dtest=RetrievalAblationTest,RecordedRetrievalAblationTest,RecordedEvaluationTest" test
```

- `capture.json`: raw local channel hits/scores, per-mode orders, settings and timings.
- `comparison.json`: reproducible aggregates, per-case metrics/ranks and source hashes.
- `report.html`: Chinese reading view, including every unchanged/negative case.

These commands need only Java 21 and the saved files; no services, account or API
key are required. JSON bytes are preserved by `.gitattributes` across platforms.
