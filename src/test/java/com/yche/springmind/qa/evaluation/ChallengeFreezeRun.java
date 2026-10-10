package com.yche.springmind.qa.evaluation;

import org.junit.jupiter.api.Test;

/** Run and commit before any challenge retrieval or embedding. */
class ChallengeFreezeRun {
    @Test
    void freeze() throws Exception {
        var fixture = ChallengeData.fixture();
        var corpus = ChallengeData.build(fixture);
        ChallengeData.write(ChallengeData.ROOT.resolve("corpus.json"), corpus);
        ChallengeData.write(ChallengeData.ROOT.resolve("freeze.json"), ChallengeData.hashes());
        ChallengeData.verifyFreeze();
        System.out.println("Frozen: " + fixture.documents().size() + " documents, " + corpus.chunks().size() + " chunks, "
                + fixture.cases().size() + " questions. Initial labels await human review.");
    }
}
