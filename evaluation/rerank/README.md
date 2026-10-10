# Fixed-pool reranking evaluation

This tool replays exactly the same candidate texts through the production
`HttpEvidenceReranker` and compares the supplied order with its output. It does
not query PostgreSQL, Elasticsearch or the chat model. It does not evaluate
query planning, evidence-window selection, token budgets, answer quality or
refusal. There is no model-based judge and no extra runtime dependency.

The bundled `sample.json` contains **30 hand-authored synthetic cases** against a
fictional company handbook: 24 answerable and 6 unanswerable, in Chinese and
English. Candidate ordering is hand-authored, **not measured RRF output**. Use it
to exercise the tooling/API, not as evidence of production quality.
Some relevant passages are deliberately below rank five.

An initial run through the real local ingestion/retrieval stack and Qwen is now
recorded in [the Northstar experiment](results/northstar-2026-10-10/README.md),
including both timeout rounds and a [Chinese annotation-review packet](results/northstar-2026-10-10/review.html).
Those labels are still provisional; the measurements are not production claims.

## 1. Offline smoke check (no credentials or charges)

Use Java 21 from the repository root:

```bash
mvn "-Dtest=RerankEvaluationRun" test
```

The entry point is deliberately named `*Run`, so ordinary `mvn test` does not run
it. Even when explicitly invoked, live calls are disabled by default regardless
of `RERANK_ENABLED` or any existing credentials. It does not load `.env`.

The command validates the dataset and writes a timestamped JSON report under
`target/rerank-evaluation/` (ignored by Git). Offline reports have mode
`OFFLINE_BASELINE_ONLY`, zero planned provider requests, and **null** reranked
metrics, deltas, latency and cost. These nulls mean "not measured", not zero.

Run the tool's regression tests separately:

```bash
mvn "-Dtest=RerankEvaluationTest,HttpEvidenceRerankerTest,QaRetrievalHarnessTest" test
```

## 2. Opt-in provider comparison (external data transfer and possible charges)

Only use data approved for the configured provider. Set `RERANK_URL`,
`RERANK_MODEL`, and, when required, `RERANK_API_KEY` in your process environment
using your normal secret mechanism. Do not paste keys into tracked files or
command arguments. See [Qwen setup and diagnostics](../../docs/evidence-selection.md).
The tool requires an explicit URL and model; it does not silently choose a vendor.

```bash
mvn "-Dtest=RerankEvaluationRun" "-Deval.live=true" "-Deval.confirmExternal=true" "-Deval.maxRequests=30" "-Deval.timeoutMs=2000" test
```

Both live flags are required. The complete dataset is validated and its request
budget checked **before** the first request. By default, at most 30 cases with
two or more candidates may run (override range 1..500). Cases run sequentially,
one request each, with no automatic retries. Empty/single pools are `SKIPPED`.
The default timeout is 2000 ms; set it to match the deployed timeout for a fair
comparison. This standalone runner uses `eval.timeoutMs`, not `RERANK_TIMEOUT`.

The production adapter caps each text preview at 4096 Unicode code points and
asks the provider to score every candidate. Account for this truncation when
labeling long passages. The full question and previews are sent to the provider.
Failures retain the original ordering and remain in the comparison. Inspect
`summary.statuses` before interpreting metrics: a completed run with every call
`FALLBACK` is not a successful model evaluation. The backend's safe diagnostic
reason codes also appear in this command's output.

Reports store opaque IDs, ranks, settings, dataset SHA-256 and measurements, but
not questions, passages, endpoint URLs or credentials. Keep real datasets under
`evaluation/rerank/local/` (ignored) and keep reports private until reviewed.
Provider cost is **unknown**, recorded as `billedCost: null`; consult the provider
billing/usage console. Latency is wall-clock adapter time, including failures,
not end-to-end answer latency. A 30-case P95 is noisy; repeat under comparable
conditions before making latency claims. Hosted model revisions can also change.

## 3. Replace the fixture with a real RRF snapshot

### Capture the bundled corpus through the real local services

With the normal local Compose stack and backend ingestion worker running, Java 21:

```bash
mvn "-Dtest=LiveRetrievalCaptureRun" "-Deval.capture=true" "-Deval.confirmLocalWrites=true" test
```

