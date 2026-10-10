# Frozen Harborview multilingual embedding comparison

Use the unchanged, human-reviewed Harborview fixture (66 documents, 105 actual
chunks, 24 questions). Do not change defaults, source data, original indexes,
questions, labels, chunk boundaries or earlier results. No cloud inference.

## Declared arms and controls

- `CURRENT_VECTOR`: local `qllama/bge-small-zh-v1.5:latest`, 512 dimensions,
  Q8_0. Preserve the production raw-query/body-only convention.
- `BGE_M3_VECTOR`: local `bge-m3:567m`, 1024 dimensions, F16. Use only dense
  embeddings, with raw queries and body text; no sparse/ColBERT scoring.
- `CURRENT_HYBRID` and `BGE_M3_HYBRID`: offline equal-weight RRF (constant 60)
  of each freshly measured vector arm and the same previously captured original
  filename-enabled keyword hits; at most 30 candidates, evaluate at five.
- `KEYWORD_REFERENCE`: those unchanged keyword hits, not a new ES search.

Encode all 105 document bodies and 24 original questions for each model locally,
one text at a time, without filenames, translation, rewrite, instruction tuning
or reranking. Pin tags, model digests, quantization and output dimensions in
`model-lock.json` before measurement. Sequence model loads to reduce peak memory.
Use each model's native context length (512 / 8192); fail on truncation rather
than silently changing evaluated text. Validate dimensions, finite values and
nonzero norms. Save embeddings for fully offline reproduction.

Store each model's 105 vectors in its own table in the new dedicated PostgreSQL
schema `retrieval_eval_bgem3_20261010`. Never write `public.vector_store` or run
application startup/migrations/ingestion. Refuse to reuse an existing schema.
Use the same exhaustive cosine-distance SQL ranking for both models, with chunk
ID tie breaks, top 50 and then top-30 candidate caps. Do not compare fresh exact
search with the historical approximate arm as if only the model changed.
Independently recompute all 105 cosine scores in Java and verify each SQL top-50
result, allowing only numerical differences/ties within 1e-6. This removes ANN
candidate omission as a confound in the primary model comparison.

Keep the historical vector results as a separate diagnostic reference. Report
fresh-current versus historical rank overlap and relevant-rank changes; do not
attribute a difference solely to HNSW because query execution/model runtime may
also differ. Primary ranking/fusion metrics always use fresh matched exact arms.

Report Hit/Recall/MRR/nDCG@5, candidate recall@30, raw recall@50, distinct fact
coverage and all-required-facts@5, with language and predeclared category slices.
Twenty answerable questions contribute means; four unanswerable cases are not
successful refusals. Preserve all wins, ties and regressions and exact source
references. These are raw chunk rankings, not expanded windows or answer quality.
Record per-input embedding time and observed resource use as local diagnostics,
not production P95 or an SLA. Persist successful per-model embeddings immediately
and retain incomplete attempts; never rerun selectively to obtain better scores.

This compares deployment configurations, not multilingual training alone: size,
architecture, precision and dimensionality differ. It is a small already-inspected
synthetic set, not a held-out production benchmark or a claim that BGE-M3 must win.

## Execution (Java 21)

```bash
mvn "-Dtest=EmbeddingComparisonTest" test
mvn "-Dtest=LiveEmbeddingComparisonRun" "-Deval.embedding=true" "-Deval.confirmLocalWrites=true" test
mvn "-Dtest=EmbeddingComparisonReportRun" test
mvn "-Dtest=EmbeddingComparisonTest,RecordedEmbeddingComparisonTest" test
```

The live runner uses only local Ollama HTTP and local JDBC. It reads datasource
settings from `application-dev.yml` without starting Spring or exposing secrets.
Only downloading the public BGE-M3 weights needs Internet access. No Qwen, chat,
reranker or hosted embedding requests are made. Default report directory:
`evaluation/retrieval/results/harborview-bgem3-2026-10-10`.
