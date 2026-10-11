package com.yche.springmind.qa.evaluation;

import com.yche.springmind.qa.rag.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.ai.document.Document;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import static com.yche.springmind.qa.evaluation.RetrievalAblation.require;

/** Offline export of the exact previously measured contexts; no model clients. */
@EnabledIfSystemProperty(named = "eval.multihopAnswerExport", matches = "true")
class MultiHopAnswerExportRun {
    @Test void export() throws Exception {
        MultiHopRerank.verify();
        var corpus = MultiHopData.corpus(); MultiHopData.verify(corpus);
        var baseline = MultiHopRerank.baseline();
        var capture = MultiHopData.M.readValue(MultiHopRerank.OUT.resolve("capture.json").toFile(), MultiHopRerank.Capture.class);
        var sample = MultiHopData.sample();
        List<Object> rows = new ArrayList<>();
        for (int i = 0; i < sample.questions().size(); i++) {
            var q = sample.questions().get(i); var before = baseline.rows().get(i); var after = capture.rows().get(i);
            int[] modeIndex = {0};
            MultiHopReplay.run(corpus, q, before.keyword(), before.vector(), (query, docs) -> {
                String mode = MultiHopRerank.MODES.get(modeIndex[0]++);
                if (!mode.equals("KEYWORD")) for (boolean rerank : List.of(false, true)) {
                    try {
                        EvidenceReranker replay = rerank ? (question, candidates) -> MultiHopRerank.replay(after.outcomes().get(mode), candidates)
                                : (question, candidates) -> new EvidenceReranker.Result(candidates, "DISABLED");
                        var selected = new EvidenceSelector(EvidenceSelectionProperties.defaults(), replay).select(query, docs, 5).documents();
                        String text = selected.stream().map(Document::getText).collect(Collectors.joining(System.lineSeparator()));
                        var expected = (rerank ? after.contexts() : before.contexts()).get(mode);
                        require(expected.textSha256().equals(MultiHopData.sha(text)), "Previously saved context changed");
                        List<Object> evidence = new ArrayList<>();
                        for (var d : selected) {
                            long docId = ((Number) d.getMetadata().get("documentId")).longValue();
                            int start = ((Number) d.getMetadata().get("startChunkIndex")).intValue();
                            int end = ((Number) d.getMetadata().get("endChunkIndex")).intValue();
                            var chunks = corpus.chunks().values().stream().filter(c -> c.documentId() == docId && c.index() >= start && c.index() <= end)
                                    .map(MultiHopData.Chunk::id).toList();
                            evidence.add(Map.of("id", d.getId(), "documentId", docId, "chunks", chunks, "text", d.getText()));
                        }
                        rows.add(Map.of("id", q.id(), "split", q.split(), "arm", mode + (rerank ? "_QWEN" : ""),
                                "query", query, "context", text, "contextSha256", expected.textSha256(), "evidence", evidence));
                    } catch (Exception e) { throw new IllegalStateException(e); }
                }
                return new EvidenceReranker.Result(docs, "DISABLED");
            });
        }
        require(rows.size() == 240, "Expected four contexts for each of60 questions");
        var local = Path.of("target/multihop-answer"); Files.createDirectories(local);
        ChallengeData.write(local.resolve("inputs.json"), rows);
        System.out.println("Verified and exported240 existing contexts; external calls0");
    }
}
