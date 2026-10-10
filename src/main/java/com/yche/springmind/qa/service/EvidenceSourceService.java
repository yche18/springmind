package com.yche.springmind.qa.service;

import com.yche.springmind.common.exception.BusinessException;
import com.yche.springmind.groupmembership.service.GroupMembershipService;
import com.yche.springmind.ingestion.mapper.DocumentChunkMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class EvidenceSourceService {
    private final GroupMembershipService membership;
    private final DocumentChunkMapper chunks;

    public EvidenceSourceService(GroupMembershipService membership, DocumentChunkMapper chunks) {
        this.membership = membership;
        this.chunks = chunks;
    }

    public List<SourceChunk> read(HttpServletRequest request, Long groupId, Long documentId, int start, int end) {
        membership.requireGroupReadable(request, groupId);
        if (groupId == null || groupId <= 0 || documentId == null || documentId <= 0
                || start < 0 || end < start || (long) end - start >= 64) {
            throw new BusinessException("Invalid evidence range (maximum 64 chunks)");
        }
        return chunks.selectEvidenceWindow(groupId, documentId, start, end).stream()
                .map(chunk -> new SourceChunk(chunk.getId(), chunk.getChunkIndex(), chunk.getChunkText())).toList();
    }

    public record SourceChunk(Long chunkId, Integer chunkIndex, String text) { }
}
