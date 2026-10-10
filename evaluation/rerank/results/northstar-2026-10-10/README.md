# Northstar: real-retrieval / Qwen rerank experiment

**Provisional results: synthetic corpus, labels awaiting human review.**

Start with [the Chinese review packet](review.html). It contains all 30 questions,
expected-answer notes, provisional grades, source text and ranking comparisons.
The [review template](review-template.json) has every question/source pair marked
`PENDING_HUMAN_REVIEW`. No human approval or answer-generation accuracy is claimed.

## Setup and provenance

- Captured on 2026-10-10, run `northstar-d41905eb`, using collector commit `6575b9e`.
  The collector code was validated and then committed immediately after capture.
- Review generator commit: `f1ed77f`.
- 16 short English policy documents, 30 Chinese/English questions, 24 provisionally
  answerable and 6 provisionally unanswerable. These are fictional policies, not
  SpringMind product behavior or actual company rules.
- Real registration/login/group/upload controllers and authentication filters
  (in-process MockMvc HTTP transport); real MinIO storage, durable asynchronous
  ingestion, PostgreSQL, Ollama embeddings, Elasticsearch and RRF retrieval.
- The running backend processed all 16 uploads to READY. Each source produced one
  exact-text chunk; the collector verified this before transferring labels.
- Embeddings: `qllama/bge-small-zh-v1.5:latest`, 512 dimensions, local Ollama digest
  `9a2067741c2aacd74a623b0e09b40ab437bc4a5ebe9cd59a776376e414931c89`.
- Channel top-k 50, RRF constant 60, candidate limit 30, final evidence top-k 5,
  neighbor window 1, evidence budget 4096 tokens. All 16 documents entered each
  candidate pool. Snapshot text/order was captured immediately before reranking.
- Original questions only: production direct-query fallback, without query
  rewriting or answer generation. Candidate snapshot frozen across both rounds.
- Qwen endpoint: `https://maas.qianwenaiapi.com/compatible-api/v1/reranks`, model
  `qwen3-rerank`. No claim about the hosted model's parameter count/version.
- Fixture SHA-256:
  `910509167602939d96fb21fb2b2d0c70b69f4cac668807cadcc71e24e38a521e`.
- Snapshot SHA-256:
  `864c3ed7f88bfec7ba46093a385f1e6e440760684dd35faa04ffdf2a38773883`.

The collector retained its private manifest, account credentials, full candidate
metadata and traces under the ignored local run directory. Only explicitly selected
synthetic-data artifacts are recorded here; no credentials are included.

## Observed results

### Current grading revision: complete-answer-v2 (offline only)

The user confirmed a uniform rubric: 3 = one passage suffices for the whole
question; 2 = directly supports only part of the answer and needs other evidence;
1 = background only; 0 = irrelevant. q17–q21 and q24 each require two complementary
passages, now both graded 2. This changes 11 grades (q24's `support-hours` was
already 2). q16 retains 3 for `external-ai`, which independently answers its
yes/no question, and 1 for `group-access` background. Other labels are unchanged.

The review page/template now use v2 labels. This rubric alignment does **not**
claim all judgments have been independently human-reviewed. No Qwen request was
made for this revision. The original texts, questions, input/output ordering,
statuses, latency and raw reports are unchanged; v2 reports are derived artifacts.

| Metric at k=5 | RRF with v2 labels | Qwen 2s with v2 labels | Qwen 5s with v2 labels |
| --- | ---: | ---: | ---: |
| Hit | 1.0000 | 1.0000 | 1.0000 |
| Recall | 0.9583 | 1.0000 | 1.0000 |
| MRR | 0.8764 | 1.0000 | 1.0000 |
| nDCG | 0.8844 | 0.9967 | 0.9967 |

Only nDCG changes: its macro delta is now 0.1123, previously 0.1174. q17–q21
change both relevant grades equally, so their normalized scores are unchanged.
q24 changes from unequal 2/3 to equal 2/2; its RRF nDCG becomes 0.6934 and Qwen
nDCG becomes 0.9197. Equal weighting penalizes the second required passage being
ranked third more than the old unequal weighting did. This is a judgment-rubric
correction, not a change in model performance. Hit/Recall/MRR are unchanged.

Reproduce without network calls: `mvn "-Dtest=OfflineRelabelRun" test`.
The source-report hashes and 11 individual edits are embedded in both v2 reports.

### Original recorded results (v1 labels, retained for audit)

