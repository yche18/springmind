# Harborview extension: provisional three-way retrieval comparison

**All new annotations are pending human review.** These numbers are diagnostic,
not approved benchmark conclusions. Start with the [Chinese review packet](review.html),
then open the [three-way comparison](report.html). The original Northstar 30 questions,
labels and measured results are unchanged.

## What was frozen and run

- New fictional corpus: 66 documents (33 Chinese, 33 English), 24 questions
  (12 Chinese, 12 English), 20 provisionally answerable and four unanswerable.
- Predeclared categories: six exact-match, six paraphrase, four cross-language,
  two multi-evidence, two long-document and four unanswerable questions. Six
  answerable cases require two complementary facts, including cross-language/long cases.
- First fixture/protocol commit: `319f581`. The offline preflight found one
  semicolon boundary in e12. Its evidence locator was shortened to the complete
  yes/no answer clause before any retrieval; no query or source text changed.
  That correction and the tools were committed as `3c8bfde`, still before measurement.
- Final pre-retrieval fixture SHA-256:
  `279421dc7e5339e929111e92c075ec12caea8ae33fbfd335b9249a58e7cc5a6c`.
- Created only the dedicated `harborview-45c7f7c2` evaluation account/group 3,
  documents 19–84. Real authenticated upload, MinIO, durable ETL, PostgreSQL,
  Elasticsearch and local Ollama were used. Existing groups/documents were untouched.
- Ingestion produced **105 READY chunks** (37 Chinese, 68 English). The English
  restoration handbook has 18 chunks; the Chinese release handbook has five.
  Some short English documents also split under the current production chunker.
- All chunks and quote-projected judgments were saved before the first query at
  `2026-10-10T12:11:15.575427400Z`; capture completed at
  `2026-10-10T12:11:22.804516100Z`. This is the first retrieval run, with no tuning,
  retries selected by performance, or annotation changes after seeing ranks.
- Every query uses the original wording. Each real channel is called once (limit
  50); exactly those hits are replayed through production retrieval with one or
  both inputs enabled. Equal-weight RRF constant 60, candidate cap 30, neighbor
  window 1, evidence budget 4096, cutoff 5. No production settings were changed.
- Embedding: `qllama/bge-small-zh-v1.5:latest`, digest
  `9a2067741c2aacd74a623b0e09b40ab437bc4a5ebe9cd59a776376e414931c89`.
- **Zero new Qwen/chat requests.** Paid model beans were replaced and verified
  unused. ETL and query embeddings used the local Ollama model. There is no
  reranker or answer-generation arm in this extension.

## Provisional chunk-ranking results

Metrics here refer to **chunks before clustering and neighbor expansion**, not
the final evidence windows. All modes use a full-corpus judgment set and a
30-chunk candidate cap. Means exclude the four no-answer cases. Overlapping
supporting chunks are distinct for chunk metrics but deduplicated for fact coverage.

| Strategy | Hit@5 | Recall@5 | MRR@5 | nDCG@5 | Candidate chunk recall | Complementary facts complete@5 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Keyword only | 0.9000 | 0.9000 | 0.8167 | 0.8403 | 1.0000 | 5/6 |
| Vector only | 0.8000 | 0.7750 | 0.7500 | 0.7397 | 0.8500 | 3/6 |
| Hybrid RRF | 0.8500 | 0.8500 | 0.7767 | 0.7863 | 0.9750 | 4/6 |

On this initial annotation version, **keyword-only wins overall**. Hybrid improves
Recall@5 by 7.5 percentage points and nDCG@5 by 0.0466 over vector-only, but is
5 percentage points below keyword-only in Recall@5 and 0.0540 below in nDCG@5.
Against vector-only, nDCG improves on e10/e13/e19, regresses on e08, and is unchanged
on the other 16 answerable questions. Do not describe this as a universal hybrid win.

## Distinct answer-fact coverage through the pipeline

Fact coverage is the fraction of required annotated facts supported by a set of
chunks/windows, averaged over 20 answerable questions. Each fact counts once even
if overlapping chunks repeat it. These values are not answer-generation accuracy.

| Strategy | Raw channel hits (each ≤50) | Candidate chunks ≤30 | Top 5 chunks | Top 5 pre-rerank windows | Finally selected windows |
| --- | ---: | ---: | ---: | ---: | ---: |
| Keyword only | 1.0000 | 1.0000 | 0.9000 | 0.9000 | 0.9000 |
| Vector only | 0.8750 | 0.8500 | 0.7750 | 0.8000 | 0.8000 |
| Hybrid RRF | 1.0000 | 0.9750 | 0.8500 | 0.8500 | 0.8500 |

Hybrid's raw hit union recovers facts missing from the vector top 50, unlike the
old 16-document experiment where vector already returned the entire corpus.
However, fusion and the top-30 cap can still discard a keyword-only fact (e16).
Higher candidate coverage does not automatically imply better top-five ranking.

The production window pipeline can also recover a fact via a neighbor, so a raw
chunk miss must not be reported as a missing final answer context (e19 below).

## Representative cases, including failures

### e08: semantic paraphrase, hybrid regression

The Bristol employee asks whose permission is needed to work from another city
for a month. `remote-exception-en--c000` has provisional grade 3. Its ranks are
vector 2, keyword 13, hybrid 3. Adding lexical evidence promotes unrelated routine
notification material ahead of it. nDCG@5 falls from 0.6309 to 0.5000, although
the required fact remains in the top five and selected context for both modes.

