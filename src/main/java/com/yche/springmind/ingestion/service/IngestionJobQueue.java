package com.yche.springmind.ingestion.service;

import com.yche.springmind.document.service.DocumentIngestionAsyncService;
import com.yche.springmind.document.service.DocumentIngestionRequestedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Durable work queue; database row locks fence workers whose claim has expired. */
@Service
public class IngestionJobQueue {
    private final JdbcTemplate jdbc;
    private final DocumentIngestionAsyncService ingestion;

    public IngestionJobQueue(JdbcTemplate jdbc, DocumentIngestionAsyncService ingestion) {
        this.jdbc = jdbc;
        this.ingestion = ingestion;
    }

    /** The synchronous listener joins the upload transaction: both records commit or neither does. */
    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(DocumentIngestionRequestedEvent event) {
        jdbc.update("""
                insert into ingestion_jobs(document_id, group_id, job_type, status, retry_count, max_retries)
                values (?, ?, 'INGEST_DOCUMENT', 'PENDING', 0, 3)
                on conflict (document_id) where status in ('PENDING', 'RUNNING') do nothing
                """, event.documentId(), event.groupId());
    }

    @Transactional
    public int recoverUnqueuedDocuments() {
        return jdbc.update("""
                with documents_to_recover as (
                    select id, group_id from documents
                    where status = 'PROCESSING' and deleted = false
                    for update skip locked
                )
                insert into ingestion_jobs(document_id, group_id, job_type, status, retry_count, max_retries)
                select id, group_id, 'INGEST_DOCUMENT', 'PENDING', 0, 3
                from documents_to_recover
                on conflict (document_id) where status in ('PENDING', 'RUNNING') do nothing
                """);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Job claim() {
        String owner = UUID.randomUUID().toString();
        List<Job> jobs = jdbc.query("""
                with candidate as (
                    select id from ingestion_jobs
                    where job_type = 'INGEST_DOCUMENT' and (
                        (status = 'PENDING' and (next_retry_at is null or next_retry_at <= now()))
                        or (status = 'RUNNING' and (lease_until is null or lease_until <= now()))
                    )
                    order by created_at, id for update skip locked limit 1
                )
                update ingestion_jobs j set status = 'RUNNING', worker_id = ?,
                    started_at = now(), finished_at = null, lease_until = now() + interval '2 minutes',
                    retry_count = retry_count + 1, updated_at = now()
                from candidate where j.id = candidate.id
                returning j.id, document_id, group_id, retry_count, max_retries
                """, (rs, row) -> new Job(rs.getLong("id"), rs.getLong("document_id"),
                rs.getLong("group_id"), owner, rs.getInt("retry_count"), rs.getInt("max_retries")), owner);
        return jobs.isEmpty() ? null : jobs.getFirst();
    }

    /** Hold the job lock throughout external writes; expired claims cannot steal an active attempt. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void execute(Job job) {
        if (!lockOwnedJob(job)) {
            return;
        }
        if (!lockProcessingDocument(job)) {
            finish(job, "FAILED", "Document was deleted or is no longer processing");
            return;
        }
        if (job.attempt() > job.maxAttempts()) {
            ingestion.recover(new IllegalStateException("Ingestion interrupted too many times"),
                    job.documentId(), job.groupId());
            finish(job, "FAILED", "Ingestion interrupted too many times");
            return;
        }
        ingestion.ingestDocument(job.documentId(), job.groupId());
        finish(job, "SUCCEEDED", null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(Job job, RuntimeException failure) {
        if (!lockOwnedJob(job)) {
            return;
        }
        String error = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
        error = error.substring(0, Math.min(512, error.length()));
        if (!lockProcessingDocument(job)) {
            finish(job, "FAILED", error);
        } else if (job.attempt() >= job.maxAttempts()) {
            ingestion.recover(failure, job.documentId(), job.groupId());
            finish(job, "FAILED", error);
        } else {
            long delaySeconds = Math.min(60, 2L << Math.min(job.attempt() - 1, 5));
            jdbc.update("""
                    update ingestion_jobs set status = 'PENDING', worker_id = null, lease_until = null,
                        next_retry_at = now() + (? * interval '1 second'), last_error = ?, updated_at = now()
                    where id = ? and worker_id = ?
                    """, delaySeconds, error, job.id(), job.owner());
        }
    }

    private boolean lockOwnedJob(Job job) {
        return !jdbc.queryForList("""
                select id from ingestion_jobs where id = ? and worker_id = ? and status = 'RUNNING'
                for update
                """, Long.class, job.id(), job.owner()).isEmpty();
    }

    private boolean lockProcessingDocument(Job job) {
        return !jdbc.queryForList("""
                select id from documents where id = ? and group_id = ? and deleted = false
                    and status = 'PROCESSING' for update
                """, Long.class, job.documentId(), job.groupId()).isEmpty();
    }

    private void finish(Job job, String status, String error) {
        jdbc.update("""
                update ingestion_jobs set status = ?, finished_at = now(), lease_until = null,
                    next_retry_at = null, last_error = ?, updated_at = now()
                where id = ? and worker_id = ?
                """, status, error, job.id(), job.owner());
    }

    public record Job(long id, long documentId, long groupId, String owner, int attempt, int maxAttempts) { }
}
