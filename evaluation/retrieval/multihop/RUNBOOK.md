# Reproduce the frozen local diagnostic

## Scope and storage

This is a retrieval diagnostic, not an end-to-end answer benchmark. No application
Spring context is started and no chat, planning, cloud embedding, or reranker API
is called. It uses the production keyword query/mapping, chunker and context
assembly, but local exact cosine instead of pgvector ANN. Do not compare these
numbers directly to the official paper or the earlier English-analyzer BEIR run.

Complete news articles, vector batches and detailed context quotations stay in
ignored `target/`. The compact official question/answer/fact annotations are
versioned in `sample.json` under the dataset's ODC-BY license with source attribution.
This clarifies the frozen protocol's shorthand about local-only raw news.
Selected questions may contain their answer entity; the report preserves them
and flags this limitation instead of silently removing easier examples.

## Download (no inference)

Download `corpus.json`, `MultiHopRAG.json`, and `README.md` from:

`https://huggingface.co/datasets/yixuantt/MultiHopRAG/resolve/71ac0d0bd1f951d2d6b70311f7d2ae404e1ffa82/<filename>`

Place them under `target/multihop-downloads/`. `freeze.json` records the SHA-256
for every source file. Keep the 609-article corpus intact. Do not re-run the freeze
writer over an existing sample; the committed sample is the experiment definition.
Formal/calibration counts are 50/10, including 6/1 null questions.

## Local collection

Use Java 21. Elasticsearch must be reachable on loopback port 9200 with the project's
IK analyzer. A local Ollama endpoint on loopback port 11435 must expose the pinned
`bge-m3:567m` digest in `evaluation/retrieval/multilingual-embedding/model-lock.json`.
The existing isolated GPU container `springmind-beir-ollama-gpu` can be started;
it mounts the application's model volume read-only. Stop this evaluation container
after collection. The normal application Ollama container is not reconfigured.

```powershell
$env:JAVA_HOME='C:\Users\CY\.jdks\ms-21.0.12'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn '-Dtest=LiveMultiHopRun' '-Deval.multihop=true' '-Deval.confirmLocalWrites=true' test
```

Only the isolated `springmind_eval_multihop_20261011` Elasticsearch index is created.
It is write-blocked after indexing. No application documents, groups, accounts,
PostgreSQL records or MinIO files are modified. `target/multihop-cache` binds
resumable binary vector batches to the frozen inputs; truncated/corrupt batches
fail verification rather than being silently accepted. An existing final capture
is never overwritten. Keep failed caches for diagnosis.

## Offline reports and tests

```powershell
mvn '-Dtest=MultiHopReportRun' '-Deval.multihopReport=true' test
mvn '-Dtest=MultiHopEvaluationTest,RecordedMultiHopTest' test
```

The report generator reads the saved capture and local source files only. It writes
new `comparison.json`, `report.html`, `artifact-hashes.json`, and the local
`target/multihop-review/review.html`; it refuses to overwrite existing reports.
For deliberate regeneration, archive the old derived reports first, retaining the
capture and frozen sample. Routine verification uses `RecordedMultiHopTest`, not
another collection run. Default tests do not call any external services.

Open `evaluation/retrieval/results/multihop-2026-10-11/report.html` in a browser.
The linked local review adds exact selected chunks. Check reference sufficiency,
necessary versus redundant facts, possible semantic alternatives, and null answers.
Do not mark labels HUMAN_REVIEWED until the user actually approves them. Corrections
must be a separate, auditable label revision with before/after metrics.
