# Answer traces and source passages

`POST /api/qa/ask` includes an additive `trace` field on answers and refusals:

- `traceId`: request identifier also emitted in completion/error logs.
- `evidenceLevel`: the existing heuristic coverage grade, not a calibrated confidence.
- `retrieval`: planned queries, channel hit counts, unique candidates, and stage milliseconds.
- `retrievalMs`, `generationMs`, `totalMs`: wall-clock durations; generation includes
  the existing raw-output fallback if structured conversion fails.
- `evidence`: selected source identities, zero-based chunk ranges, RRF score,
  and previews capped at 600 characters. Refusals may still have inspected evidence.

The frontend displays one-based chunk positions. Expand an evidence entry to call
`GET /api/qa/evidence?groupId=...&documentId=...&start=...&end=...`.
The backend rechecks membership and returns only READY, non-deleted source chunks.
It rejects inverted, negative, or excessively large ranges. The returned passages
are parsed/cleaned source text, not original PDF page coordinates. Deleted or
subsequently unavailable sources produce an empty result.

Trace metadata is carried with the request; there is no public trace-ID lookup or
cross-user trace cache. Questions, full prompts and evidence text are not added
to diagnostic logs. Existing application/model error logging is unchanged.
No-evidence requests skip answer generation (query planning may still call a model).
The source viewer treats retrieved text as plain text, including HTML-like content.

Validation uses Java 21 QA tests and the QA/document lifecycle harnesses, PostgreSQL
source filtering tests, plus `npm run build` in the frontend.
