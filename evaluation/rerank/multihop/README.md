# MultiHop-RAG fixed-pool Qwen reranking protocol v1

Reuse all 60 frozen questions, official reference labels and recorded keyword /
BGE-M3 ranks from `evaluation/retrieval/results/multihop-2026-10-11`. Formal 50 and
calibration 10 remain separate (44/9 answerable). Labels are official with a partial
user spot-check, NOT exhaustive manual review. No new retrieval, embeddings,
query rewriting, answer generation, question selection or label edits.

## Six paired arms

KEYWORD, VECTOR and RRF, each with reranking off/on. Replay the production
HybridEvidenceRetriever from the saved top50 channel lists: candidate cap30,
RRF k60, contiguous clustering, +/-1 neighbors. The model ranks the assembled
candidate WINDOWS at the existing production reranker boundary, not an invented
new top50 chunk rerank pipeline. The fixed pool can contain fewer than30 windows.
After ranking use the same EvidenceSelector: at most5 windows /4096 tokens.
Every off-context must exactly reproduce the previous baseline before paid calls.

Use the already configured `qwen3-rerank` endpoint
`https://maas.qianwenaiapi.com/compatible-api/v1/reranks` (the user's configured
service, not assumed to have Alibaba official-region pricing). Only query and
candidate evidence previews are sent; no reference answer, fact labels, credentials
in logs or other private repository content. Use the production HttpEvidenceReranker,
including the first4096 Unicode-code-point preview per candidate and original-order
ties. Save input/preview hashes, clipping counts and scores. Record the actual
endpoint/model; the remote alias is not a reproducibly pinned model weight version.

## Budget, latency and failures

At most180 provider attempts (60x3); fewer if a pool has fewer than2 windows.
One sequential attempt per question/arm, no automatic retries. Evaluation-only
timeout10s; application defaults remain unchanged. This isolates ranking quality
better than a tight interactive deadline but is NOT a production-2s-SLA claim.
Timeouts/provider/validation failures retain the original order and remain in
primary metrics as FALLBACK. Stop after3 consecutive fallbacks to investigate;
never replace their saved outcomes just to improve success rate.

Durable intent records are written before requests and results immediately after.
Resume reuses recorded outcomes; an intent without a result is ambiguous and must
NOT trigger another paid attempt automatically. No more than180 intents. No fallback
may be called APPLIED. Candidate IDs must be a full unique permutation. Billed cost
and billed tokens are unknown because the production client discards usage data;
consult the configured service's bill. Preflight token estimates are descriptive,
not actual billing or a monetary cap.

## Metrics and retained evidence

Primary: macro official-fact recall and all-reference-facts coverage in FINAL
production contexts, plus paired improved/tied/worsened cases. Reference-fact
coverage is not answer accuracy, and requiring all official facts may exceed the
minimal evidence needed to answer a yes/no question. Null questions have no recall
denominator and do not yield a refusal score without generation.

Also report full candidate-window fact coverage (an availability ceiling), top5
window fact coverage before budget selection, document coverage, selected tokens,
APPLIED/FALLBACK/SKIPPED counts and request latency p50/p95. Expanded windows may
contain neighbors absent from the original top30 chunk IDs. Keep this distinct from
raw chunk Recall@5. Report bad and good examples, including the four earlier
mutual-complement cases whether or not reranking helps. Do not tune on outcomes.

Keep all original retrieval reports byte-identical. Publish a separate rerank report
with links back to the baseline; detailed candidate text stays under ignored
`target/multihop-rerank-review`. No application settings or indexes change.
