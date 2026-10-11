package com.yche.springmind.ingestion.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@ConditionalOnProperty(name = "document.ingestion.worker-enabled", havingValue = "true", matchIfMissing = true)
public class IngestionJobWorker {
    private static final Logger log = LoggerFactory.getLogger(IngestionJobWorker.class);
    private final IngestionJobQueue queue;

    public IngestionJobWorker(IngestionJobQueue queue) {
        this.queue = queue;
    }

    @Scheduled(fixedDelayString = "${document.ingestion.poll-interval-ms:2000}")
    public void poll() {
        IngestionJobQueue.Job job = queue.claim();
        if (job == null) {
            return;
        }
        try {
            queue.execute(job);
        } catch (RuntimeException failure) {
            log.warn("Ingestion attempt failed: jobId={}, documentId={}, attempt={}",
                    job.id(), job.documentId(), job.attempt(), failure);
            queue.fail(job, failure);
        }
    }
}
