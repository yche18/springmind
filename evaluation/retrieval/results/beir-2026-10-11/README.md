# BEIR local results: SciFact and NFCorpus

Full official test sets, frozen before measurement in commit `b48aeb1`. The
collector/report implementation is in `94bd6f5`. No cloud inference, query
rewriting, new labels, reranker or application-default changes. Original synthetic
results remain intact. See [report.html](report.html), the
[protocol](../../beir/README.md) and [runbook](../../beir/RUNBOOK.md).

## Ranked quality

Numbers below are nDCG@10 on a 0–1 scale, using trec_eval's linear gains. RRF is a
separate dual-route arm, not part of either single-route baseline. All use the
same title+body documents; RRF rank constant60 and both input depths were fixed
before measurement.

| Dataset | Documents / test queries | BM25 | BGE-M3 | RRF depth50 | RRF depth1000 |
|---|---:|---:|---:|---:|---:|
| SciFact | 5,183 / 300 | 0.6826 | 0.6436 | 0.7011 | 0.6998 |
| NFCorpus | 3,633 / 323 | 0.3222 | 0.3152 | 0.3458 | 0.3492 |

RRF50 exceeds the stronger single route (BM25) by **1.85 percentage points** on
SciFact, but the paired bootstrap95% interval is **[-0.37,+4.05]** points: the
observed mean is better, not a conclusive advantage over that baseline on these
queries. Per-query nDCG wins/losses/ties are71/37/192.

NFCorpus RRF50 improves by **2.36 points**, interval **[+1.20,+3.55]** points,
wins/losses/ties114/68/141. RRF1000 improves by2.70 points, interval
[+1.54,+3.89]. Both preregistered depths are reported; neither was selected by
tuning on test judgments. RRF1000 is not uniformly better: its SciFact mean is
slightly lower than RRF50. Intervals are descriptive for these datasets, not a
claim of production/domain-general performance.

## Complementarity is real, but larger unions do not always beat deeper singles

U25 means top25 from each route, deduplicated, at most50 total; U50 means top50
from each route, at most100 total. These have no intrinsic ranking. Comparisons
below use the same maximum output budget, but two-route compute is still higher.

| Dataset / budget | Mean union size | Union recall | BM25 same-budget recall | BGE same-budget recall |
|---|---:|---:|---:|---:|
| SciFact U25 vs top50 | 42.85 | 93.20% | 90.02% | 88.03% |
| SciFact U50 vs top100 | 86.26 | 94.87% | 92.82% | 90.37% |
| NFCorpus U25 vs top50 | 37.89 | 23.03% | 21.85% | 23.32% |
| NFCorpus U50 vs top100 | 74.77 | 27.85% | 24.60% | 28.31% |

At per-route depth50, SciFact has21 keyword-exclusive and17 dense-exclusive
relevant query-document pairs; NFCorpus has509 and567 respectively. Neither
route contains everything the other finds. However, NFCorpus's union loses to
the deeper dense single at the same *maximum* output budget. This is why a
larger union is not, by itself, proof of superior retrieval efficiency.

RRF50 has at most100 candidates, so its Recall@1000 is a saturation diagnostic,
not a fair equal-candidate comparison with a single top1000. The depth1000 arm
and explicit union-budget tables prevent confusing these cases.

## Concrete examples, including regressions

Official query/document IDs can be inspected in the full HTML report and locally
downloaded source files. No source query/abstract text is republished here.

- **SciFact715**, relevant doc18421962: BM25 rank13 and BGE rank25, but RRF50
  rank5. Both singles miss at10, while fusion promotes their shared moderately
  ranked evidence into the top10. This demonstrates an agreement benefit, not
  a model generating a new answer or a document absent from both candidates.
- **SciFact72**, relevant doc6076903: rank2 in each single, rank1 after fusion;
  nDCG@10 rises from0.6309 in both singles to1.0000. Their top-ranked distractions
  do not receive the same cross-route agreement boost.
- **SciFact1088**, relevant doc37549932: BGE rank1, BM25 rank54. It is absent
  from BM25's depth50 input, and RRF50 drops it outside the top10; nDCG falls
  from1.0000 for dense to0.0000. RRF1000 recovers it at rank5, but still below
  the dense single. Fixed fusion can suppress strong one-route evidence.
- **NFCorpus PLAIN-307**: nDCG@10 BM25=0.5249, BGE=0.7328, RRF50=1.0000;
  here fusion beats both singles on the same query.
- **NFCorpus PLAIN-1537**: BM25=0.0948, BGE=0.7530, RRF50=0.3070;
  a substantial regression against dense is retained, not filtered out.

The HTML additionally selects two largest RRF50-vs-dense improvements and two
largest regressions per dataset by a stated deterministic rule. All623 query
records remain available, not only illustrative cases. A local-only source-text
view is generated at `target/beir-review.html` and excluded from Git.

## Interpretation and limitations

These public corpora show useful lexical/dense complementarity and an average
fusion gain under this particular setup. They do **not** invalidate the earlier
synthetic results, prove that RRF always wins, or prove it improves the project's
actual user workload. Maintain the single-route controls and evaluate application
queries separately. Further tuning needs separate development data; do not tune
against these test results and report the same test set as an unbiased holdout.

SciFact labels scientific evidence (support or refutation); NFCorpus relevance
comes from automatically extracted links and is not exhaustive human grading.
Unjudged results score zero under the official convention but are not confirmed
negatives. With RRF50 the judged fraction@10 is9.43% on SciFact and25.17% on
NFCorpus. This is label coverage, not precision. No manual relabeling is required
to reproduce the official metrics. These English datasets cannot establish
Chinese/cross-language quality; possible model-training overlap precludes a
strict zero-shot claim. This is a controlled English BM25/exact-dense baseline,
not the application's IK/filename-boosted production pipeline or vendor stack.

Runtime: Elasticsearch8.15.3, Ollama0.33.1, pinned BGE-M3 F16. The initial112
SciFact vectors used CPU; remaining encodings used an isolated RTX3060 Laptop GPU
container with read-only existing weights. That temporary container was stopped
after collection; original services/defaults were unchanged. The initial physical
batch-limit failure and full-context fix are documented in the runbook. No
latency claim is made. Raw corpora, vectors and uncompressed originals remain
in ignored local cache paths; only IDs/scores/qrels and derived reports are
committed. Compressed captures are losslessly round-trip checked.

Validation: all80 offline evaluation regression tests passed with Java21,
including independent linear-nDCG checks, exact report regeneration, sorted and
unique ranked IDs, full test-set counts, frozen provenance, candidate accounting
and artifact hashes. The source/qrel hashes were rechecked after collection.
HTML content and deterministic generation were verified offline. Open
`report.html` locally to view it.
