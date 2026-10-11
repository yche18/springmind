package com.yche.springmind.ingestion.mapper;

import com.yche.springmind.ingestion.model.entity.DocumentChunkEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface DocumentChunkMapper {

    List<DocumentChunkEntity> selectEvidenceWindow(@Param("groupId") Long groupId,
            @Param("documentId") Long documentId, @Param("start") int start, @Param("end") int end);

    int deleteByDocumentId(@Param("documentId") Long documentId);

    int insert(DocumentChunkEntity chunk);

    int insertBatch(List<DocumentChunkEntity> chunks);

    List<DocumentChunkEntity> selectByDocumentId(@Param("documentId") Long documentId);

    List<DocumentChunkEntity> selectReadyActiveChunksByDocumentId(
            @Param("groupId") Long groupId,
            @Param("documentId") Long documentId
    );

    List<Map<String, Object>> selectReadyActiveChunksByIds(
            @Param("groupId") Long groupId,
            @Param("chunkIds") List<Long> chunkIds
    );

    List<Map<String, Object>> selectQaReadyChunksByIds(
            @Param("groupId") Long groupId,
            @Param("chunkIds") List<Long> chunkIds
    );
}
