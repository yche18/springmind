package com.yche.springmind.document.service;

/**
 * 表示某个文档已经完成存储、需要异步执行入库处理的领域事件。
 *
 * <p>位于业务服务层：编排领域操作和基础设施调用，并集中维护事务、权限校验及失败处理边界。</p>
 */
public record DocumentIngestionRequestedEvent(
        Long documentId,
        Long groupId
) {
}
