package com.yche.springmind.groupmembership.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 定义知识组加入申请的查询和状态更新操作。
 *
 * <p>位于数据访问层：由 MyBatis 映射数据库操作，上层服务通过该接口读写持久化数据。</p>
 */
@Mapper
public interface GroupJoinRequestMapper {

    /**
     * 访问数据库，完成 {@code selectActiveGroupByCode} 对应的持久化操作。
     *
     * @param groupCode 方法参数 {@code groupCode}
     * @return 方法执行结果，具体结构由返回类型 {@code Map&lt;String, Object&gt;} 表示
     */
    Map<String, Object> selectActiveGroupByCode(@Param("groupCode") String groupCode);

    /**
     * 访问数据库，完成 {@code countActiveMembership} 对应的持久化操作。
     *
     * @param groupId 群组唯一标识
     * @param userId 用户唯一标识
     * @return 计算或处理得到的数值结果
     */
    Long countActiveMembership(
            @Param("groupId") Long groupId,
            @Param("userId") Long userId
    );

    /**
     * 访问数据库，完成 {@code countPendingInvitation} 对应的持久化操作。
     *
     * @param groupId 群组唯一标识
     * @param userId 用户唯一标识
     * @return 计算或处理得到的数值结果
     */
    Long countPendingInvitation(
            @Param("groupId") Long groupId,
            @Param("userId") Long userId
    );

    /**
     * 访问数据库，完成 {@code countPendingJoinRequest} 对应的持久化操作。
     *
     * @param groupId 群组唯一标识
     * @param userId 用户唯一标识
     * @return 计算或处理得到的数值结果
     */
    Long countPendingJoinRequest(
            @Param("groupId") Long groupId,
            @Param("userId") Long userId
    );

    /**
     * 访问数据库，完成 {@code insertPendingJoinRequestReturningId} 对应的持久化操作。
     *
     * @param groupId 群组唯一标识
     * @param applicantUserId applicant用户唯一标识
     * @param status 目标业务状态
     * @return 计算或处理得到的数值结果
     */
    Long insertPendingJoinRequestReturningId(
            @Param("groupId") Long groupId,
            @Param("applicantUserId") Long applicantUserId,
            @Param("status") String status
    );

    /**
     * 访问数据库，完成 {@code selectMyJoinRequests} 对应的持久化操作。
     *
     * @param applicantUserId applicant用户唯一标识
     * @return 符合条件的结果集合；无结果时返回空集合
     */
    List<Map<String, Object>> selectMyJoinRequests(@Param("applicantUserId") Long applicantUserId);

    /**
     * 访问数据库，完成 {@code selectPendingJoinRequestsByGroupId} 对应的持久化操作。
     *
     * @param groupId 群组唯一标识
     * @return 符合条件的结果集合；无结果时返回空集合
     */
    List<Map<String, Object>> selectPendingJoinRequestsByGroupId(@Param("groupId") Long groupId);

    /**
     * 访问数据库，完成 {@code selectJoinRequestById} 对应的持久化操作。
     *
     * @param requestId 申请唯一标识
     * @return 方法执行结果，具体结构由返回类型 {@code Map&lt;String, Object&gt;} 表示
     */
    Map<String, Object> selectJoinRequestById(@Param("requestId") Long requestId);

    /**
     * 访问数据库，完成 {@code updateJoinRequestStatus} 对应的持久化操作。
     *
     * @param requestId 申请唯一标识
     * @param fromStatus 方法参数 {@code fromStatus}
     * @param toStatus 方法参数 {@code toStatus}
     * @param decidedByUserId decided按用户唯一标识
     * @return 计算或处理得到的数值结果
     */
    int updateJoinRequestStatus(
            @Param("requestId") Long requestId,
            @Param("fromStatus") String fromStatus,
            @Param("toStatus") String toStatus,
            @Param("decidedByUserId") Long decidedByUserId
    );

    /**
     * 访问数据库，完成 {@code insertMembership} 对应的持久化操作。
     *
     * @param groupId 群组唯一标识
     * @param userId 用户唯一标识
     * @param role 用户在目标作用域内的角色
     * @return 计算或处理得到的数值结果
     */
    int insertMembership(
            @Param("groupId") Long groupId,
            @Param("userId") Long userId,
            @Param("role") String role
    );
}
