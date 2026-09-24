package com.yche.springmind.document.service;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 在事务提交后接收入库事件，将耗时处理与上传请求解耦。
 *
 * <p>位于业务服务层：编排领域操作和基础设施调用，并集中维护事务、权限校验及失败处理边界。</p>
 */
@Component
public class DocumentIngestionAsyncListener {

    private final DocumentIngestionAsyncService documentIngestionAsyncService;

    /**
     * 创建并初始化 {@link DocumentIngestionAsyncListener}，保存该组件运行所需的依赖与配置。
     *
     * @param documentIngestionAsyncService 方法参数 {@code documentIngestionAsyncService}
     */
    public DocumentIngestionAsyncListener(DocumentIngestionAsyncService documentIngestionAsyncService) {
        this.documentIngestionAsyncService = documentIngestionAsyncService;
    }

    /**
     * 执行 {@code handle} 对应的业务步骤。
     * <p>
     * 实现要点：在异步执行器中处理耗时任务；使用事务保证多次数据库操作的一致性。
     *
     * @param event 方法参数 {@code event}
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(DocumentIngestionRequestedEvent event) {
        documentIngestionAsyncService.ingestDocument(event.documentId(), event.groupId());
    }
}
