# BEIR public-data baseline protocol (frozen before measurement)

## Why these datasets

Vendor evaluations use independently published benchmarks, not only synthetic
application questions. [Elastic's hybrid study](https://www.elastic.co/search-labs/blog/improving-information-retrieval-elastic-stack-hybrid)
uses BEIR; [AWS/OpenSearch's RRF study](https://opensearch.org/blog/introducing-reciprocal-rank-fusion-hybrid-search/)
includes NFCorpus, ArguAna, FiQA, TREC-COVID, SciDocs and Quora. Its results include
losses against the best single route as well as gains. Microsoft's
[Azure evaluation](https://techcommunity.microsoft.com/blog/azure-ai-foundry-blog/raising-the-bar-for-rag-excellence-query-rewriting-and-new-semantic-ranker/4302729)
uses public BEIR/MIRACL labels alongside other evaluation sources; results with
query rewriting or semantic reranking must not be attributed to RRF alone.

Select **SciFact and NFCorpus in advance**, for complete-corpus local feasibility
and different tasks, not observed fusion wins. These are an initial two-dataset
study, not a full BEIR/vendor reproduction. Both are English; they do not measure
Chinese or cross-language retrieval. No original application result is replaced.

- SciFact: scientific claim/evidence retrieval, 5,183 abstracts and 300 BEIR test
  queries. [Original source](https://github.com/allenai/scifact),
  [license](https://github.com/allenai/scifact/blob/master/LICENSE.md): claims and
  evidence annotations CC BY 4.0; abstracts ODC-By 1.0; repository code Apache2.0.
  Relevant abstracts can support or refute a claim; this is retrieval, not truth scoring.
- NFCorpus: nontechnical queries against technical medical documents, 3,633 BEIR
  documents and 323 test queries. [Original source and terms](https://www.cl.uni-heidelberg.de/statnlpgroup/nfcorpus/):
  free for academic purposes, other uses require consulting the owner's terms.
  Relevance is automatically derived from direct/indirect links, not exhaustive
  independent human adjudication. Use here only for local research evaluation;
  do not ship the source corpus in the application/repository or claim permission
  for commercial use. Cite Boteva et al. (2016).
- [BEIR distribution and checksums](https://github.com/beir-cellar/beir): SciFact
  ZIP MD5 `5f7d1de60b170fc8027bb7898e2efca1`; NFCorpus ZIP MD5
  `a89dba18a62ef92f7d323ec890a0d38d`. Data downloaded only from the official
  public.ukp.informatik.tu-darmstadt.de mirror into ignored target/beir-downloads.

## Preregistered controls

Freeze corpus/query/test-qrel file hashes, archive checksums, this protocol and the
existing BGE-M3 model lock before collection. Use the full corpus and all official
test queries having positive judgments, without rewriting, translating, dropping
hard questions, sampling distractors or changing qrels. Do not tune on test labels.
Public benchmarks may overlap model training: do not claim strict zero-shot status.

Both routes see the **same title + newline + text** per original BEIR document.
No filename field/boost, chunking, window expansion or answer generation. Preserve
original document IDs and document-level qrels. Local pinned bge-m3:567m F16 dense
embeddings, 1024 dimensions, raw queries, num_ctx8192, truncate=false. Encoding is
batched with per-batch checkpoints. No cloud inference or model downloads.

Lexical baseline: local Elasticsearch8.15.3, BM25 defaults (k1=1.2,b=.75), English
analyzer on the single concatenated text field, no phrase/rescore/filename boosts.
New dedicated one-shard indexes only. This is a controlled public-benchmark
baseline, **not** the application's IK-based boosted lexical chain or an exact
vendor-stack reproduction. Dense baseline: exhaustive cosine over the same
documents; no ANN omissions. Save top1000 per route, score ties by document ID.

RRF: equal weights, rank constant60, predeclared input depths50 and1000, with
ranked outputs through1000 for analysis (no production30 cap). Report both; do not
pick the better configuration after observing results. Use the same output cutoff
when comparing ranked arms. Unordered unions U25 (25+25, maximum50) and U50
(50+50, maximum100) report candidate recall and size, never MRR/nDCG. Compare
U25 with each single top50, and U50 with each single top100, noting actual size
and different retrieval compute costs. Count exclusive relevant query-document
pairs and relevant items lost from U50 at RRF50 top10.

## Metrics

Primary nDCG@10 uses **linear relevance gain**, matching trec_eval's ndcg_cut
convention (not the synthetic evaluator's exponential gain). Secondary Recall@5,
@10,@50,@100,@1000 and MRR@10. Recall is over all positive qrels. Missing judgments
are treated as zero for standard scoring, not proven irrelevant; report judged
fraction@10 to expose incomplete labels. Explicit zero qrels remain judged.
Exclude identical query/document IDs as BEIR's default evaluator does.
No new human annotation or generation judge is required to run official qrels.

Per dataset, report paired RRF-minus-dense and RRF-minus-BM25 query deltas,
win/loss/tie counts and a seeded paired bootstrap95% interval (2,000 resamples).
Intervals describe these queries, not unseen domains or all BEIR datasets. No
automatic default model/policy changes. Report gains and regressions together.

## Local artifacts

Raw data and batch embeddings stay in ignored target directories; commit only
provenance, code, aggregate/per-query scores, ranked IDs/scores and HTML reports.
Do not embed copyrighted abstracts or query text into the public report. IDs link
the measurements to the locally downloaded data. Old evaluation datasets remain
unchanged, including pending Meridian annotations.

Java21: BeirFreezeRun, LiveBeirRun (eval.beir=true and eval.confirmLocalWrites=true),
BeirReportRun and BeirEvaluationTest/RecordedBeirTest. Live collection only calls
loopback Ollama and Elasticsearch. Corpus downloading is a separate explicit step.
