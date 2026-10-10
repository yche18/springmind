# Reliable, explainable RAG implementation plan

Branch: `feat/reliable-rag-pipeline`, based on merged `origin/main`.

1. Durable ingestion: transactional enqueue, atomic claims, bounded retries,
   interrupted-job recovery, and serialized rebuilding of partial indexes.
   Commit: `feat(ingestion): persist and recover document ingestion jobs`.
2. Answer traces: query planning and stage timings, authorized source previews,
   and a bilingual expandable evidence panel.
   Commit: `feat(qa): expose retrieval traces and answer evidence`.
3. Evidence selection: optional HTTP reranking with timeout/fallback, overlapping
   evidence deduplication, and a token budget.
   Commit: `feat(rag): add reranking and bounded evidence selection`.

Each feature commit includes its tests and documentation. Validate with Java 21,
the matching business harness, database integration tests for job locking, and
the frontend production build. Do not claim quality gains without measurement.
Cross-language model migration is a separate follow-up.

## Validation record

- Stage 1: Java 21; 24 tests passed, including 9 PostgreSQL migration/queue tests.
  Temporary test database removed by the suite. External model/index calls mocked.
