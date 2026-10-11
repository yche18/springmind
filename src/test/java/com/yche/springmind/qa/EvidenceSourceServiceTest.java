package com.yche.springmind.qa;

import com.yche.springmind.common.exception.BusinessException;
import com.yche.springmind.common.exception.ForbiddenException;
import com.yche.springmind.groupmembership.service.GroupMembershipService;
import com.yche.springmind.ingestion.mapper.DocumentChunkMapper;
import com.yche.springmind.ingestion.model.entity.DocumentChunkEntity;
import com.yche.springmind.qa.service.EvidenceSourceService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class EvidenceSourceServiceTest {
    private final GroupMembershipService membership = mock(GroupMembershipService.class);
    private final DocumentChunkMapper chunks = mock(DocumentChunkMapper.class);
    private final EvidenceSourceService service = new EvidenceSourceService(membership, chunks);
    private final MockHttpServletRequest request = new MockHttpServletRequest();

    @Test
    void nonMemberCannotReadSource() {
        when(membership.requireGroupReadable(request, 2L)).thenThrow(new ForbiddenException("No access"));
        assertThatThrownBy(() -> service.read(request, 2L, 3L, 0, 2)).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(chunks);
    }

    @Test
    void invalidOrExcessiveRangeDoesNotQueryStorage() {
        assertThatThrownBy(() -> service.read(request, 2L, 3L, -1, 2)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.read(request, 2L, 3L, 0, Integer.MAX_VALUE)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(chunks);
    }

    @Test
    void memberReadsOnlyRequestedDocumentWindow() {
        DocumentChunkEntity chunk = new DocumentChunkEntity();
        chunk.setId(4L);
        chunk.setChunkIndex(1);
        chunk.setChunkText("Source content");
        when(chunks.selectEvidenceWindow(2L, 3L, 1, 2)).thenReturn(List.of(chunk));
        assertThat(service.read(request, 2L, 3L, 1, 2)).containsExactly(
                new EvidenceSourceService.SourceChunk(4L, 1, "Source content"));
        var order = inOrder(membership, chunks);
        order.verify(membership).requireGroupReadable(request, 2L);
        order.verify(chunks).selectEvidenceWindow(2L, 3L, 1, 2);
    }
}
