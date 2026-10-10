# Harborview filename-field ablation

Open the [Chinese comparison report](report.html) for language/category slices,
all 24 questions, positive-evidence ranks, actual top-five texts and both query
bodies. Labels were already [confirmed by the owner](../harborview-2026-10-10/review-approval.json);
no new labeling is required and no labels were changed.

## Outcome

On this reviewed synthetic corpus, including filenames improves keyword-only
retrieval. The effect is concentrated in three cross-language questions and one
English paraphrase, not a uniform improvement across every type of question.

| Raw chunk metric | Body only | Body + filename | Difference |
| --- | ---: | ---: | ---: |
| Hit@5 | 0.7500 | 0.9000 | +15 percentage points |
| Recall@5 | 0.7500 | 0.9000 | +15 percentage points |
| MRR@5 | 0.7250 | 0.8167 | +0.0917 |
| nDCG@5 | 0.7315 | 0.8403 | +0.1087 |
| Chunk Recall@30 | 0.9000 | 1.0000 | +10 percentage points |
| Chunk Recall@50 | 0.9000 | 1.0000 | +10 percentage points |
| Distinct fact coverage@5 | 0.7500 | 0.9000 | +15 percentage points |
| All complementary facts@5 | 4/6 | 5/6 | +1 question |

Means use 20 answerable questions; four no-answer questions are excluded, not
counted as successful refusals. By per-question nDCG@5, adding filenames improves
**4**, regresses **0**, and leaves **16** unchanged. This says nothing about
changes among irrelevant hits or ranks below the metric cutoff.

| Category | Questions | Body-only nDCG@5 | Body + filename nDCG@5 |
| --- | ---: | ---: | ---: |
| Exact match | 6 | 1.0000 | 1.0000 |
| Semantic paraphrase | 6 | 0.7718 | 0.8333 |
| Cross-language | 4 | 0.0000 | 0.4514 |
| Multi-evidence | 2 | 1.0000 | 1.0000 |
| Long document | 2 | 1.0000 | 1.0000 |

Cross-language Recall@5 is **0% versus 75%**. Thus the prior keyword arm's
top-five success on these cross-language cases depends on filename matching,
not on keyword search learning cross-language semantics. Removing filenames
does not make the current vector model's cross-language failures disappear.

## Concrete cases: body-only rank -> filename-enabled rank

- **e10**, English expense paraphrase: `expense-receipt-en--c000` moves **2 -> 1**.
  This is an ordering improvement, not new top-five recall.
- **e13**, Chinese question about Seattle backups: `backup-seattle-en--c000`
  moves **9 -> 2**. The answer was already recalled but outside the top five.
- **e14**, English question about Shanghai backup retention: the Chinese
  `backup-shanghai-zh--c000` goes from **absent -> 2**. The body-only query returns
  only eight hits, so this was not merely pushed past a 50-hit cutoff. The
  descriptive filename supplies English `backup` / `shanghai` aliases for a
  body written in Chinese.
- **e15**, English question about mainland remote work: Chinese ordinary and
  exception policies go from **both absent -> ranks 5 and 3**, respectively.
  Body-only returns 27 hits, versus 29 with filenames; both required Chinese
  chunks are new matches, and both enter the top five.
- **e08** still fails top-five recall: the month-long remote exception remains
  rank **13 -> 13**. Filename matching does not fix this paraphrase failure.
- **e16** still fails top-five recall: Bristol receipt/deadline evidence remains
  **19/25 -> 19/25**. File metadata is not a complete cross-language solution.
- **e19/e20** retain the same positive ranks (1/2); filename matching does not
  explain the keyword channel's long-document success on this sample.

## Controls and provenance

- [Protocol](../../filename-ablation/README.md) and query-difference checks were
  written before live retrieval. This is the first live run; no result-driven
  retries, parameter sweeps, source changes or relabeling were performed.
- Reused group 3, 66 documents and all 105 real Elasticsearch chunks, validated
  by document/chunk identity, exact text, filenames and active status. No new
  account, upload, ingestion, database write or index modification was needed.
- Both arms use a single Elasticsearch point-in-time view. Only `fileName`
  match and phrase clauses are removed, from both the initial query and rescore.
  All body boosts, filters, analyzers, rescore settings, queries and top-50
  limit remain unchanged. This is not a plain text-only BM25 implementation.
- Exactly 48 experimental searches, alternating arm order by question, between
  `2026-10-10T12:40:10.579277300Z` and `2026-10-10T12:40:12.031772200Z`.
  The temporary search view was closed. **Zero Qwen/chat/embedding requests.**
- Fresh filename-enabled results reproduce **24/24 original complete keyword
  rankings and scores exactly**, including no-answer cases. No observed baseline
  drift confounds the comparison.
- `capture.json` stores both real hit lists, scores, query bodies, index metadata
  and fixture/corpus/approval/protocol/original-capture hashes. Its SHA-256 is
  `cccefe1e6f4f30f48669cae29171a6cf1eaf5de135cb6c19d790b321bac8fcb6`.
- `index-snapshot.json` preserves all 105 validated indexed source records,
  saved before experimental queries; SHA-256
  `84839a0dc3c09a90d7c08f7fb540a571d2ed5a0f7f307a924254be555a01fca2`.
- `comparison.json` and `report.html` recompute offline from those fixed inputs.
  A JSON integer-width mismatch in the offline request comparison was repaired
  without changing or recollecting the live capture; round-trip regression tests
  cover both in-memory and parsed requests.
- All old Northstar and Harborview artifacts are retained unchanged. The run
  changes no production code, retrieval settings or default strategy.

## Interpretation and boundaries

Keeping useful filename metadata has measured value for the current system and
these documents. It is not inherently unfair for a production retriever to use
metadata, but comparisons must disclose that the vector arm embeds body text
while the keyword arm can exploit English filename aliases.

For context only, the already-recorded body-vector nDCG@5 was 0.7397, close to
body-only keyword 0.7315; their Recall@5 values were 0.775 and 0.750. Those are
historical vector results, not a fresh vector run or evidence of statistical
superiority. This experiment does not measure a body-only hybrid/RRF variant,
another embedding model, reranking, generated answers or refusal behavior.

The dataset has only 20 answerable synthetic questions. Descriptive filenames
are unusually consistent and may be less useful or even misleading in other
collections. The absence of observed regression establishes neither universal
safety nor general superiority. Do not redesign labels or tune weights to force a winner.
No further human annotation is needed for this unchanged corpus.

## Offline reproduction (Java 21)

```bash
mvn "-Dtest=FilenameAblationReportRun" test
mvn "-Dtest=FilenameAblationTest,RecordedFilenameAblationTest" test
```
