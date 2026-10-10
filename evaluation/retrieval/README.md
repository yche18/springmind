# Single-channel and hybrid retrieval ablation

This experiment reuses the 30 reviewed Northstar questions, 16 source passages and
`complete-answer-v2` annotations from [the reranking experiment](../rerank/results/northstar-2026-10-10/README.md).
It compares keyword-only, vector-only, hybrid RRF, and both previously recorded
Qwen timeout rounds. It does not call Qwen or a chat model.

The [recorded Northstar comparison](results/northstar-2026-10-10/README.md) includes
all 30 cases, a coverage/ranking trade-off, and both improved and regressed examples.

A separate [Harborview extension](results/harborview-2026-10-10/README.md) adds 24
questions and 66 bilingual documents with real multi-chunk ingestion. Its new
judgments are pending human review; the original Northstar experiment is unchanged.

## Capture from the existing local corpus (Java 21)

Start the normal PostgreSQL, Elasticsearch and Ollama services. The original
isolated group and ignored `evaluation/rerank/local/northstar-d41905eb/` manifest
and account file must still exist. No new uploads, ingestion jobs or groups are
created. Login uses only that evaluation account and may create its normal auth
session. Startup recovery, ingestion workers, admin initialization and schema
migrations/initialization are disabled in the capture context.

```bash
mvn "-Dtest=LiveRetrievalAblationRun" "-Deval.ablation=true" test
```

The manual `*Run` class is excluded from ordinary tests, and requires the explicit
flag. Optional paths: `-Deval.captureDir=<original-private-capture>` and
`-Deval.ablationOutput=<new-output-file>`. Existing output files are never replaced.
The default output is ignored `target/retrieval-ablation/capture.json`.

Each query calls the actual vector and keyword adapters once (top 50 per channel).
Those exact ordered hits are replayed through the production `HybridEvidenceRetriever`
with keyword-only, vector-only and both-channel gates. Only the gate inputs are
test doubles; hits are not fabricated, and PostgreSQL evidence hydration, READY
filtering, window assembly and production RRF are real. Candidate cap 30, RRF
constant 60, neighbor window 1, evidence budget 4096 and metric cutoff 5 match the
original run. Query planning uses its production null-user original-query fallback.
The paid chat/rerank beans are replaced and verified unused.

Metrics are calculated on **pre-rerank candidate rankings**, not post-budget
evidence or generated answers. The collector checks membership, exact single-chunk
source content, index identities, embedding model digest and all hybrid input
texts/orders against the frozen snapshot. Drift stops capture instead of attaching
old rerank results to new inputs. Keyword adapter warnings fail the run rather than
silently scoring an outage as empty retrieval. No account/token is exported.

Channel timings in the capture are one sequential local measurement per question,
with warm-up/cache/order effects. Gate replay times are not measured service
latencies. Do not add historical Qwen latency to them or claim an end-to-end SLA.

## Offline report and checks

```bash
mvn "-Dtest=RetrievalAblationReportRun" test
mvn "-Dtest=RetrievalAblationTest,RecordedRetrievalAblationTest,RecordedEvaluationTest" test
```

`-Deval.ablationDir=<directory>` selects a directory containing `capture.json` for
the report command. It writes deterministic `comparison.json` and Chinese
`report.html`. No Spring context, database, credentials or model client is used.

The report binds inputs by SHA-256, verifies both historical rerank permutations,
and independently checks raw channel orders and RRF arithmetic (including chunk-ID
tie breaking) for this one-chunk-per-document corpus. It retains all 30 questions,
both timeout rounds including fallbacks, language/category breakdowns and every
case that improves, regresses or stays unchanged.

- Ranking means exclude the six unanswerable questions, but empty retrieval on an
  answerable question is a miss, not excluded.
- Hit/Recall/MRR retain the original `grade > 0` definition, including background
  grade 1. nDCG uses gain `2^grade - 1`.
- Multi-evidence completeness requires all complementary grade-2 passages in the
  top five, only for q17–q21 and q24 (six questions). q16's optional background does
  not become a required answer component.
- This small corpus is fully recalled by the vector channel, and English-only
  sources disadvantage Chinese lexical queries. A winning overall score does not
  prove broad generalization; report language slices and negative cases too.
- Labels remain approved and unchanged. New or changed questions/labels require
  fresh review. Do not tune the fixture to make hybrid retrieval win.
