# Filename-field ablation protocol

Reuse the human-reviewed Harborview fixture, 105 real indexed chunks and all 24
questions without modifying sources, filenames, labels or production settings.
Twenty answerable cases contribute ranking means; four no-answer cases are
reported but are not counted as successful refusals.

Two keyword-only arms are declared before retrieval:

- `BODY_ONLY`: remove the two `fileName` clauses from the production initial
  query AND the two `fileName` clauses from its Elasticsearch rescore query.
- `BODY_AND_FILENAME`: use the production request body unchanged.

Keep filters (evaluation group, READY, not deleted), analyzers, body boosts,
phrase matching, rescore weights/window, query wording and top 50 unchanged.
Removing filenames only from the first stage would leave a confounded experiment.
This is not bare BM25 versus hybrid, and Elasticsearch rescore is not Qwen rerank.

Use a single Elasticsearch point-in-time search view for source validation and
both arms. Alternate which arm runs first by case. Store both request bodies,
raw ranks/scores, corpus/fixture/approval/protocol hashes and index metadata.
Reject timeouts, failed shards, foreign or changed sources, duplicate hits and
invalid scores. Compare the fresh filename-enabled arm with the old recorded
keyword ranks, explicitly reporting any drift rather than silently reusing them.
Never overwrite a capture or choose repeat runs by performance.

Use the existing full-corpus relevance labels and metric implementation. Report
Hit/Recall/MRR/nDCG at 5, chunk recall at 30 and 50, distinct fact coverage at 5,
and complete complementary-fact coverage. Include language/category slices and
all per-question changes, including regressions. These are raw chunk rankings,
not neighbor-expanded windows, answer quality or production accuracy. A filename
benefit may reflect English aliases in these descriptive synthetic filenames;
it does not establish cross-language semantic understanding.

The live entry point uses only the local Elasticsearch HTTP API, no Spring
application context, database writes, ingestion, embeddings, chat or Qwen calls.
It creates/closes only a temporary search context. No documents/indexes change.

```bash
# Java 21; local Elasticsearch must already be running.
mvn "-Dtest=LiveFilenameAblationRun" "-Deval.filename=true" test
# Offline regeneration and verification; no service or model required.
mvn "-Dtest=FilenameAblationReportRun" test
mvn "-Dtest=FilenameAblationTest,RecordedFilenameAblationTest" test
```

Default output: `evaluation/retrieval/results/harborview-filename-2026-10-10`.
Override with `-Deval.filenameOutput=<new-directory>` for a separately retained
run. The frozen original Harborview and Northstar artifacts remain untouched.
