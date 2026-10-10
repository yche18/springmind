# Fixed-pool rerank replay

Run with Java21; do not start Spring, Docker, Elasticsearch, Ollama or chat generation.
The existing frozen source downloads are needed only to reconstruct windows and
verify complete context text. Normal recorded-metric tests do not need raw news.

## Collection (paid, explicit)

The committed `freeze.json` and `inputs.json` define the 180 request inputs and
10-second evaluation timeout. Do not recreate them based on model outcomes.
Load only `RERANK_URL`, `RERANK_MODEL`, and `RERANK_API_KEY` into the process from the
existing local configuration; never print keys or pass them on a command line.
The collector rejects endpoint/model changes. Do not copy any credentials into Git.

```powershell
mvn '-Dtest=LiveMultiHopRerankRun' '-Deval.multihopRerank=true' '-Deval.confirmExternal=true' test
```

Existing final captures are immutable. Interrupted runs use
`target/multihop-rerank-cache`: completed outcomes are reused, but an unmatched
`.intent.json` stops execution to avoid a duplicate charge after an unknown result.
No automatic retry and no more than180 provider attempts. Three consecutive
fallbacks halt new attempts for investigation. Do not delete failed outcomes to
make a rerun look better. `request-ledger-hashes.json` preserves cache provenance;
the final capture includes every outcome, full scores/order and selected context.

The query/evidence previews are public-data material. Standard answers and reference
labels are not request fields. The production client does not expose billed usage;
the freeze's approximately1.40M token estimate is not an invoice amount.

## Offline verification and report

```powershell
mvn '-Dtest=MultiHopRerankReportRun' '-Deval.multihopRerankReport=true' test
mvn '-Dtest=MultiHopRerankTest,RecordedMultiHopRerankTest,RecordedMultiHopTest' test
```

Report generation independently reconstructs all180 contexts from saved model
orders and verifies their chunk IDs, tokens and text hashes. No provider call occurs.
It refuses to overwrite existing reports. Archive derived files first if deliberate
regeneration is needed; keep the capture and frozen protocol immutable.

The versioned report is `evaluation/rerank/results/multihop-2026-10-11/report.html`.
The ignored detailed review is `target/multihop-rerank-review/review.html`, with
candidate previews, scores, and before/after selected chunks. Old retrieval reports
are preserved byte-for-byte. A later answer-generation experiment requires a separate
request and protocol; this run does not perform it.