### e10: receipt paraphrase, hybrid improvement

The receipt requirement ranks second in vector search and first in keyword search.
Hybrid puts it first, improving nDCG@5 from 0.6309 to 1.0000. All modes still cover
the needed fact in their top five. This is a ranking improvement, not new recall.

### e13: Seattle retention, top-five promotion

The Chinese query needs the English Seattle policy. The relevant chunk ranks
vector 10, keyword 2, hybrid 5, so hybrid repairs this top-five miss. This particular
case is promotion of an existing vector candidate, not recovery from outside top 50.

### e14–e16: candidate recovery is not sufficient

- e14 (English query / Chinese Shanghai policy): the supporting chunk is absent
  from vector's top 50, keyword rank 2, hybrid rank 8. The keyword channel genuinely
  adds a missing candidate, yet the final top five still lack it in hybrid.
- e15 (English query / mainland remote rules): both required Chinese chunks are
  absent from vector's top 50. Keyword places them at 3/5, hybrid at 24/27. Fusion
  preserves them in the candidate pool but loses the top-five coverage available
  from keyword-only retrieval.
- e16 (Chinese query / Bristol expense rules): the receipt is absent from vector
  top 50 and keyword rank 19; the deadline is vector rank 49 and keyword rank 25.
  Hybrid retains the deadline at 27 but excludes the receipt from its top 30.
  None of the three modes covers these facts in the top five on this question.

These are clear reasons not to conclude that RRF always dominates its inputs.

### e19: long-document neighbor context changes the interpretation

The Atlas restore needs approval roles and approval-record retention, located in
separated handbook chunks `c004` and `c016`. Vector ranks them at 7/1; hybrid at
3/1; keyword at 1/2. Chunk Recall@5 rises from 0.5 to 1.0 from vector to hybrid.
However, production neighbor expansion already includes `c004` in the vector
context via nearby hits. **All three finally selected contexts cover both facts.**
Claiming that hybrid alone makes the final context complete would be incorrect.

## Review checklist for the owner

Please review all 24 cases using [review.html](review.html). No JSON editing is
required: report the question ID, the chunk/source ID, the proposed grade or
corrected expected answer, and the reason. The template exposes all **2,520**
question/chunk grades (24 × 105), including implicit zeros.

Priority checks (not a substitute for reviewing the remaining cases):

1. e12: the initial locator spans a semicolon boundary. The narrowed clause directly
   answers the yes/no question; consider whether adjacent `notify-urgent-en--c000`
   also deserves direct-support or background credit instead of zero.
2. e13–e16: verify region/language scope. Similar policies for a different site
   must not become positive just because their wording is close.
3. e19/e20: verify both required facts, the grades of neighboring fragments and
   whether any other chunk supplies sufficient/partial support. A whole-document
   label must not automatically apply to every chunk of a handbook.
4. e21–e24: confirm absence across the whole corpus, especially the unknown error
   code and software refund question with near-matching but inapplicable policies.
5. All other positive and zero grades: quote matching is only an initial annotation
   aid, not proof that unquoted paraphrases or background chunks are irrelevant.

Do not change labels to favor any strategy. If corrected, preserve this initial
version and rescore the same raw results for all three modes, without Qwen.

## Limits of this experiment

- Although questions and documents are language-balanced, chunk counts are not
  (37 Chinese / 68 English) under the current chunker. This is one corpus and one
  embedding configuration, not a comparison of all vector models.
- The production keyword adapter includes filename matching/boosting, phrase
  matching and Elasticsearch query rescoring; it is **not a bare text-only BM25
  baseline**. The descriptive filenames are visible corpus metadata and contain
  English terms, including region names for Chinese documents. This can help
  cross-language lexical retrieval (for example Shanghai). Do not generalize that
  keyword retrieval understands cross-language semantics from these results.
- Exact-term cases are all solved by all three modes in this sample. The largest
  remaining failures are cross-language cases under this configuration. No weights,
  analyzers, embeddings or labels were adjusted after this observation.
- The old Northstar experiment ranked one-window-per-document candidates; this
  extension separates raw chunk ranks from window fact coverage. **Do not merge
  the two experiment averages or compare their absolute scores as a progress curve.**
- Only 20 answerable cases contribute ranking means. The data are synthetic,
  provisional and not a statistically established held-out production benchmark.
- Timings are single-pass local diagnostics, not end-to-end P95 or a production SLA.
  No answers were generated; no refusal/citation accuracy was measured.

## Reproduce offline and inspect artifacts

```bash
mvn "-Dtest=ExtensionReportRun" "-Deval.extensionOutput=evaluation/retrieval/results/harborview-2026-10-10" test
mvn "-Dtest=ExtensionFixtureTest,ExtensionEvaluationTest,RecordedExtensionTest" test
```

Use Java 21. The report/tests need no services or credentials.

- `corpus.json`: actual READY text/identities and all initial judgments, frozen before queries.
- `capture.json`: real raw channel ranks/scores, three-mode raw candidate orders,
  production candidate/selected windows, timings and model provenance.
- `comparison.json`: provisional aggregates, language/type slices, per-case metrics,
  and input hashes.
- `review-template.json`: all 24 pending review entries with all chunk grades.
- `review.html`: annotation-first Chinese review packet, full source texts and actual chunks.
- `report.html`: Chinese results, every case and actual selected evidence.

The private account file and manifest remain only under ignored
`evaluation/rerank/local/harborview-45c7f7c2/`. The isolated group and its documents
remain available for inspection; no user data was deleted or replaced.
