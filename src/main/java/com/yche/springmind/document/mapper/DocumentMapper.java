package com.yche.springmind.document.mapper;

import com.yche.springmind.document.model.dto.DocumentQuery;
import com.yche.springmind.document.model.entity.DocumentEntity;
import com.yche.springmind.document.model.vo.DocumentListItemVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 定义文档主记录的查询、插入、状态更新和软删除操作。
 *
 * <p>位于数据访问层：由 MyBatis 映射数据库操作，上层服务通过该接口读写持久化数据。</p>
 */
@Mapper
public interface DocumentMapper {

    /**
     * 访问数据库，完成 {@code insert} 对应的持久化操作。
     *
     * @param document 当前处理的文档实体
     * @return 计算或处理得到的数值结果
     */
    int insert(DocumentEntity document);

    /**
     * 访问数据库，完成 {@code selectReadableDocuments} 对应的持久化操作。
     *
     * @param query 用于检索或筛选的查询条件
     * @return 符合条件的结果集合；无结果时返回空集合
     */
    List<DocumentListItemVO> selectReadableDocuments(DocumentQuery query);

    /**
     * 访问数据库，完成 {@code selectByIdAndGroupId} 对应的持久化操作。
     *
     * @param documentId 文档唯一标识
     * @param groupId 群组唯一标识
     * @return 方法执行结果，具体结构由返回类型 {@code DocumentEntity} 表示
     */
    DocumentEntity selectByIdAndGroupId(
            @Param("documentId") Long documentId,
            @Param("groupId") Long groupId
    );

    /**
     * 访问数据库，完成 {@code selectByGroupIdAndFileHash} 对应的持久化操作。
     *
     * @param groupId 群组唯一标识
     * @param fileHash 方法参数 {@code fileHash}
     * @return 方法执行结果，具体结构由返回类型 {@code DocumentEntity} 表示
     */
    DocumentEntity selectByGroupIdAndFileHash(
            @Param("groupId") Long groupId,
            @Param("fileHash") String fileHash
    );

    /**
     * 访问数据库，完成 {@code markDeleted} 对应的持久化操作。
     *
     * @param documentId 文档唯一标识
     * @param groupId 群组唯一标识
     * @return 计算或处理得到的数值结果
     */
    int markDeleted(
            @Param("documentId") Long documentId,
            @Param("groupId") Long groupId
    );

    /**
     * 访问数据库，完成 {@code updateStatus} 对应的持久化操作。
     *
     * @param documentId 文档唯一标识
     * @param groupId 群组唯一标识
     * @param status 目标业务状态
     * @param failureReason 方法参数 {@code failureReason}
     * @param processedAt 方法参数 {@code processedAt}
     * @return 计算或处理得到的数值结果
     */
    int updateStatus(
            @Param("documentId") Long documentId,
            @Param("groupId") Long groupId,
            @Param("status") String status,
            @Param("failureReason") String failureReason,
            @Param("processedAt") LocalDateTime processedAt
    );

    /**
     * 访问数据库，完成 {@code markStaleProcessingDocumentsFailed} 对应的持久化操作。
     *
     * @param fromStatus 方法参数 {@code fromStatus}
     * @param toStatus 方法参数 {@code toStatus}
     * @param failureReason 方法参数 {@code failureReason}
     * @param staleBefore 方法参数 {@code staleBefore}
     * @param processedAt 方法参数 {@code processedAt}
     * @return 计算或处理得到的数值结果
     */
    int markStaleProcessingDocumentsFailed(
            @Param("fromStatus") String fromStatus,
            @Param("toStatus") String toStatus,
            @Param("failureReason") String failureReason,
            @Param("staleBefore") LocalDateTime staleBefore,
            @Param("processedAt") LocalDateTime processedAt
    );

    /**
     * 访问数据库，完成 {@code updatePreviewText} 对应的持久化操作。
     *
     * @param documentId 文档唯一标识
     * @param groupId 群组唯一标识
     * @param previewText 方法参数 {@code previewText}
     * @return 计算或处理得到的数值结果
     */
    int updatePreviewText(
            @Param("documentId") Long documentId,
            @Param("groupId") Long groupId,
            @Param("previewText") String previewText
    );
}
