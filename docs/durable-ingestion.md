# Durable ingestion

Upload/finalization and owner-triggered retry synchronously enqueue a row in
`ingestion_jobs` in the same database transaction. No in-memory callback is needed
after commit. Startup backfills processing documents created by older versions.

A worker polls every two seconds, claiming one job using `FOR UPDATE SKIP LOCKED`.
The claim commits before execution and records a unique owner, a two-minute lease,
and an attempt number (`retry_count`; `max_retries` is the total attempt budget).
Execution locks both the owned job and the processing document until commit.
Other workers skip that job even if a long attempt exceeds its lease. This also
serializes document deletion with index writes. A crashed process releases its
locks; after lease expiry a different worker can claim and rebuild the document.

The default budget is three attempts with persistent two/four-second backoff.
Expired owners cannot execute, finish, or fail a reclaimed job. Cleanup must
succeed before another rebuild. Terminal failure attempts best-effort cleanup
and hides the document from retrieval by marking it FAILED. An owner may retry
the failed document from the existing UI, creating a fresh job.

PostgreSQL and Elasticsearch do not share a distributed transaction. External
writes may survive a database rollback; retries delete artifacts by document ID
before rebuilding. A cleanup outage can leave hidden artifacts until a later
successful retry. Delivery is at least once, not exactly once. A hung worker
holding a live database transaction requires cancellation/restart; leases do not
permit a second worker to write over an active attempt.

Configuration:

- `document.ingestion.worker-enabled`: disable background polling in integration tests.
- `document.ingestion.poll-interval-ms`: polling delay (default 2000).

Validation: `docker compose up -d postgres`, then with Java 21 run
`mvn -Dtest=FlywayMigrationTest,DocumentLifecycleHarnessTest,DocumentServiceTest,DocumentIngestionAsyncServiceTest,StaleProcessingDocumentRecoveryRunnerTest test`.
The integration suite uses an isolated temporary database and verifies rollback,
duplicate delivery, interrupted claims, lock exclusion, backoff and exhaustion.
