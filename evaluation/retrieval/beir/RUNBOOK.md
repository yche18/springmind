# Running the public benchmark locally

The frozen controls are in [README.md](README.md); do not edit that file or the
freeze manifest after collection. This runbook describes operational details,
not a new parameter search. Java 21 is required.

## Inputs

Download the official BEIR archives into ignored `target/beir-downloads`:

- <https://public.ukp.informatik.tu-darmstadt.de/thakur/BEIR/datasets/scifact.zip>
- <https://public.ukp.informatik.tu-darmstadt.de/thakur/BEIR/datasets/nfcorpus.zip>

Extract each so that `target/beir-downloads/<dataset>/corpus.jsonl`,
`queries.jsonl` and `qrels/test.tsv` exist. The collector checks the official ZIP
MD5s and every frozen source SHA-256; it refuses changed or partial inputs.
Observe the source licenses described in README.md. Raw texts stay local.

Use the pinned `bge-m3:567m` digest from the existing multilingual model lock.
The collector never downloads a model, calls cloud inference, starts the Spring
application, or modifies its default model/configuration. Services must be local:
Elasticsearch on port9200, Ollama on port11434, or a dedicated Ollama on11435.
Dedicated Elasticsearch indexes are named `springmind_eval_beir_<dataset>_20261011`;
their `_meta.freeze` owner marker must match before any continuation writes.
They are write-blocked after indexing and are not application knowledge bases.

## Capture and resume

PowerShell (quote Maven properties containing dots):

```powershell
mvn '-Dtest=LiveBeirRun' '-Deval.beir=true' '-Deval.confirmLocalWrites=true' test
```

For the isolated GPU endpoint, add `'-Deval.beirOllamaPort=11435'`.
The opt-in is required because collection creates isolated benchmark indexes.
Use the same index/cache/run directory when resuming, not a changed model or
dataset. Completed captures are verified and skipped. Intermediate embeddings
are checkpointed in ignored `target/beir-cache`, 16 documents/queries per binary
file; partial writes never become completed batches. Changing the dataset or
model requires a separately named experiment, not overwriting these artifacts.

`num_ctx=8192`, `num_batch=8192`, `truncate=false` are explicit. In the installed
Ollama runtime, a default physical batch limit of2048 rejected a2156-token SciFact
document even though the model context is8192. Raising the physical batch capacity
resolved that runtime error; no document was dropped or truncated. This change
was made before retrieval/scoring, without changing the frozen semantic inputs.

The first112 SciFact vectors were encoded on CPU; subsequent vectors used an
isolated GPU container with the same weights mounted read-only. This is not a
latency benchmark. Small floating-point differences across hardware/runtime
versions are possible; the recorded rankings are the reproducible metric inputs.
Do not restart an unrelated service or change the application container to use GPU.

## Offline report and verification

```powershell
mvn '-Dtest=BeirReportRun' '-Deval.beirReport=true' test
mvn '-Dtest=BeirEvaluationTest,RecordedBeirTest' test
```

The report generator refuses to overwrite existing outputs. Run it once after
both captures are complete; use the recorded tests for subsequent verification.
Compressed captures retain each query's official qrels and both top1000 ranked
document IDs/scores, not source text. They can be read using standard gzip tools.
The initial collector's uncompressed files are preserved under ignored cache
paths after lossless compression. The HTML, per-query comparison JSON and capture
hashes are versioned together. Offline tests need no service or raw corpus.

The locally generated `target/beir-review.html` contains source questions and
evidence for symmetric improvement/regression examples. It is deliberately not
committed or redistributed. Generating this optional local view requires the
original downloads. The public report contains IDs and measurements only.

These are retrieval metrics, not answer accuracy, hallucination rates, production
latency or a full vendor reproduction. Official missing labels remain unjudged,
not verified negatives. No new manual labeling is needed to reproduce the
official metrics; source annotations may still be incomplete or noisy.
