# Reranking and evidence budgets

The retrieval pipeline fuses vector/keyword hits with RRF, validates READY and
group-scoped database rows, expands source windows, optionally reranks them, and
selects at most five evidence items by default. Up to 30 fused chunk candidates
are considered before clustering (`qa.evidence.candidate-limit`, range 5..50).

Evidence selection is enabled by default with a 4096-token budget. It counts the
actual joined evidence text, including evidence IDs, filenames and separators,
with Spring AI's JTokkit `cl100k_base` estimator. The question, system prompt,
output and chat framing are outside this evidence-only budget. Providers using
other tokenizers may report different usage; these counts are not billed usage.

Selection retains the primary hit and adds complete contiguous neighboring
chunks while space remains. Previously used chunk identities and normalized
duplicate content are excluded; exact suffix/prefix overlap of 16..512 characters
between adjacent chunks is collapsed. Oversized primary chunks are skipped,
not silently truncated. Empty selection follows the existing refusal path.
Source ranges and retrieval-channel coverage are recomputed for retained chunks.
References to distinct documents with the same filename remain distinct.

## Optional reranker

Set these environment variables (also forwarded by Docker Compose):

```dotenv
QA_EVIDENCE_MAX_TOKENS=4096
RERANK_ENABLED=true
RERANK_URL=http://host.docker.internal:8081/rerank
RERANK_MODEL=your-reranker-model
RERANK_API_KEY=
```

Use an endpoint you operate or trust to receive the question and candidate source
previews. Nothing is sent to a reranker when disabled (the default). This repository
does not download or start a reranking model. The example URL is illustrative;
configure a reachable endpoint. Model and API-key fields may be empty for local
services that do not require them.

Request JSON has `query`, `documents` (strings), `top_n` (all candidates), and an
optional `model`. Each preview is capped at 4096 Unicode code points. Response:

```json
{"results":[{"index":1,"relevance_score":0.91},{"index":0,"relevance_score":0.32}]}
```

Every input must have one finite score and a unique valid integer index. Returned
document text is ignored. Invalid JSON, partial results, duplicate indices, HTTP
errors and timeouts preserve the full RRF ordering. Ties preserve original rank.
Redirects are disabled. `qa.evidence.rerank-timeout` defaults to two seconds and
bounds the complete response, including its body. No automatic network retry is
performed on the QA request path.

The answer trace reports APPLIED / DISABLED / SKIPPED / FALLBACK, before/after
ranks, RRF and reranker scores separately, and candidate/selected token counts.
Scores are not probabilities. No measured accuracy or latency improvement is
claimed without a representative evaluation dataset.

## Validation

`EvidenceSelectorTest` covers shared budgets, Chinese/English/emoji text, overlap,
source ranges, primary-hit retention and deduplication. `HttpEvidenceRerankerTest`
uses a local HTTP fixture for ordering, malformed outputs, provider errors,
header/body timeouts and disabled mode. Run with Java 21:

```bash
mvn -Dtest=EvidenceSelectorTest,HttpEvidenceRerankerTest,HybridEvidenceRetrieverTest,QaRetrievalHarnessTest test
```
