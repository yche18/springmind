# Meridian challenge: local single-route, candidate-union and RRF comparison

**Provisional: PENDING_HUMAN_REVIEW.** Open the [review page](review.html) first;
the [metric report](report.html) and comparison.json use unchanged initial labels.
Do not quote these numbers as human-validated project outcomes yet.

## Execution and isolation

- Freeze commit `0494d46`: 96 synthetic documents, 105 production-generated chunks,
  36 questions, initial evidence quotes/grades and exhaustive 3,780 query-chunk
  judgments. Six cases each: identifiers, paraphrase, scope, temporal,
  complementary evidence, unanswerable. 18 Chinese and 18 English questions.
- Tooling commit `b3d172a`: local collection, exact cosine replay, budgeted unions,
  fixed RRF, report and review generation. Both commits preceded collection.
- 282 nonempty local embedding calls: (105 chunks + 36 queries) × 2 models.
  Two additional empty requests unload the models. 72 local lexical searches;
  model metadata/index verification calls are not counted as retrieval queries.
  **No cloud calls, query rewriting, translation, reranking or answer generation.**
- Reused pinned local current model (512d Q8_0) and BGE-M3 (1024d F16), body-only,
  native context limits, truncation disabled; all vectors saved. Ollama 0.33.1,
  Elasticsearch 8.15.3. These are deployment configurations, not an isolated
  comparison of multilingual training alone.
- New index `springmind_eval_meridian_20261011`, 105 chunks, production IK mapping
  and lexical query/rescore, one shard, zero replicas. It is retained with writes
  blocked after refresh. No writes to production indexes, PostgreSQL, old results,
  application data or default configurations. Creation metadata precedes the write
  block; index-snapshot.json records all indexed text before experimental searches.
- Exact vector ranking is recomputed over all saved chunks in Java, not ANN or a
  fresh application ingestion run. Production chunking and lexical query code are
  reused; auth, parsing uploads, neighbor expansion and generation are out of scope.

The freeze hash is in capture.json, along with hashes of vectors, lexical requests,
index snapshot and run metadata. Collection occurred after the freeze, once, with
no score-driven question deletion or label revision. Old Northstar/Harborview
artifacts are unchanged. Offline reproduction needs no running service.

## Provisional ranked metrics

Means cover 30 answerable questions; six unanswerable queries are shown but are
not scored as successful refusals. Fact completeness counts six multi-fact cases.

| Arm | Recall@5 | MRR@5 | nDCG@5 | Recall@30 | Recall@50 | All facts@5 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Keyword body | .8667 | .7178 | .7476 | .8667 | .8667 | 6/6 |
| Keyword body + filename | .8667 | .7261 | .7505 | .8667 | .8667 | 6/6 |
| Current vector | .8667 | .7389 | .7677 | .9667 | .9667 | 6/6 |
| Current + body RRF | .8667 | .7778 | .7967 | .9167 | — | 6/6 |
| Current + filename RRF | .8667 | .7778 | .7967 | .9167 | — | 6/6 |
| BGE-M3 vector | .9833 | .8750 | .9017 | 1.0000 | 1.0000 | 5/6 |
| BGE-M3 + body RRF | .8667 | .7833 | .8002 | 1.0000 | — | 6/6 |
| BGE-M3 + filename RRF | .8667 | .7833 | .8002 | 1.0000 | — | 6/6 |

Body and filename RRF have equal headline metrics, not necessarily identical
full rankings. Filename gives lexical nDCG +.0030, no Recall@5 gain here.
Current-model RRF improves nDCG on c02/c18/c20/c22, regresses on c03, ties 25.
BGE-M3 RRF improves c10/c16/c28, regresses c03/c08/c11/c12/c15/c24, ties 21.
No significance claim is made for these small, correlated samples.

## Candidate coverage, with explicit budgets

U25 combines at most25 per channel; U50 combines at most50 per channel. They are
unordered sets, not ranking algorithms. Counts are means over all36 queries;
recall means exclude six unanswerable cases. “Exclusive” counts are query-chunk
pairs, not unique documents, using each union's own input depth.

| Vector + keyword input | U25 size | U25 recall | U50 size | U50 recall | Vector-only / keyword-only positives at U50 | U50 positives lost at RRF30 / RRF5 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Current + body | 33.44 | .9667 | 54.75 | .9667 | 4 / 0 | 2 / 4 |
| Current + filename | 33.75 | .9667 | 55.00 | .9667 | 4 / 0 | 2 / 4 |
| BGE-M3 + body | 34.50 | 1.0000 | 57.14 | 1.0000 | 5 / 0 | 0 / 5 |
| BGE-M3 + filename | 34.64 | 1.0000 | 57.31 | 1.0000 | 5 / 0 | 0 / 5 |

