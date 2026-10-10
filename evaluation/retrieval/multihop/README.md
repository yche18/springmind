# MultiHop-RAG local retrieval diagnostic — frozen protocol v1

Source: Yixuan Tang and Yi Yang, *MultiHop-RAG: Benchmarking Retrieval-Augmented
Generation for Multi-Hop Queries* (COLM 2024), https://github.com/yixuantt/MultiHop-RAG.
Official data: https://huggingface.co/datasets/yixuantt/MultiHopRAG,
revision `71ac0d0bd1f951d2d6b70311f7d2ae404e1ffa82`, ODC-BY 1.0.
The questions are model-assisted constructions over real news, not an entirely
human-authored test set. Official reference labels are NOT yet locally reviewed.
Raw news, vectors and the detailed quotation review stay under ignored `target/`.

## Sampling before retrieval

Retain the full 609-article corpus. Freeze 50 formal questions (16 inference,
17 comparison, 11 temporal, 6 null) and 10 calibration questions (3 inference,
3 comparison, 3 temporal, 1 null). Within each type, allocate evidence-count
strata proportionally using largest remainders. Order candidates by SHA-256 of
`multihop-20261011-v1:<zero-based official row index>`; select formal first.
No retrieval scores are consulted. Skip duplicate normalized questions; prevent
the same nonempty source-document combination and question token Jaccard >= 0.8
from crossing splits. Record all skipped candidates. Do not exclude difficult
questions or unmappable reference facts: flag them for review and conservatively
count an unmapped fact as missing. Freeze source hashes, IDs, original questions,
answers, fact-to-chunk span labels and implementation/configuration hashes before
any retrieval. Do not tune on formal results; calibration is reported separately.

## Fixed retrieval controls

- Original English question only; no query planning, rewriting or cloud calls.
- Production StructureAwareChunkTransformer with development profile settings
  target/max/overlap = 240/320/32 tokens. Chunk the body, then prepend title,
  source and publication time to EVERY chunk for BOTH channels. Use the original
  title as fileName (including the production keyword title boost). No answers,
  queries or reference facts are indexed.
- Production Elasticsearch mapping/query (IK, phrase rescore and fileName boost),
  isolated one-shard index. This is NOT a vanilla English BM25 benchmark.
- Local pinned BGE-M3 from the existing model lock; 1024-dimensional F16 embeddings,
  no truncation, exact cosine search. No pgvector ANN approximation in this diagnostic.
  Neither the default application embedding model nor existing indexes are changed.
- Each channel returns up to 50 chunks. Single-channel order is unchanged; RRF
  uses k=60 with numeric chunk-ID ties. Report top-5, top-30 and top-50 evidence
  coverage; RRF@50 is diagnostic, actual production candidate cap is 30.
- Unordered U25 = keyword25 union vector25 (<=50 candidates), U50 = each top50
  union (<=100). These are coverage ceilings, not ranked systems. U50 has a larger
  candidate budget than either single channel; U25 is the equal maximum-budget check.
- Replay production HybridEvidenceRetriever clustering, +/-1 neighbors and
  EvidenceSelector (top5 windows, 4096 tokens, reranker disabled) using captured
  hits and local source rows. Only storage adapters are replaced by recorded data.

## Metrics and interpretation

A required fact is covered if one complete frozen alternative chunk span is
present. Overlapping chunks are not separate relevant facts. Gold source-document
recall and full-document coverage are secondary, weaker metrics. Report macro
fact recall and fraction with ALL reference facts, channel-exclusive facts/cases,
union-complete cases with neither single channel complete, and actual final-context
fact coverage. Selected spans are taken from production selector metadata.
Unmapped facts stay in the denominator and are prominently listed for manual review.
Null questions have no recall denominator; do not equate retrieved hits or evidence
level with correct refusal. No answer accuracy, faithfulness or end-to-end success
is claimed without generation. A 44-answerable formal subset is exploratory;
one question changes complete-evidence rate by 2.27 percentage points.

The diagnostic does not promise hybrid wins. Retain gains, losses and shared misses.
The 50 formal questions are now exposed for diagnosis: future model/configuration
tuning needs a new held-out confirmation sample, not a claim of untouched testing.
