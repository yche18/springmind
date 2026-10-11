package com.yche.springmind.qa.evaluation;

import org.junit.jupiter.api.Test;

class BeirFreezeRun {
    @Test void freeze() throws Exception {
        ChallengeData.write(BeirData.PROTOCOL.resolve("freeze.json"), BeirData.hashes());
        BeirData.verify();
        System.out.println("BEIR inputs frozen: SciFact 5183/300; NFCorpus 3633/323. No retrieval performed.");
    }
}
