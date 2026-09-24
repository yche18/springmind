package com.yche.springmind.ingestion.service;

/**
 * 定义从已存储文档到可检索索引的完整入库处理契约。
 *
 * <p>位于业务服务层：编排领域操作和基础设施调用，并集中维护事务、权限校验及失败处理边界。</p>
 */
public interface DocumentIngestionProcessor {

    /**
     * 执行完整文档 ETL：读取原文、解析清洗、切片持久化，并写入检索索引。
     *
     * @param documentId 文档唯一标识
     * @param groupId 群组唯一标识
     */
    void process(Long documentId, Long groupId);
}
