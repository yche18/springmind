# Harborview: local BGE-M3 versus the current embedding configuration

Open the [Chinese report](report.html) for all question/category/language results,
historical-rank checks, positive evidence positions and actual top-five passages.
The original [human-reviewed annotations](../harborview-2026-10-10/review.html)
are unchanged; no new labeling is required.

## Outcome

BGE-M3 substantially improves this synthetic bilingual corpus, especially the
four cross-language questions. It is not uniformly better: e12 regresses, and
adding equal-weight keyword RRF to BGE-M3 reduces overall top-five quality.

| Mode | Hit@5 | Recall@5 | MRR@5 | nDCG@5 | Recall@30 | All complementary facts@5 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Current model, exact vector | 0.8000 | 0.7750 | 0.7500 | 0.7397 | 0.8500 | 3/6 |
| BGE-M3, exact vector | 1.0000 | 1.0000 | 0.9600 | 0.9613 | 1.0000 | 6/6 |
| Current model + fixed keyword RRF | 0.8500 | 0.8500 | 0.7767 | 0.7863 | 0.9750 | 4/6 |
| BGE-M3 + fixed keyword RRF | 1.0000 | 0.9750 | 0.9167 | 0.9254 | 1.0000 | 5/6 |
| Keyword historical reference | 0.9000 | 0.9000 | 0.8167 | 0.8403 | 1.0000 | 5/6 |

Means use 20 answerable cases. Four unanswerable cases are shown separately and
are not counted as successful refusals. These are raw chunk metrics before
neighbor expansion, not answer accuracy or final-window completeness.

- Model swap, vector-only: Recall@5 **+22.5 percentage points**, nDCG@5 **+0.2216**.
  By per-question nDCG: **7 improve, 1 regresses, 12 are unchanged**.
- Model swap with the same RRF: Recall@5 **+12.5 percentage points**, nDCG@5
  **+0.1391**; **6 improve, 1 regresses, 13 are unchanged**.
- Adding RRF to BGE-M3 alone: **1 improves (e12), 2 regress (e15/e16), 17 unchanged**;
  Recall@5 falls from 1.0000 to 0.9750 and nDCG@5 from 0.9613 to 0.9254.
  No weights were tuned to force a hybrid win.

| Slice | Cases | Current vector nDCG@5 | BGE-M3 vector nDCG@5 | Current / BGE-M3 Recall@5 |
| --- | ---: | ---: | ---: | ---: |
| Exact match | 6 | 1.0000 | 1.0000 | 1.000 / 1.000 |
| Paraphrase | 6 | 0.8770 | 0.8978 | 1.000 / 1.000 |
| Cross-language | 4 | 0.0000 | 0.9799 | 0.000 / 1.000 |
| Multi-evidence | 2 | 1.0000 | 1.0000 | 1.000 / 1.000 |
| Long-document | 2 | 0.7664 | 0.9599 | 0.750 / 1.000 |
| Chinese queries | 10 | 0.7920 | 0.9920 | 0.800 / 1.000 |
| English queries | 10 | 0.6875 | 0.9307 | 0.750 / 1.000 |

## Cases, including failures

- **e13, Chinese query / Seattle English backups**: relevant chunk rank **10 -> 1**.
  This is better ranking of an existing vector candidate, not new raw recall.
- **e14, English query / Shanghai Chinese backups**: **absent from 50 -> 1**.
  Unlike the keyword filename benefit, this arm embeds only the Chinese body,
  so the improvement does not come from English filename aliases.
- **e15, English query / mainland Chinese remote-work rules**: ordinary and
  exception chunks go from **both absent -> 3/1**. RRF moves them to **4/2**;
  both remain in the top five but ranking quality decreases.
- **e16, Chinese query / Bristol English expense policies**: receipt/deadline
  move from **absent/49 -> 2/1**. RRF then moves them to **3/6**, pushing the
  deadline out of the top five. It remains in the 30-candidate pool; this is a
  top-five ranking regression, not loss from all retrieved candidates.
- **e08/e10**, English paraphrases: correct evidence moves **2 -> 1** for both.
- **e19**, long restoration handbook: approval/retention chunks move **7/1 -> 2/1**.
  Raw top-five evidence becomes complete. The older window experiment already
  recovered the missing approval chunk through neighbors, so do not claim a
  newly complete final answer context here.
