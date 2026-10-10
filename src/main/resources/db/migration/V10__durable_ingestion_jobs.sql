-- Only one active ingestion may rebuild a document's indexes at a time.
with duplicates as (
    select id, row_number() over (partition by document_id order by id desc) as position
    from ingestion_jobs where status in ('PENDING', 'RUNNING')
)
update ingestion_jobs set status = 'FAILED', finished_at = now(),
    last_error = 'Superseded by another active ingestion job'
where id in (select id from duplicates where position > 1);

alter table ingestion_jobs add column lease_until timestamp;
create unique index uk_ingestion_jobs_active_document on ingestion_jobs(document_id)
    where status in ('PENDING', 'RUNNING');
create index idx_ingestion_jobs_expired on ingestion_jobs(lease_until)
    where status = 'RUNNING';
