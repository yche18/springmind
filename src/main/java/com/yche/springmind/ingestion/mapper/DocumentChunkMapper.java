package com.yche.springmind.ingestion.mapper;

import com.yche.springmind.ingestion.model.entity.DocumentChunkEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 定义文档切片的批量写入、查询和删除操作。
 *
 * <p>位于数据访问层：由 MyBatis 映射数据库操作，上层服务通过该接口读写持久化数据。</p>
 */
@Mapper
public interface DocumentChunkMapper {

    /**
     * 访问数据库，完成 {@code deleteByDocumentId} 对应的持久化操作。
     *
     * @param documentId 文档唯一标识
     * @return 计算或处理得到的数值结果
     */
    int deleteByDocumentId(@Param("documentId") Long documentId);

    /**
     * 访问数据库，完成 {@code insert} 对应的持久化操作。
     *
     * @param chunk 方法参数 {@code chunk}
     * @return 计算或处理得到的数值结果
     */
    int insert(DocumentChunkEntity chunk);

    /**
     * 访问数据库，完成 {@code insertBatch} 对应的持久化操作。
     *
     * @param chunks 待处理的文档切片集合
     * @return 计算或处理得到的数值结果
     */
    int insertBatch(List<DocumentChunkEntity> chunks);

    /**
     * 访问数据库，完成 {@code selectByDocumentId} 对应的持久化操作。
     *
     * @param documentId 文档唯一标识
     * @return 符合条件的结果集合；无结果时返回空集合
     */
    List<DocumentChunkEntity> selectByDocumentId(@Param("documentId") Long documentId);

    /**
     * 访问数据库，完成 {@code selectReadyActiveChunksByDocumentId} 对应的持久化操作。
     *
     * @param groupId 群组唯一标识
     * @param documentId 文档唯一标识
     * @return 符合条件的结果集合；无结果时返回空集合
     */
    List<DocumentChunkEntity> selectReadyActiveChunksByDocumentId(
            @Param("groupId") Long groupId,
            @Param("documentId") Long documentId
    );

    /**
     * 访问数据库，完成 {@code selectReadyActiveChunksByIds} 对应的持久化操作。
     *
     * @param groupId 群组唯一标识
     * @param chunkIds 切片标识集合
     * @return 符合条件的结果集合；无结果时返回空集合
     */
    List<Map<String, Object>> selectReadyActiveChunksByIds(
            @Param("groupId") Long groupId,
            @Param("chunkIds") List<Long> chunkIds
    );

    /**
     * 访问数据库，完成 {@code selectQaReadyChunksByIds} 对应的持久化操作。
     *
     * @param groupId 群组唯一标识
     * @param chunkIds 切片标识集合
     * @return 符合条件的结果集合；无结果时返回空集合
     */
    List<Map<String, Object>> selectQaReadyChunksByIds(
            @Param("groupId") Long groupId,
            @Param("chunkIds") List<Long> chunkIds
    );
}
