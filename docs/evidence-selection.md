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
RERANK_TIMEOUT=2s
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

## Qwen hosted setup

For the Qianwen platform's OpenAI-compatible **qwen3-rerank** API, use the full
operation URL, not a chat base URL:

```dotenv
RERANK_ENABLED=true
RERANK_URL=https://maas.qianwenaiapi.com/compatible-api/v1/reranks
RERANK_MODEL=qwen3-rerank
RERANK_API_KEY=your-key-from-the-matching-platform
RERANK_TIMEOUT=2s
```

Follow the endpoint shown in your own workspace's API documentation. Other Alibaba
Cloud workspaces can have different regional/workspace endpoints and credentials.
Do not mix platform keys and endpoints. `/compatible-mode/v1` is a chat base URL,
not this rerank operation. This adapter expects the flat request and `results`
response above; choosing another model does not make a different API protocol
compatible. See the [Qianwen rerank API](https://platform.qianwenai.com/docs/api-reference/rerank/openai-rerank)
and [Alibaba Cloud rerank API](https://help.aliyun.com/zh/model-studio/text-rerank-api).

Keep credentials in the ignored `.env` file or your deployment's secret store.
After changing Compose environment variables, recreate the backend:

```bash
docker compose up -d --force-recreate backend
docker compose logs --tail=100 backend
```

Ask a question with multiple candidate sources. Confirm the answer trace says
`APPLIED` (the UI's successful reranking status), not just that reranking has a
nonzero duration: failed calls also take time. `SKIPPED` means fewer than two
candidates, and `DISABLED` means reranking is off. Inspect before/after source
ranks to see what changed; a successful call may legitimately keep the same order.

On `FALLBACK`, the backend preserves RRF order and logs a safe reason:

| Reason | Check |
| --- | --- |
| `AUTHENTICATION` / 401 | Key validity and the platform/region that issued it |
| `PERMISSION` / 403 | Model access and workspace permissions |
| `ENDPOINT` / 404 or 405 | Full rerank URL and HTTP API protocol |
| `RATE_LIMIT` / 429 | Provider quota and request rate |
| `PROVIDER` / 5xx | Provider availability |
| `HTTP_ERROR` / other status | Request/model compatibility in provider documentation |
| `TIMEOUT` | Network latency and `RERANK_TIMEOUT` (positive, at most 30s) |
| `NETWORK` | DNS, proxy, connectivity and TLS |
| `INVALID_RESPONSE` | JSON schema, complete index coverage and finite scores |
| `REQUEST_ERROR` / `INTERRUPTED` | Local configuration or request cancellation |

Logs deliberately omit keys, endpoint URLs, questions, source text and provider
response bodies. A longer timeout increases worst-case answer latency; adjust it
only after measuring. The timeout default remains 2s.

## Validation

`EvidenceSelectorTest` covers shared budgets, Chinese/English/emoji text, overlap,
source ranges, primary-hit retention and deduplication. `HttpEvidenceRerankerTest`
uses a local HTTP fixture for ordering, malformed outputs, provider errors,
header/body timeouts and disabled mode. Run with Java 21:

```bash
mvn -Dtest=EvidenceSelectorTest,HttpEvidenceRerankerTest,HybridEvidenceRetrieverTest,QaRetrievalHarnessTest test
```