This explicitly creates a dedicated ordinary account, a knowledge group, and 16
TXT uploads through the real controllers/auth filters (MockMvc transport only).
MinIO, database, asynchronous ingestion, Ollama embeddings and Elasticsearch are
real, not mocks. The runner's own worker and startup recovery are suppressed to
avoid claiming/backfilling unrelated jobs. The running backend ingests the uploads.
It waits up to five minutes for all documents to become READY and requires exact
one-chunk text preservation before transferring the provisional fixture labels.

For each question it checks group membership and calls the production hybrid
retriever, intercepting the exact inputs at `EvidenceReranker`. It uses the original
query via the production no-user planning fallback: **no query rewriting, paid
planning, reranking or answer-generation calls occur during capture**. This is a
controlled retrieval/rerank experiment, not full QA accuracy testing. The current
embedding model remains whatever the local application configuration specifies.

Artifacts go to ignored `evaluation/rerank/local/northstar-<run>/`: `snapshot.json`
for the existing evaluator, `candidates.json` for exact windows/metadata,
`traces.json` for channel counts and timings, and `manifest.json` for group/document
IDs and provenance. `account.json` contains the generated test-account credentials;
**never commit it**. The group/account/documents are retained for inspection, not
automatically deleted. A new capture creates a new isolated group. If interrupted,
use its manifest to inspect only those created resources before rerunning.

Run the captured snapshot with the existing explicit live flags and
`-Deval.dataset=evaluation/rerank/local/northstar-<run>/snapshot.json`. Labels remain
provisional until a reviewer checks all questions against all 16 source passages.
The collector fails on foreign-group sources, foreign documents, split chunks or
ambiguous repeated windows instead of silently assigning misleading labels.

### Generate a human-review packet after replay

```bash
mvn "-Dtest=ReviewPacketRun" "-Deval.captureDir=evaluation/rerank/local/northstar-<run>" "-Deval.report=target/rerank-evaluation/report-<timestamp>.json" test
```

This makes no model calls. It checks the fixture and snapshot hashes, then writes
`review.html` and `review-template.json` inside the capture directory. The HTML
contains each question, expected-answer notes, provisional positive labels,
before/after ranks, and all 16 source texts including implicit zero grades. The
template expands all 480 question/source grades for explicit review. Nothing is
marked human-reviewed automatically. Review notes are reading aids, not generated
answers or ground truth. Pay special attention to multi-evidence grading and the
six unanswerable cases. Do not change labels to match the model's preferences.

The saved output ordering can be rescored after label corrections without another
provider call. The capture remains synthetic even though its retrieval is real.

For the recorded Northstar run, after editing only labels in `sample.json`:

```bash
mvn "-Dtest=OfflineRelabelRun" test
```

This offline-only entry point rejects changes to question/source text, verifies the
original snapshot hash, and reuses recorded rankings. It writes `snapshot-v2.json`
and `qwen-2s-v2.json` / `qwen-5s-v2.json`, then updates the review page/template.
Original `snapshot.json` and Qwen reports remain byte-for-byte unchanged. Derived
reports record source hashes, changed grades, zero new provider requests, and keep
the original measured status/latency rather than timing the offline replay. The
original capture's fixture hash is historical: do not bypass it in `ReviewPacketRun`
after editing labels; use this explicit relabeling path instead.

### Custom dataset format

Create a JSON file under `evaluation/rerank/local/`. The shape is:

```json
{
  "provenance": "RRF_SNAPSHOT",
  "documents": {
    "passage-001": "The exact authorized candidate-window text...",
    "passage-002": "Another candidate-window text...",
    "passage-003": "A relevant passage the retriever missed..."
  },
  "cases": [{
    "id": "question-001",
    "category": "multi-evidence",
    "query": "A representative user question",
    "candidates": ["passage-002", "passage-001"],
    "relevance": {"passage-001": 3, "passage-003": 2}
  }]
}
```

- `provenance`: `SYNTHETIC` or `RRF_SNAPSHOT`. The tool validates the format, not
  the historical truth of the label. Do not relabel hand-ranked examples as RRF.
