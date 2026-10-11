# MultiHop-RAG final cached-context answer evaluation

## Scope fixed before generation

Reuse all60 frozen questions (10 calibration,50 formal) and four saved context
arms: VECTOR, RRF, VECTOR_QWEN, RRF_QWEN. Original results and labels stay immutable.
No retrieval, embedding, planning, reranking or paid judge calls. Export replays
saved ranks offline and verifies each context against its previously committed
SHA-256. This is a controlled retrieval-to-answer experiment with cached evidence,
not a live HTTP/application-SLA or unchanged-production-prompt benchmark.

Model: configured `deepseek-flash`, endpoint `https://api.deepseek.com/chat/completions`.
Use temperature0, thinking disabled, JSON object output, max_tokens900,120s timeout.
Same evaluation-only prompt/schema for every arm; production prompts remain unchanged.
The schema adds a short answer and quoted evidence citations to enable auditing;
the application currently returns prose and attaches retrieved documents instead.
No official answer, reference fact, split or arm name is sent to the provider.
Public news contexts and questions are the only task data sent externally.
API options: https://api-docs.deepseek.com/api/create-chat-completion/ (checked2026-10-11).

## Two stages and cost controls

1. Freeze inputs, prompt, options and scoring implementation before any calls.
2. Generate40 calibration responses first. Inspect schema, answer extraction,
   refusals and citation integrity; document a calibration decision and commit it.
3. Freeze the approved protocol, then generate200 formal responses once.
   No changes selected from formal outcomes. No additional judge requests.

At most240 generation attempts total in this protocol. No automatic retries.
Every request has a durable intent before sending and a durable outcome afterward.
An unmatched intent stops resumption; completed responses are reused. Three
consecutive transport/provider errors stop collection. HTTP errors are retained.
Record actual returned token usage, model identifier, fingerprint and latency.
Temperature0 does not guarantee reproducibility or pin remote model weights.
Changing the prompt requires a new explicit version and a separately approved
attempt budget; failures are never silently discarded or replaced.

## Scoring fixed before formal answers

- **Normalized exact match (EM)**: NFKC, case folding, punctuation removal,
  English article removal and whitespace normalization. Do not invent aliases
  after viewing formal outputs. Token F1 is secondary, not semantic correctness.
- Formal44 answerable and6 null questions are reported separately. On null
  questions, only a well-formed `answered=false` counts as correct abstention.
  On answerable questions, abstention is incorrect even when context is missing.
  Provider/schema/truncation failures count as failures in the full denominator.
- Citation validity checks nonempty quotes against the cited supplied window
  after whitespace/NFKC normalization. A matching quote is NOT entailment.
- Report a strict **reference-supported EM proxy**: exact answer plus valid
  citations whose cited windows jointly contain all official reference-fact
  spans. This is conservative and is NOT human-verified grounded accuracy:
  alternate sufficient evidence may pass semantic review despite proxy failure,
  and citing a window is not proof that the quoted passage supports every claim.
- Human semantic answer/grounding decisions remain pending, never inferred from
  model self-reports. Export a review queue for nonexact answered outputs,
  unsupported exact answers, malformed citations and cross-arm answer/refusal
  disagreement; all responses are inspectable, including negative cases.
- Official reference labels were only partly spot-checked by the user. Do not
  describe all60 as manually adjudicated. Keep answer-leaking question m2521
  flagged, report a sensitivity score excluding it, and do not claim closed-book
  knowledge was ruled out. These public questions may occur in training data.

Only50 formal questions contribute headline results. Calibration scores stay
separate. No significance or production-generalization claims from this small set.
Generation latency excludes cached retrieval/rerank time. Provider tokens are
not a currency invoice. Evaluation reports must retain all four arms.

## Reproduction

Use Java21 for offline export:

```powershell
mvn '-Dtest=MultiHopAnswerExportRun' '-Deval.multihopAnswerExport=true' test
python evaluation/answer/multihop/run.py freeze
python -m unittest discover -s evaluation/answer/multihop -p 'test_*.py'
python evaluation/answer/multihop/run.py collect --split CALIBRATION --confirm-paid
# Inspect40 outputs and commit calibration.json before the next command.
python evaluation/answer/multihop/run.py collect --split FORMAL --confirm-paid
python evaluation/answer/multihop/run.py report
```

The collector reads only the existing local `.env` AI_BASE_URL, AI_CHAT_MODEL and
AI_API_KEY (process environment takes precedence); secrets are never printed or
committed. Full contexts/raw responses/review source text stay in ignored target/.
Versioned manifests, answer records, metrics and reports allow offline auditing.