- **e12, the model regression**: confirmed supporting chunk
  `notify-urgent-en--c001` moves **1 -> 5** (nDCG 1.0000 -> 0.3869).
  BGE-M3 ranks its neighboring `c000` first, then routine notification, remote
  work and priority shipping passages before `c001`. The first fragment concerns
  the same urgent notification but lacks the confirmed answer clause. The
  existing owner-approved zero grade for that fragment was not changed to favor
  the new model. RRF promotes `c001` to **2**, a partial recovery (nDCG 0.6309),
  still below the current model's first place. Chunk boundaries and relevance
  granularity are material to interpreting this failure.

## Local execution and controls

- Protocol committed before measurement: `30560c1`; pinned model/capture tooling:
  `4566f6c`. This is the first and only measured run, with no result-selected retries.
- Downloaded `bge-m3:567m` (1,157,672,605 bytes), F16, 1024 dimensions, digest
  `7907646426070047a77226ac3e684fbbe8410524f7b4a74d02837e43f2146bab`.
  The current model remains `qllama/bge-small-zh-v1.5:latest`, Q8_0, 512 dimensions.
  Both exact versions are in the [model lock](../../multilingual-embedding/model-lock.json).
- The host has a 6GB RTX 3060 Laptop, but the existing Ollama container has no
  GPU device request. Actual `/api/ps` reports **size_vram=0 for both models**:
  this completed on CPU, without changing Docker configuration.
- Local Ollama 0.33.1 and pgvector 0.8.6. Each model encoded the same 105 bodies
  and 24 questions sequentially: **258 nonempty embedding requests total**, plus
  two empty-input unload requests. **Zero cloud/chat/reranker requests**.
- Body-only inputs, no filename, query rewriting, translation or instructions.
  Truncation was disabled: max observed tokens were **313/512** for current and
  **208/8192** for BGE-M3, so neither model needed truncation.
- Each model was unloaded after encoding. Saved runtime metadata reported model
  allocation approximately 33.5 MB (current) and 1.219 GB (BGE-M3), not peak
  whole-process/container memory.
- Current 129-input embedding request time summed to **2.383 s**; BGE-M3 **18.748 s**.
  Query-embedding medians were **9.71 ms / 66.56 ms** respectively. These are one
  local sequential run including HTTP overhead (total includes first-load cost),
  not a throughput benchmark, answer latency or production P95.
- Created only schema `retrieval_eval_bgem3_20261010`, with `current_vectors`
  (512D) and `bge_m3_vectors` (1024D), **105 rows each**. No vector ANN indexes;
  both use exhaustive PostgreSQL cosine ordering with chunk-ID tie breaks.
  Independently scoring all 105 vectors in Java verified all **48 SQL top-50
  results**, with a 1e-6 numerical/tie tolerance.
- Fresh current-model exact rankings match the historical vector top 50 in
  **24/24 questions, every rank**. Its aggregate metrics also reproduce exactly.
  Thus there is no observed rank-level ANN/runtime drift explaining the measured
  gains in this run; this is not a general proof that approximate search is exact.
- RRF uses the original filename-enabled keyword results, unchanged constant 60,
  equal weights and candidate cap 30. No Elasticsearch queries or Qwen calls
  were needed. Hybrid Recall@50 is intentionally null because its pool ends at 30.
- Default settings, production code, `public.vector_store`, all original documents,
  labels and previous result directories were not modified. The downloaded model
  and isolated evaluation tables are retained for inspection; no data was deleted.

## Reproduction and interpretation

`current-vectors.json` and `bge_m3-vectors.json` preserve every document/query
vector and per-input diagnostics. `capture.json` binds their hashes plus the
fixture, corpus, approval, protocol, lock and original-capture hashes. Capture
SHA-256: `80738762128e53da1c02a3c9e9b3d489b6d4e8f7e0d0507c5dd7128c34d6c98a`.
`comparison.json` and the HTML report recompute fully offline from these files.

```bash
# Java 21; no database, model or cloud service is needed.
mvn "-Dtest=EmbeddingComparisonReportRun" test
mvn "-Dtest=EmbeddingComparisonTest,RecordedEmbeddingComparisonTest" test
```

This supports BGE-M3 as a promising configuration for this bilingual scenario,
not an automatic production migration. Model size, architecture, precision and
dimension all differ; the gain cannot be attributed solely to multilingual
training. The 24-question synthetic set has already been examined, so it is not
a held-out production benchmark. Recall@5=100% refers to the 20 answerable cases
and confirmed chunk labels, not perfect answer quality or refusal behavior.
The default model has deliberately not been changed.