- `documents`: candidate-window IDs to exact text, not filenames. For real data,
  capture the **authorized, READY, clustered/windowed list immediately before
  `EvidenceReranker.rerank`**, using a local debugger or a controlled test fixture.
The answer UI contains only selected evidence and is not a complete candidate
  export. No production raw-text logging/export endpoint is introduced here.
- `candidates`: unique IDs in their original pre-rerank order (0..50 per question).
  Freeze query, candidates and labels for both sides. A relevant passage absent
  from the pool must remain absent; do not insert it to improve results.
- `relevance`: integer judgments: 0 irrelevant; 1 background only, not direct
  answer support; 2 directly supports part of the answer but requires additional
  evidence; 3 a single passage suffices to answer the whole question. Multiple
  retrieved passages do not automatically imply grade 2: independently sufficient
  passages still get 3. Every positive grade counts as relevant for
  Hit/Recall/MRR; nDCG uses the graded values. Omitted IDs count as irrelevant.
  Judge the entire pool and include known relevant corpus passages outside it.
  Incomplete judgments make the reported recall conditional on those judgments.
- Empty/all-zero labels mean **judged unanswerable in this corpus**, not
  "annotation unfinished". Finish annotation before running an evaluation.
  Review these cases manually for refusal in the actual application; reranking
  cannot establish whether the answer generator refuses appropriately.
- IDs/categories must be opaque letters, digits, `_` or `-`, at most 80 characters.
  Text is Unicode. The reader rejects unknown fields, fractional/string grades,
  duplicate case/candidate IDs, unknown references and files over 10 MB. Limits:
  500 cases, 10000 corpus entries, 100000 characters per text, 16000 per query.

Run your own dataset with:

```bash
mvn "-Dtest=RerankEvaluationRun" "-Deval.dataset=evaluation/rerank/local/my-dataset.json" "-Deval.k=5" test
```

Add the explicit live flags only after reviewing the data and expected request
count. Record the Git commit, dataset hash, model/region, time, candidate-limit
and production retrieval settings alongside any published result. Keep a held-out
question set for the final comparison rather than tuning on every question.

## Metric definitions and interpretation

All ranking metrics are macro-averaged over judged-answerable questions. Empty
candidate lists still count as misses. Unanswerable cases are counted separately
and excluded from these averages, **not treated as perfect or failed refusals**.

| Report field | Definition at cutoff `k` (default 5) |
| --- | --- |
| `hit` | 1 if any positively labeled passage appears in the first k, else 0 |
| `recall` | Relevant passages in first k / all positively labeled corpus passages |
| `mrr` | Reciprocal of first relevant rank within k, or 0 |
| `ndcg` | DCG / ideal DCG, gain `2^grade - 1`, discount `log2(rank + 1)` |
| `candidateRecall` | Relevant passages anywhere in the pool / all positively labeled corpus passages |
| `delta` | Reranked metric minus baseline, including fallback cases |
| `latencyP50Ms`, `latencyP95Ms` | Nearest-rank percentiles of attempted adapter calls; excludes skipped cases |

For example, a recall delta of `0.07` means **7 percentage points**, not a 7%
relative improvement. Reranking cannot recover evidence outside the candidate
pool. When candidate recall is low, inspect retrieval first. When it is high but
top-k ranking is weak, inspect reranking and the text preview presented to it.
High ranking scores alone do not prove grounded answers or correct citations.

## Application acceptance checklist (separate from this replay)

Use a dedicated test group and approved documents. For each scenario record the
question, trace ID, expected source IDs, actual citations, rerank status and
stage timings. Do not publish source content without permission.

1. Answerable Chinese and English questions: successful rerank status and citations
   that open the intended source passage. An unchanged rank can be valid.
2. Multi-document question: all necessary evidence survives selection within the
   evidence budget. Verify the answer, not just retrieval scores.
3. No supporting evidence: the answer must state the limitation rather than invent
   supporting citations. Do not infer refusal quality from reranker scores.
4. Disabled/unavailable provider in a test deployment: original RRF order remains
   usable and the UI accurately shows disabled/fallback status.
5. Non-member/deleted/unready sources: permission/visibility checks still prevent
   access. Do not test destructive operations against production documents.

Only report an improvement after this application-level check and a real, reviewed
dataset comparison. The repository's synthetic baseline is not that evidence.
