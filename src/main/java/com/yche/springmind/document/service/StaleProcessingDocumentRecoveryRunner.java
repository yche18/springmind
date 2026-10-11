package com.yche.springmind.document.service;

import com.yche.springmind.ingestion.service.IngestionJobQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Backfills interrupted uploads from versions that did not persist ingestion requests. */
@Component
public class StaleProcessingDocumentRecoveryRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(StaleProcessingDocumentRecoveryRunner.class);
    private final IngestionJobQueue queue;

    public StaleProcessingDocumentRecoveryRunner(IngestionJobQueue queue) {
        this.queue = queue;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("Recovered unqueued ingestion requests: count={}", queue.recoverUnqueuedDocuments());
    }
}
