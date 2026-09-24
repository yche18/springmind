package com.yche.springmind.ingestion.mapper;

import com.yche.springmind.ingestion.model.entity.IngestionJobEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 定义文档入库任务的创建、状态变更和查询操作。
 *
 * <p>位于数据访问层：由 MyBatis 映射数据库操作，上层服务通过该接口读写持久化数据。</p>
 */
@Mapper
public interface IngestionJobMapper {

    /**
     * 访问数据库，完成 {@code insert} 对应的持久化操作。
     *
     * @param job 方法参数 {@code job}
     * @return 计算或处理得到的数值结果
     */
    int insert(IngestionJobEntity job);

    /**
     * 访问数据库，完成 {@code selectRunnableJobs} 对应的持久化操作。
     *
     * @param status 目标业务状态
     * @param now 方法参数 {@code now}
     * @param limit 方法参数 {@code limit}
     * @return 符合条件的结果集合；无结果时返回空集合
     */
    List<IngestionJobEntity> selectRunnableJobs(
            @Param("status") String status,
            @Param("now") LocalDateTime now,
            @Param("limit") int limit
    );

    /**
     * 访问数据库，完成 {@code selectById} 对应的持久化操作。
     *
     * @param jobId job唯一标识
     * @return 方法执行结果，具体结构由返回类型 {@code IngestionJobEntity} 表示
     */
    IngestionJobEntity selectById(@Param("jobId") Long jobId);

    /**
     * 访问数据库，完成 {@code claimRunning} 对应的持久化操作。
     *
     * @param jobId job唯一标识
     * @param pendingStatus 方法参数 {@code pendingStatus}
     * @param runningStatus 方法参数 {@code runningStatus}
     * @param workerId worker唯一标识
     * @param startedAt 方法参数 {@code startedAt}
     * @return 计算或处理得到的数值结果
     */
    int claimRunning(
            @Param("jobId") Long jobId,
            @Param("pendingStatus") String pendingStatus,
            @Param("runningStatus") String runningStatus,
            @Param("workerId") String workerId,
            @Param("startedAt") LocalDateTime startedAt
    );

    /**
     * 访问数据库，完成 {@code markSucceeded} 对应的持久化操作。
     *
     * @param jobId job唯一标识
     * @param runningStatus 方法参数 {@code runningStatus}
     * @param succeededStatus 方法参数 {@code succeededStatus}
     * @param workerId worker唯一标识
     * @param finishedAt 方法参数 {@code finishedAt}
     * @return 计算或处理得到的数值结果
     */
    int markSucceeded(
            @Param("jobId") Long jobId,
            @Param("runningStatus") String runningStatus,
            @Param("succeededStatus") String succeededStatus,
            @Param("workerId") String workerId,
            @Param("finishedAt") LocalDateTime finishedAt
    );

    /**
     * 访问数据库，完成 {@code markFailed} 对应的持久化操作。
     *
     * @param jobId job唯一标识
     * @param runningStatus 方法参数 {@code runningStatus}
     * @param failedStatus 方法参数 {@code failedStatus}
     * @param workerId worker唯一标识
     * @param finishedAt 方法参数 {@code finishedAt}
     * @param lastError 方法参数 {@code lastError}
     * @return 计算或处理得到的数值结果
     */
    int markFailed(
            @Param("jobId") Long jobId,
            @Param("runningStatus") String runningStatus,
            @Param("failedStatus") String failedStatus,
            @Param("workerId") String workerId,
            @Param("finishedAt") LocalDateTime finishedAt,
            @Param("lastError") String lastError
    );
}
