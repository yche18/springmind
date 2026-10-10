package com.yche.springmind.flyway;

import com.yche.springmind.ingestion.service.DocumentIngestionProcessor;
import com.yche.springmind.ingestion.service.IngestionJobQueue;
import com.yche.springmind.ingestion.vector.VectorIngestionService;
import com.yche.springmind.retrieval.elasticsearch.ElasticsearchChunkIndexService;
import com.yche.springmind.document.service.DocumentIngestionRequestedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FlywayMigrationTest {

    private static final String ADMIN_DATABASE_URL = "jdbc:postgresql://localhost:5433/postgres";
    private static final String DATABASE_USERNAME = "root";
    private static final String DATABASE_PASSWORD = "123456";
    private static final String TEST_DATABASE = "springmind_flyway_" + UUID.randomUUID().toString().replace("-", "");
    private static final String INIT_PASSWORD_HASH = "$2a$10$LiNZepZm/R5ucS5PZW341u4sLXJfKoPVAwfFXBHEciHHcBoMBFpfi";
    private static final String TEST_JWT_SECRET = "test-jwt-secret-for-springmind-auth-32-bytes";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @MockBean
    private DocumentIngestionProcessor documentIngestionProcessor;

    @MockBean
    private VectorIngestionService vectorIngestionService;

    @MockBean
    private ElasticsearchChunkIndexService elasticsearchChunkIndexService;

    @Autowired
    private IngestionJobQueue queue;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private ApplicationEventPublisher events;

    @Autowired
    private com.yche.springmind.ingestion.mapper.DocumentChunkMapper chunkMapper;

    @Test
    void evidenceWindowMustRespectGroupReadyStateDeletionAndBounds() {
        seedDocument(5201L, 2501L, 2502L, 3401L);
        insertChunk(5201L, 2501L, 0);
        insertChunk(5201L, 2501L, 1);
        assertThat(chunkMapper.selectEvidenceWindow(2501L, 5201L, 0, 1)).isEmpty();
        jdbcTemplate.update("update documents set status = 'READY' where id = 5201");
        assertThat(chunkMapper.selectEvidenceWindow(2501L, 5201L, 1, 1))
                .extracting(com.yche.springmind.ingestion.model.entity.DocumentChunkEntity::getChunkText)
                .containsExactly("chunk-1");
        assertThat(chunkMapper.selectEvidenceWindow(2502L, 5201L, 0, 1)).isEmpty();
        jdbcTemplate.update("update documents set deleted = true where id = 5201");
        assertThat(chunkMapper.selectEvidenceWindow(2501L, 5201L, 0, 1)).isEmpty();
    }

    @DynamicPropertySource
    static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
        createDatabase(TEST_DATABASE);
        String jdbcUrl = "jdbc:postgresql://localhost:5433/" + TEST_DATABASE;
        registry.add("spring.datasource.url", () -> jdbcUrl);
        registry.add("spring.datasource.username", () -> DATABASE_USERNAME);
        registry.add("spring.datasource.password", () -> DATABASE_PASSWORD);
        registry.add("spring.flyway.url", () -> jdbcUrl);
        registry.add("spring.flyway.user", () -> DATABASE_USERNAME);
        registry.add("spring.flyway.password", () -> DATABASE_PASSWORD);
        registry.add("springmind.auth.jwt-secret", () -> TEST_JWT_SECRET);
        registry.add("app.ai.chat.api-key", () -> "test-api-key");
        registry.add("document.ingestion.worker-enabled", () -> "false");
    }

    @Test
    void flywayShouldLoadCoreSchema() {
        List<String> tables = List.of(
                "users",
                "groups",
                "group_memberships",
                "group_invitations",
                "group_join_requests",
                "documents",
                "document_upload_sessions",
                "document_upload_chunks",
                "document_chunks",
                "ingestion_jobs"
        );
        List<String> indexes = List.of(
                "idx_groups_owner_user",
                "uk_group_memberships_single_owner",
                "uk_group_invitations_pending",
                "uk_group_join_requests_pending",
                "idx_group_join_requests_group_status",
                "idx_group_join_requests_applicant",
                "idx_documents_group_deleted_status",
                "idx_documents_group_hash_active",
                "uk_document_upload_sessions_upload_id",
                "idx_document_upload_sessions_reusable",
                "uk_document_upload_chunks_upload_chunk",
                "idx_document_chunks_document_chunk",
                "idx_ingestion_jobs_status_retry"
        );

        assertThat(jdbcTemplate.queryForObject("select current_database()", String.class)).isEqualTo(TEST_DATABASE);
        assertThat(currentFlywayVersion()).isEqualTo("10");
        assertThat(hasColumn("ingestion_jobs", "lease_until")).isTrue();
        assertThat(hasIndex("uk_ingestion_jobs_active_document")).isTrue();
        assertThat(tables).allSatisfy(table -> assertThat(hasTable(table)).isTrue());
        assertThat(indexes).allSatisfy(index -> assertThat(hasIndex(index)).isTrue());
        assertThat(hasConstraint("ck_group_join_requests_status")).isTrue();
        assertThat(hasColumn("groups", "description")).isTrue();
        assertThat(hasColumn("documents", "preview_text")).isTrue();
        assertThat(hasColumn("documents", "file_hash")).isTrue();
        assertThat(hasTaskFourSeedMemberships()).isTrue();
        assertThat(hasTaskFourPendingInvitation()).isTrue();
    }

    @Test
    void shouldContainAuthColumnsAndRefreshTokenTable() {
        assertThat(hasColumn("users", "username")).isTrue();
        assertThat(hasColumn("users", "email")).isTrue();
        assertThat(hasColumn("users", "password_hash")).isTrue();
        assertThat(hasColumn("users", "system_role")).isTrue();
        assertThat(hasColumn("users", "status")).isTrue();
        assertThat(hasColumn("users", "must_change_password")).isTrue();
        assertThat(hasColumn("users", "last_login_at")).isTrue();
        assertThat(hasTable("user_refresh_tokens")).isTrue();
        assertThat(hasIndex("uk_users_username")).isTrue();
        assertThat(hasIndex("uk_users_email")).isTrue();
        assertThat(hasConstraint("ck_users_system_role")).isTrue();
        assertThat(hasConstraint("ck_users_status")).isTrue();
        assertThat(hasUserCode("admin")).isTrue();
        assertThatThrownBy(() -> insertUserWithRoleAndStatus(8801L, "SUPER_ADMIN", "ACTIVE"))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertUserWithRoleAndStatus(8802L, "USER", "LOCKED"))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void documentChunksShouldEnforceGroupConsistencyAndChunkUniqueness() {
        seedDocument(5001L, 2201L, 2202L, 3201L);
        insertChunk(5001L, 2201L, 0);

        assertThatThrownBy(() -> insertChunk(5001L, 2202L, 1)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertChunk(5001L, 2201L, 0)).isInstanceOf(DataAccessException.class);
    }

    @Test
    void ingestionJobsShouldEnforceGroupConsistency() {
        seedDocument(5002L, 2301L, 2302L, 3202L);

        assertThatThrownBy(() -> insertIngestionJob(4001L, 5002L, 2302L)).isInstanceOf(DataAccessException.class);
    }

    @Test
    void shouldCommitOrRollBackDocumentAndJobTogether() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            seedDocument(5101L, 2401L, 2402L, 3301L);
            events.publishEvent(new DocumentIngestionRequestedEvent(5101L, 2401L));
            assertThat(jobCount(5101L)).isEqualTo(1);
            status.setRollbackOnly();
        });
        assertThat(jobCount(5101L)).isZero();
        assertThat(jdbcTemplate.queryForObject("select count(*) from documents where id = 5101", Integer.class)).isZero();

        seedProcessingDocument(5102L, 2411L, 3311L);
        tx.executeWithoutResult(status -> {
            events.publishEvent(new DocumentIngestionRequestedEvent(5102L, 2411L));
            events.publishEvent(new DocumentIngestionRequestedEvent(5102L, 2411L));
        });
        assertThat(jobCount(5102L)).isEqualTo(1);
        IngestionJobQueue.Job job = queue.claim();
        assertThat(job.documentId()).isEqualTo(5102L);
        assertThat(queue.claim()).isNull();
        queue.execute(job);
        queue.execute(job);
        verify(documentIngestionProcessor, times(1)).process(5102L, 2411L);
        assertThat(documentStatus(5102L)).isEqualTo("READY");
        assertThat(jobStatus(job)).isEqualTo("SUCCEEDED");
    }

    @Test
    void shouldRecoverExpiredClaimAndFencePreviousOwner() {
        seedProcessingDocument(5103L, 2421L, 3321L);
        assertThat(queue.recoverUnqueuedDocuments()).isEqualTo(1);
        assertThat(queue.recoverUnqueuedDocuments()).isZero();
        IngestionJobQueue.Job abandoned = queue.claim();
        jdbcTemplate.update("update ingestion_jobs set lease_until = now() - interval '1 minute' where id = ?", abandoned.id());
        IngestionJobQueue.Job replacement = queue.claim();
        assertThat(replacement.id()).isEqualTo(abandoned.id());
        assertThat(replacement.attempt()).isEqualTo(2);
        queue.execute(abandoned);
        queue.fail(abandoned, new IllegalStateException("late failure"));
        verifyNoInteractions(documentIngestionProcessor);
        queue.execute(replacement);
        assertThat(documentStatus(5103L)).isEqualTo("READY");
    }

    @Test
    void shouldNotReclaimLockedAttemptEvenWhenItsLeaseExpired() throws Exception {
        seedProcessingDocument(5104L, 2431L, 3331L);
        queue.recoverUnqueuedDocuments();
        IngestionJobQueue.Job job = queue.claim();
        jdbcTemplate.update("update ingestion_jobs set lease_until = now() - interval '1 minute' where id = ?", job.id());
        java.util.concurrent.CountDownLatch entered = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        doAnswer(invocation -> {
            entered.countDown();
            if (!release.await(5, java.util.concurrent.TimeUnit.SECONDS)) {
                throw new IllegalStateException("test worker did not release");
            }
            return null;
        }).when(documentIngestionProcessor).process(5104L, 2431L);
        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var work = executor.submit(() -> queue.execute(job));
            try {
                assertThat(entered.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                assertThat(queue.claim()).isNull();
            } finally {
                release.countDown();
            }
            work.get(5, java.util.concurrent.TimeUnit.SECONDS);
        }
        assertThat(jobStatus(job)).isEqualTo("SUCCEEDED");
    }

    @Test
    void shouldPersistBackoffAndFailAfterThreeAttempts() {
        seedProcessingDocument(5105L, 2441L, 3341L);
        queue.recoverUnqueuedDocuments();
        doThrow(new IllegalStateException("index unavailable")).when(documentIngestionProcessor).process(5105L, 2441L);
        for (int attempt = 1; attempt <= 3; attempt++) {
            IngestionJobQueue.Job job = queue.claim();
            assertThat(job.attempt()).isEqualTo(attempt);
            assertThatThrownBy(() -> queue.execute(job)).isInstanceOf(IllegalStateException.class);
            queue.fail(job, new IllegalStateException("index unavailable"));
            assertThat(queue.claim()).isNull();
            if (attempt < 3) {
                assertThat(jobStatus(job)).isEqualTo("PENDING");
                assertThat(documentStatus(5105L)).isEqualTo("PROCESSING");
                jdbcTemplate.update("update ingestion_jobs set next_retry_at = now() where id = ?", job.id());
            } else {
                assertThat(jobStatus(job)).isEqualTo("FAILED");
                assertThat(documentStatus(5105L)).isEqualTo("FAILED");
            }
        }
        verify(vectorIngestionService, times(4)).deleteDocumentVectors(5105L);
    }

    @Test
    void shouldSkipDeletedDocumentAndRejectCleanupFailure() {
        seedProcessingDocument(5106L, 2451L, 3351L);
        queue.recoverUnqueuedDocuments();
        IngestionJobQueue.Job job = queue.claim();
        jdbcTemplate.update("update documents set deleted = true where id = 5106");
        queue.execute(job);
        verifyNoInteractions(documentIngestionProcessor, vectorIngestionService, elasticsearchChunkIndexService);
        assertThat(jobStatus(job)).isEqualTo("FAILED");

        seedProcessingDocument(5107L, 2461L, 3361L);
        queue.recoverUnqueuedDocuments();
        IngestionJobQueue.Job failing = queue.claim();
        doThrow(new IllegalStateException("cleanup unavailable")).when(vectorIngestionService).deleteDocumentVectors(5107L);
        assertThatThrownBy(() -> queue.execute(failing)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(documentIngestionProcessor);
        jdbcTemplate.update("update documents set deleted = true where id = 5107");
        queue.fail(failing, new IllegalStateException("cleanup unavailable"));
    }

    private void seedProcessingDocument(long documentId, long groupId, long userId) {
        seedDocument(documentId, groupId, groupId + 1, userId);
        jdbcTemplate.update("update documents set status = 'PROCESSING' where id = ?", documentId);
    }

    private int jobCount(long documentId) {
        return jdbcTemplate.queryForObject("select count(*) from ingestion_jobs where document_id = ?", Integer.class, documentId);
    }

    private String documentStatus(long documentId) {
        return jdbcTemplate.queryForObject("select status from documents where id = ?", String.class, documentId);
    }

    private String jobStatus(IngestionJobQueue.Job job) {
        return jdbcTemplate.queryForObject("select status from ingestion_jobs where id = ?", String.class, job.id());
    }

    @AfterAll
    void cleanupDatabase() {
        closeDataSource();
        dropDatabase(TEST_DATABASE);
    }

    private boolean hasTable(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                select count(*)
                from information_schema.tables
                where table_schema = 'public' and table_name = ?
                """,
                Integer.class,
                tableName
        );
        return count != null && count > 0;
    }

    private boolean hasIndex(String indexName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                select count(*)
                from pg_indexes
                where schemaname = 'public' and indexname = ?
                """,
                Integer.class,
                indexName
        );
        return count != null && count > 0;
    }

    private boolean hasColumn(String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                select count(*)
                from information_schema.columns
                where table_schema = 'public'
                  and table_name = ?
                  and column_name = ?
                """,
                Integer.class,
                tableName,
                columnName
        );
        return count != null && count > 0;
    }

    private boolean hasConstraint(String constraintName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                select count(*)
                from pg_constraint
                where conname = ?
                """,
                Integer.class,
                constraintName
        );
        return count != null && count > 0;
    }

    private boolean hasUserCode(String userCode) {
        Integer count = jdbcTemplate.queryForObject(
                """
                select count(*)
                from users
                where user_code = ?
                """,
                Integer.class,
                userCode
        );
        return count != null && count > 0;
    }

    private void insertUserWithRoleAndStatus(long userId, String systemRole, String status) {
        jdbcTemplate.update(
                """
                insert into users (
                    id, user_code, username, email, display_name,
                    system_role, status, must_change_password, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, true, now(), now())
                """,
                userId,
                "u" + userId,
                "u" + userId,
                "u" + userId + "@local.springmind.test",
                "user-" + userId,
                systemRole,
                status
        );
    }

    private boolean hasTaskFourSeedMemberships() {
        Integer count = jdbcTemplate.queryForObject(
                """
                select count(*)
                from group_memberships
                where (user_id = 1001 and group_id = 2001 and role = 'OWNER')
                   or (user_id = 1002 and group_id = 2001 and role = 'MEMBER')
                   or (user_id = 1002 and group_id = 2002 and role = 'OWNER')
                """,
                Integer.class
        );
        return count != null && count == 3;
    }

    private boolean hasTaskFourPendingInvitation() {
        Integer count = jdbcTemplate.queryForObject(
                """
                select count(*)
                from group_invitations
                where group_id = 2002
                  and inviter_user_id = 1002
                  and invitee_user_id = 1001
                  and status = 'PENDING'
                """,
                Integer.class
        );
        return count != null && count == 1;
    }

    private String currentFlywayVersion() {
        return jdbcTemplate.queryForObject(
                """
                select version
                from flyway_schema_history
                where success = true
                order by installed_rank desc
                limit 1
                """,
                String.class
        );
    }

    private void seedDocument(long documentId, long documentGroupId, long otherGroupId, long userId) {
        jdbcTemplate.update(
                """
                insert into users (
                    id, user_code, username, email, display_name, password_hash,
                    system_role, status, must_change_password, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now())
                """,
                userId,
                "u" + userId,
                "u" + userId,
                "u" + userId + "@local.springmind.test",
                "user-" + userId,
                INIT_PASSWORD_HASH,
                "USER",
                "ACTIVE",
                true
        );
        insertGroup(documentGroupId, userId);
        insertGroup(otherGroupId, userId);
        jdbcTemplate.update(
                """
                insert into documents (
                    id, group_id, uploader_user_id, file_name, file_ext, content_type, file_size,
                    storage_bucket, storage_object_key, status, deleted, uploaded_at, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, false, now(), now(), now())
                """,
                documentId,
                documentGroupId,
                userId,
                "doc-" + documentId + ".txt",
                "txt",
                "text/plain",
                128L,
                "bucket",
                "object-" + documentId,
                "UPLOADED"
        );
    }

    private void insertGroup(long groupId, long ownerUserId) {
        jdbcTemplate.update(
                """
                insert into groups (
                    id, group_code, group_name, status, owner_user_id, created_at, updated_at
                ) values (?, ?, ?, ?, ?, now(), now())
                """,
                groupId,
                "g" + groupId,
                "group-" + groupId,
                "ACTIVE",
                ownerUserId
        );
        jdbcTemplate.update(
                """
                insert into group_memberships (
                    user_id, group_id, role, created_at, updated_at
                ) values (?, ?, ?, now(), now())
                """,
                ownerUserId,
                groupId,
                "OWNER"
        );
    }

    private void insertChunk(long documentId, long groupId, int chunkIndex) {
        jdbcTemplate.update(
                """
                insert into document_chunks (
                    document_id, group_id, chunk_index, chunk_text, created_at, updated_at
                ) values (?, ?, ?, ?, now(), now())
                """,
                documentId,
                groupId,
                chunkIndex,
                "chunk-" + chunkIndex
        );
    }

    private void insertIngestionJob(long jobId, long documentId, long groupId) {
        jdbcTemplate.update(
                """
                insert into ingestion_jobs (
                    id, document_id, group_id, job_type, status, retry_count, max_retries, created_at, updated_at
                ) values (?, ?, ?, ?, ?, 0, 3, now(), now())
                """,
                jobId,
                documentId,
                groupId,
                "INGEST_DOCUMENT",
                "PENDING"
        );
    }

    private void closeDataSource() {
        if (dataSource instanceof HikariDataSource hikariDataSource) {
            hikariDataSource.close();
        }
    }

    private static void createDatabase(String databaseName) {
        executeOnAdminDatabase(connection -> connection.createStatement().execute("create database " + databaseName));
    }

    private static void dropDatabase(String databaseName) {
        executeOnAdminDatabase(connection -> {
            connection.createStatement().execute(
                    """
                    select pg_terminate_backend(pid)
                    from pg_stat_activity
                    where datname = '%s' and pid <> pg_backend_pid()
                    """.formatted(databaseName)
            );
            connection.createStatement().execute("drop database if exists " + databaseName);
        });
    }

    private static void executeOnAdminDatabase(SqlConsumer sqlConsumer) {
        try (Connection connection = DriverManager.getConnection(
                ADMIN_DATABASE_URL,
                DATABASE_USERNAME,
                DATABASE_PASSWORD
        )) {
            sqlConsumer.accept(connection);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to manage temporary Flyway database", exception);
        }
    }

    @FunctionalInterface
    private interface SqlConsumer {
        void accept(Connection connection) throws SQLException;
    }
}
