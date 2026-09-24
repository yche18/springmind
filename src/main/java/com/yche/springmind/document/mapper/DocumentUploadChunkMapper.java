package com.yche.springmind.document.mapper;

import com.yche.springmind.document.model.entity.DocumentUploadChunkEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 定义分片上传过程中已接收分片记录的持久化操作。
 *
 * <p>位于数据访问层：由 MyBatis 映射数据库操作，上层服务通过该接口读写持久化数据。</p>
 */
@Mapper
public interface DocumentUploadChunkMapper {

    /**
     * 访问数据库，完成 {@code insert} 对应的持久化操作。
     *
     * @param chunk 方法参数 {@code chunk}
     * @return 计算或处理得到的数值结果
     */
    int insert(DocumentUploadChunkEntity chunk);

    /**
     * 访问数据库，完成 {@code upsert} 对应的持久化操作。
     *
     * @param chunk 方法参数 {@code chunk}
     * @return 计算或处理得到的数值结果
     */
    int upsert(DocumentUploadChunkEntity chunk);

    /**
     * 访问数据库，完成 {@code selectByUploadId} 对应的持久化操作。
     *
     * @param uploadId 分片上传任务唯一标识
     * @return 符合条件的结果集合；无结果时返回空集合
     */
    List<DocumentUploadChunkEntity> selectByUploadId(@Param("uploadId") String uploadId);
}
