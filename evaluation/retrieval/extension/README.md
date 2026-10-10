# Harborview extension v1: protocol frozen before retrieval

This is a new, fictional corpus, not a replacement for the reviewed Northstar
30-question experiment. All earlier data and results remain unchanged.

## Predeclared design

- 66 documents: 32 short Chinese policies, 32 short English policies, and two
  genuinely long Chinese/English operating handbooks. Real ingestion must yield
  more than 50 READY chunks, with both handbooks split into multiple chunks.
- 24 questions, 12 Chinese and 12 English: six exact-match, six paraphrase, four
  cross-language, two ordinary multi-evidence, two long-document, four unanswerable.
  Six answerable questions require complementary facts, including two cross-language
  and two long-document questions. Language and category slices are predeclared.
- Similar versions, environments and regions have different policies. There are
  no repeated filler copies, invisible query hints or additions based on results.
- The same-language and paraphrase cases are included alongside lexical cases.
  Neither a hybrid win nor a single-channel win is an acceptance criterion.
- Freeze this file and `fixture.json` in Git before any retrieval measurement.
  Do not tune queries, sources, embeddings, weights, cutoffs or labels after seeing
  rankings. Human corrections must be versioned and rescored for all three modes.

## Judgments and measurement contract

The supplied judgments are **provisional**, not human-approved ground truth.
Each question lists its expected answer and evidence facts as document-bound exact
quotes. Quotes locate evidence; they are not injected into retrieval queries.
After real ETL, project those quotes onto actual chunks before issuing questions.
A chunk containing a complete single-answer quote receives grade 3. A chunk with
only part of a complementary answer receives grade 2; if it contains all required
facts it receives grade 3. Other chunks default to 0, and all those defaults need
human review too. Boundary fragments and indirect support may need correction.

The primary comparison uses chunk rankings before clustering/window expansion:
keyword-only, vector-only and equal-weight RRF (constant 60), top 50 per channel,
candidate cap 30, metrics at 5. The raw RRF order is computed from actual adapter
rankings, following production scoring and the chunk-ID tie break. Chunk Hit,
Recall, MRR and nDCG use a common full-corpus judgment set. Duplicate supporting
chunks can affect chunk recall, so additionally report **distinct answer-fact
coverage** and all-required-facts coverage. Unanswerable cases are excluded from
ranking averages, not counted as correct refusals.

For every mode, replay its actual hits through production retrieval and record
the pre-rerank evidence windows and final selected windows. Report distinct fact
coverage at these stages separately, rather than confusing chunk ranking with
evidence-window quality. This is particularly important for long documents and
neighbor expansion. No reranker or answer generator participates.

## Execution and review

Use Java 21, the running local ingestion worker, PostgreSQL, Elasticsearch,
MinIO and local Ollama embedding model. Create a new isolated evaluation account
and group through normal APIs, upload only this fictional corpus, wait for READY,
and validate source/actual-chunk mapping. Do not modify existing groups/documents.
Authentication credentials remain only in ignored local files, never in reports.

All 24 cases and all corpus chunks must appear in the review packet, including
zero grades, no-answer judgments, expected answers and actual source text. Put
annotations before results so reviewers can assess evidence before seeing winners.
Metrics remain provisional until that review is explicitly confirmed.

This is a diagnostic extension, not a representative or statistically established
production benchmark. No parameter sweep, external judge, Qwen call or chat call
is authorized or needed for this experiment.