Metrics are macro-averaged over the 24 provisionally answerable questions. Every
positive grade, including background grade 1, counts as relevant for Hit/Recall/MRR.
The six unanswerable questions are excluded, not treated as correct refusals.

| Metric at k=5 | Real RRF order | Qwen, 2s timeout | Qwen, 5s timeout |
| --- | ---: | ---: | ---: |
| Hit | 1.0000 | 1.0000 | 1.0000 |
| Recall | 0.9583 | 1.0000 | 1.0000 |
| MRR | 0.8764 | 1.0000 | 1.0000 |
| nDCG | 0.8808 | 0.9982 | 0.9982 |
| Applied / fallback | Not applicable | 27 / 3 | 30 / 0 |
| Adapter latency P50 | Not measured here | 1151 ms | 346 ms |
| Adapter latency P95 | Not measured here | 2011 ms | 401 ms |

The 2s round is the unchanged configured/default timeout. Calls q01, q13 and q14
timed out and retained RRF order; these failures remain in all aggregate metrics.
Their baseline top evidence was already correctly placed under the current labels.
The second round replayed **all 30 questions**, not only failed or favorable cases,
with a 5s timeout. No deployment settings were changed. Both raw reports are kept.

The second round was sequential and repeated identical requests. Its lower latency
may reflect warm connections, provider caching, load changes or other uncontrolled
factors. It does **not** demonstrate that increasing timeout makes inference faster.
Neither 30-request round establishes production P95 or an availability SLA.

There were 60 attempted Qwen requests in total, without application retries. The
provider's actual billed tokens/cost were not returned by this adapter and remain
unknown; check the provider console rather than treating this experiment as free.

## What actually changed

- q19 (trash versus backup recovery) and q21 (receipt requirements plus filing
  deadline) each had only one of two labeled sources in RRF's top five. Both labeled
  sources entered the top five after reranking. This accounts for the **4.17
  percentage-point** macro Recall@5 increase, not a 4.17% relative improvement.
- First relevant evidence moved earlier for q05, q06, q09, q21 and q24, explaining
  the MRR change. Hit@5 was already 100%; it did not improve.
- Candidate recall was 100% because every question received all 16 documents.
  Reranking only changed order; it did not discover missing evidence.
- Keyword hits were zero for 14 questions (Chinese queries against English source
  text); q13's `P1` term and all English questions had keyword hits. Vector retrieval
  returned 16 hits for every question. The two channels really ran, but they did not
  both contribute hits on every query.

## Review priorities and limits

1. q16: whether `group-access` deserves background grade 1 or should be irrelevant.
2. q17–q21 and q24: the 2/3 convention has now been aligned in v2 as documented
   above. Continue checking the factual evidence; do not change grades to favor
   the model. The original v1 labels remain in the frozen snapshot for audit.
3. q25–q30: verify the whole corpus lacks the requested answer; do not confuse
   unanswerability with a retrieval miss or an actual model refusal.
4. Review implicit zero grades too. The packet exposes all 480 question/source
   pairs, not just the initially positive evidence.

This is a small, easy, single-topic synthetic set with short texts and inherited
labels, not a held-out production benchmark. Do not claim general retrieval quality,
final answer accuracy, citation correctness, refusal accuracy or budget-selection
improvements from these numbers. No chat model generated answers in this experiment.

## Files and reproducibility

- `snapshot.json`: exact frozen candidate text/order plus provisional judgments.
- `qwen-2s.json` / `qwen-5s.json`: unmodified evaluator outputs, including per-case
  ranks, statuses, measurements and the matching snapshot hash.
- `snapshot-v2.json`: same frozen text/order, with the unified grading rubric.
- `qwen-2s-v2.json` / `qwen-5s-v2.json`: offline rescoring of the recorded ranks,
  including original report hashes and a label-change audit trail.
- `review.html`: Chinese review packet based on the complete 5s replay, with v2 grades.
- `review-template.json`: pending human judgments, with all current source grades explicit.

JSON bytes are preserved by `.gitattributes` so the snapshot hash remains valid on
different operating systems. Existing input order, output order and labels allow
offline metric recomputation without further paid calls. Another live replay is
optional and may differ as hosted service behavior changes.

The isolated local knowledge group (ID 2) and its 16 uploaded documents (IDs 3–18)
remain available for inspection; no existing user documents were changed or removed.
The generated account's credentials are only in the ignored local `account.json`.