U25 shares a maximum50 raw-hit budget with each single top50 arm. It matches,
but does not exceed, the corresponding vector-only Recall@50. U50 has a larger
maximum100 budget and must not be advertised as a same-budget win. In this run
neither depth adds a keyword-exclusive initially relevant chunk. There is little
candidate-recall complementarity beyond the vector route at these depths.

Nevertheless, ranking complementarity exists: c28's needed drain condition is
already in BGE-M3's candidate pool at rank8, and keyword ranking helps move it
into the final five. This is a ranking gain, not a previously absent candidate.

## Concrete evidence

- **c28, settlement maintenance:** BGE-M3 ranks drain/resume evidence 8/4.
  Keyword ranks them4/1; either RRF produces5/1. Both facts enter the top5,
  improving all-facts success from5/6 to6/6 across complementary-evidence cases.
- **c10/c16:** delegation and managed-export permission evidence improve from
  BGE-M3 rank2 to RRF rank1. Keyword offers useful ordering information.
- **c08/c11/c12:** cross-language passages rank2/1/1 in BGE-M3 but are absent
  from keyword hits; RRF demotes them to10/19/13. The two-channel agreement on
  other passages outweighs a good single-channel match.
- **c24:** the two alternative passages rank1/2 in BGE-M3, are absent from
  keyword results, and become17/18 after RRF. Both are evidence of the same
  purchase-date rule, so fact coverage needs either, while chunk recall measures
  both. Report both metrics rather than confusing alternatives with two obligations.
- **c03:** the RX-820 battery procedure ranks1 in BGE-M3 and2 in RRF; the RX-802
  cable procedure becomes first. A near-identical identifier is not sufficient
  for relevance when the model/device scope is wrong.
- **c15:** software-subscription approval ranks1 in BGE-M3 but2 in RRF, behind
  the hardware-threshold policy. Shared purchase vocabulary is not applicability.

## Human review needed

The review page exposes all105 chunks and every question's initial grade matrix,
including unreturned chunks. Please confirm expected answers, positive grades,
negative alternatives and no-answer assumptions for all36 questions. Priorities:

1. **c23 / d75--c000:** archived certificate guidance still explicitly applies
   to pre-July15 issuance. Its initial zero may omit an alternative valid source.
2. **c12 / d42--c000:** says settings do not recreate deleted backups but omits
   the independent-export recovery advice. Decide whether partial support merits
   grade1/2 rather than0.
3. **c16 / d55--c000:** states Export Operator download rights but discusses
   Auditor rather than Viewer. Review partial/background relevance.
4. **c24:** confirm both sources independently deserve3 and represent the same fact.
5. **c25–c30:** confirm partial grades2 and whether adjacent sections supply
   additional/alternative support. Do not mark a merely related paragraph positive.
6. **c32/c35:** meeting minutes explicitly say no approval occurred. They cannot
   supply an insurance amount or approved vendor, but may support a refusal.
   Current metrics deliberately do not evaluate refusal-evidence relevance.

These are identified review risks, not silent corrections. No frozen judgment has
been changed. Send question ID, chunk ID, proposed grade and reason, or explicitly
confirm the full packet. review-template.json provides a blank signoff record;
all confirmed fields remain false. Corrections should be versioned separately and
applied equally to every arm without model calls.

## Limits and next interpretation

The challenge introduces confusable identifiers, applicability boundaries,
historical exceptions and split facts, but remains small:105 chunks. A top50 arm
already examines nearly half the corpus. Strong-vector candidate recall therefore
still has a ceiling; this is not a representative large-corpus benchmark. Source
and question prose are synthetic; category balance is intentionally diagnostic,
not a sampled production query distribution. Some long guides are short synthetic
multi-section approximations, not real enterprise manuals.

Initial results do **not** establish that RRF generally fails or that the dataset
must be rewritten until hybrid wins. They locate useful ranking gains and larger
cross-language regressions. First resolve labels; later production-like evaluation
should use independent documents/questions, realistic corpus scale and an untouched
holdout before selecting any fusion weights or default retrieval policy.

## Reproduce offline (Java21)

```powershell
mvn "-Dtest=ChallengeReportRun" test
mvn "-Dtest=ChallengeTest,RecordedChallengeTest,RecordedRetrievalCrossTest,RecordedEmbeddingComparisonTest,RecordedFilenameAblationTest,RecordedExtensionTest,RerankEvaluationTest,RetrievalAblationTest" test
```

Do not rerun ChallengeFreezeRun or LiveChallengeRun over these frozen outputs.
The former refuses to overwrite labels; the latter refuses an existing capture.
Keep the isolated index for diagnosis; this task does not delete any old data.
