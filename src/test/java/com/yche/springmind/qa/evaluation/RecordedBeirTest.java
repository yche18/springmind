package com.yche.springmind.qa.evaluation;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Replays committed rankings without services, raw corpora, credentials or model calls. */
class RecordedBeirTest {
    @Test void officialFullTestRunsReproduceReportsAndCandidateAccounting() throws Exception {
        var mapper = RerankEvaluationRun.datasetMapper();
        Map<String, BeirEvaluation.Capture> captures = new LinkedHashMap<>();
        for (String name : BeirData.DATASETS) {
            var c = BeirData.capture(name); BeirReportRun.validate(c); captures.put(name,c);
            var calculated = BeirEvaluation.compare(c);
            assertThat(mapper.<com.fasterxml.jackson.databind.JsonNode>valueToTree(calculated)).isEqualTo(mapper.readTree(BeirData.OUTPUT.resolve(name+"-comparison.json").toFile()));
            for (var row : c.rows()) {
                for (var hits : List.of(row.bm25(),row.dense())) {
                    assertThat(hits).isSortedAccordingTo(Comparator.comparingDouble(BeirEvaluation.Hit::score).reversed().thenComparing(BeirEvaluation.Hit::id));
                    var m = BeirEvaluation.score(BeirEvaluation.ids(hits), row.qrels());
                    assertThat(m.ndcg10()).isBetween(0.0,1.0000000001);
                    assertThat(m.recall5()).isLessThanOrEqualTo(m.recall10());
                    assertThat(m.recall10()).isLessThanOrEqualTo(m.recall50());
                    assertThat(m.recall50()).isLessThanOrEqualTo(m.recall100());
                    assertThat(m.recall100()).isLessThanOrEqualTo(m.recall1000());
                    assertThat(m.ndcg10()).isCloseTo(independentNdcg(hits,row.qrels()),within(1e-12));
                }
                assertThat(row.dense()).hasSize(1000);
                var k=BeirEvaluation.ids(row.bm25()); var v=BeirEvaluation.ids(row.dense());
                for (int depth : List.of(25,50)) {
                    Set<String> distinct=new HashSet<>(k.stream().limit(depth).toList()); distinct.addAll(v.stream().limit(depth).toList());
                    var u=BeirEvaluation.union(k,v,BeirEvaluation.rrf(k,v,50),row.qrels(),depth);
                    assertThat(u.size()).isEqualTo(distinct.size()).isLessThanOrEqualTo(depth*2);
                    var relevant = row.qrels().entrySet().stream().filter(e->e.getValue()>0).map(Map.Entry::getKey).toList();
                    assertThat(u.recall()).isEqualTo((double) relevant.stream().filter(distinct::contains).count()/relevant.size());
                }
            }
            for (var e : calculated.pairedEffects().values()) assertThat(e.improved().size()+e.regressed().size()+e.tied().size()).isEqualTo(c.queries());
        }
        assertThat(Files.readString(BeirData.OUTPUT.resolve("report.html"))).isEqualTo(BeirReportRun.html(captures));
        var hashes=mapper.readTree(BeirData.OUTPUT.resolve("artifact-hashes.json").toFile());
        for (var fields=hashes.fields(); fields.hasNext();) {
            var entry=fields.next(); assertThat(RetrievalAblation.hash(BeirData.OUTPUT.resolve(entry.getKey()))).isEqualTo(entry.getValue().asText());
        }
    }
    private static double independentNdcg(List<BeirEvaluation.Hit> hits, Map<String,Integer> qrels) {
        double actual=0,ideal=0;
        var grades=qrels.values().stream().sorted(Comparator.reverseOrder()).toList();
        for (int position=1;position<=10;position++) {
            double discount=Math.log(2)/Math.log(position+1);
            if(position<=hits.size()) actual+=qrels.getOrDefault(hits.get(position-1).id(),0)*discount;
            if(position<=grades.size()) ideal+=grades.get(position-1)*discount;
        }
        return actual/ideal;
    }
}
