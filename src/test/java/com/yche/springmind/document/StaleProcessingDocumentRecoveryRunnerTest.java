package com.yche.springmind.document;

import com.yche.springmind.document.service.StaleProcessingDocumentRecoveryRunner;
import com.yche.springmind.ingestion.service.IngestionJobQueue;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import static org.mockito.Mockito.*;

class StaleProcessingDocumentRecoveryRunnerTest {
    @Test
    void shouldBackfillJobsWithoutFailingOtherWorkersDocuments() {
        IngestionJobQueue queue = mock(IngestionJobQueue.class);
        new StaleProcessingDocumentRecoveryRunner(queue).run(new DefaultApplicationArguments());
        verify(queue).recoverUnqueuedDocuments();
        verifyNoMoreInteractions(queue);
    }
}
