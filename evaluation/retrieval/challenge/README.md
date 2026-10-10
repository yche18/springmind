# Meridian challenge v1: preregistered local retrieval experiment

This is newly authored **synthetic**, realistic-format material, not production
traffic or an unbiased estimate of production quality. It tests failure modes,
not a preselected winner. Preserve Northstar, Harborview and all earlier results.

## Freeze before measurement

Freeze fixture.json (documents, filenames, questions, evidence quotes, grades),
corpus.json (production structure-aware chunks and exhaustive initial labels),
this protocol, the existing local model lock and relevant production retrieval
source hashes in freeze.json. Commit the freeze before any embedding or retrieval
of these questions. No question removal, quote changes or tuning after scores.
Initial judgments are PENDING_HUMAN_REVIEW, including implicit negatives, not
human-approved. Human corrections must be separately recorded and all arms rescored.

The 36 questions are balanced across six diagnostic categories: identifiers,
paraphrase, scope, temporal, complementary-evidence, unanswerable (six each).
Materials mix runbooks, incident records, release notes, policy exceptions and
longer multi-section guides. Similar identifiers, old procedures, different
regions, environments and tenant plans are deliberate plausible distractors.
They are not chosen using retrieval outputs. Some questions share documents;
they are correlated diagnostic cases, not an independent held-out test set.
No difficulty target or minimum hybrid gain is required.

Use production StructureAwareChunkTransformer with its 500/800/80 defaults.
Evidence quotes are projected before retrieval: exact normalized quote containment
in a chunk gives the specified grade; all other chunks initially grade 0.
Grade 3 = sufficient to answer the complete question; grade 2 = necessary partial
support; grade 1 = useful but insufficient context if a reviewer identifies it.
Historical or wrong-scope passages are 0 unless explicitly required by the question.
Each quoted fact must survive chunking; fail rather than silently lose labels.
The review page must expose all chunks, quotes, grades and potential missing
positives, not only passages that a retriever happened to find.

## Fixed arms and budget

- CURRENT and BGE_M3 use existing pinned local Ollama models, native context
  lengths, raw queries and chunk bodies only. No rewrite, translation, prompts,
  sparse model outputs, cloud embedding or reranker. Fail on truncation.
- Exact cosine search scores every saved chunk, sorts by score then numeric chunk
  ID, and retains 50. This is a retrieval-component experiment, not application
  ingestion, ANN, auth, neighbor-window or answer-generation benchmarking.
- A fresh dedicated local Elasticsearch index uses the production IK mapping and
  lexical query/rescore implementation; one shard, zero replicas, immutable after
  refresh. Both BODY_ONLY and BODY_AND_FILENAME are captured. Never write the
  production index or any existing evaluation index. Keep the index after capture.
- Equal-weight RRF: constant 60, at most 50 per channel, cap 30, numeric chunk-ID
  ties, evaluate top 5. Two vector models × two keyword inputs = four RRF arms,
  plus two vector and two lexical single arms. No tuning.
- Candidate unions are **unordered sets**: U25 = vector top25 ∪ keyword top25
  (at most50) compared with single top50 under the same maximum raw-hit budget;
  U50 = vector top50 ∪ keyword top50 (at most100) is a larger-budget diagnostic,
  not a fair top50 ranking baseline. Record actual sizes and duplicates.
  Never assign MRR/nDCG or top5 to an arbitrary union order.

## Metrics and diagnosis

For ranked arms: Hit/Recall/MRR/nDCG@5, Recall@30/50 (RRF @50 unavailable),
distinct fact coverage@5 and all-required-facts@5. For each union: recall and
fact coverage, actual candidate size, exclusive relevant contributions of each
channel; U50→RRF30→RRF5 evidence loss. Compare U25 against both single top50
baselines. Separate language/category summaries and retain wins/losses/ties.
Unanswerable questions are excluded from recall/ranking means; returned hits do
not count as correct refusals. No answer-accuracy or significance claims.

Inspect whether additional relevant candidates exist before blaming fusion:
no extra positives means little recall opportunity; positives lost by RRF means
a ranking/capping problem. Report both outcomes without selecting favorable cases.

## Isolation and reproducibility

Only loopback Ollama (:11434) and Elasticsearch (:9200) are permitted; do not
start Spring, query production data or invoke hosted endpoints. Save vectors,
model digests, versions, exact lexical requests, index snapshot and source hashes.
Persist each completed model before proceeding; refuse to overwrite captures.
On failure retain artifacts and resume only verified completed stages. An offline
test must reconstruct vector rankings, unions, RRF, metrics and HTML from captures.
The collection's small size and artificial balance must remain prominent.

Java 21 entry points: ChallengeFreezeRun (before measurement), LiveChallengeRun
(explicit eval.challenge and eval.confirmLocalWrites opt-ins), ChallengeReportRun
(offline), ChallengeTest and RecordedChallengeTest (offline checks).
