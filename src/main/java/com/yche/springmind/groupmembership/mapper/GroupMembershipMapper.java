package com.yche.springmind.groupmembership.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 定义知识组、成员关系和邀请记录的持久化操作。
 *
 * <p>位于数据访问层：由 MyBatis 映射数据库操作，上层服务通过该接口读写持久化数据。</p>
 */
@Mapper
public interface GroupMembershipMapper {

    /**
     * 访问数据库，完成 {@code selectOwnedGroupsByUserId} 对应的持久化操作。
     *
     * @param userId 用户唯一标识
     * @return 符合条件的结果集合；无结果时返回空集合
     */
    List<Map<String, Object>> selectOwnedGroupsByUserId(@Param("userId") Long userId);

    /**
     * 访问数据库，完成 {@code selectJoinedGroupsByUserId} 对应的持久化操作。
     *
     * @param userId 用户唯一标识
     * @return 符合条件的结果集合；无结果时返回空集合
     */
    List<Map<String, Object>> selectJoinedGroupsByUserId(@Param("userId") Long userId);

    /**
     * 访问数据库，完成 {@code selectPendingInvitationsByInviteeUserId} 对应的持久化操作。
     *
     * @param inviteeUserId invitee用户唯一标识
     * @return 符合条件的结果集合；无结果时返回空集合
     */
    List<Map<String, Object>> selectPendingInvitationsByInviteeUserId(@Param("inviteeUserId") Long inviteeUserId);

    /**
     * 访问数据库，完成 {@code selectActiveMembershipRole} 对应的持久化操作。
     *
     * @param userId 用户唯一标识
     * @param groupId 群组唯一标识
     * @return 处理后得到的字符串结果
     */
    String selectActiveMembershipRole(
            @Param("userId") Long userId,
            @Param("groupId") Long groupId
    );

    /**
     * 访问数据库，完成 {@code countActiveMembership} 对应的持久化操作。
     *
     * @param userId 用户唯一标识
     * @param groupId 群组唯一标识
     * @return 计算或处理得到的数值结果
     */
    Long countActiveMembership(
            @Param("userId") Long userId,
            @Param("groupId") Long groupId
    );

    /**
     * 访问数据库，完成 {@code countMembershipByGroupIdAndUserId} 对应的持久化操作。
     *
     * @param groupId 群组唯一标识
     * @param userId 用户唯一标识
     * @return 计算或处理得到的数值结果
     */
    Long countMembershipByGroupIdAndUserId(
            @Param("groupId") Long groupId,
            @Param("userId") Long userId
    );

    /**
     * 访问数据库，完成 {@code countPendingInvitation} 对应的持久化操作。
     *
     * @param groupId 群组唯一标识
     * @param inviteeUserId invitee用户唯一标识
     * @return 计算或处理得到的数值结果
     */
    Long countPendingInvitation(
            @Param("groupId") Long groupId,
            @Param("inviteeUserId") Long inviteeUserId
    );

    /**
     * 访问数据库，完成 {@code countUserById} 对应的持久化操作。
     *
     * @param userId 用户唯一标识
     * @return 计算或处理得到的数值结果
     */
    Long countUserById(@Param("userId") Long userId);

    /**
     * 访问数据库，完成 {@code insertGroupReturningId} 对应的持久化操作。
     *
     * @param groupCode 方法参数 {@code groupCode}
     * @param groupName 方法参数 {@code groupName}
     * @param description 方法参数 {@code description}
     * @param ownerUserId owner用户唯一标识
     * @param status 目标业务状态
     * @return 计算或处理得到的数值结果
     */
    Long insertGroupReturningId(
            @Param("groupCode") String groupCode,
            @Param("groupName") String groupName,
            @Param("description") String description,
            @Param("ownerUserId") Long ownerUserId,
            @Param("status") String status
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

    /**
     * 访问数据库，完成 {@code insertPendingInvitation} 对应的持久化操作。
     *
     * @param groupId 群组唯一标识
     * @param inviterUserId inviter用户唯一标识
     * @param inviteeUserId invitee用户唯一标识
     * @param status 目标业务状态
     * @return 计算或处理得到的数值结果
     */
    int insertPendingInvitation(
            @Param("groupId") Long groupId,
            @Param("inviterUserId") Long inviterUserId,
            @Param("inviteeUserId") Long inviteeUserId,
            @Param("status") String status
    );

    /**
     * 访问数据库，完成 {@code insertPendingInvitationReturningId} 对应的持久化操作。
     *
     * @param groupId 群组唯一标识
     * @param inviterUserId inviter用户唯一标识
     * @param inviteeUserId invitee用户唯一标识
     * @param status 目标业务状态
     * @return 计算或处理得到的数值结果
     */
    Long insertPendingInvitationReturningId(
            @Param("groupId") Long groupId,
            @Param("inviterUserId") Long inviterUserId,
            @Param("inviteeUserId") Long inviteeUserId,
            @Param("status") String status
    );

    /**
     * 访问数据库，完成 {@code selectInvitationById} 对应的持久化操作。
     *
     * @param invitationId 邀请唯一标识
     * @return 方法执行结果，具体结构由返回类型 {@code Map&lt;String, Object&gt;} 表示
     */
    Map<String, Object> selectInvitationById(@Param("invitationId") Long invitationId);

    /**
     * 访问数据库，完成 {@code updateInvitationStatus} 对应的持久化操作。
     *
     * @param invitationId 邀请唯一标识
     * @param fromStatus 方法参数 {@code fromStatus}
     * @param toStatus 方法参数 {@code toStatus}
     * @return 计算或处理得到的数值结果
     */
    int updateInvitationStatus(
            @Param("invitationId") Long invitationId,
            @Param("fromStatus") String fromStatus,
            @Param("toStatus") String toStatus
    );

    /**
     * 访问数据库，完成 {@code selectMembersByGroupId} 对应的持久化操作。
     *
     * @param groupId 群组唯一标识
     * @return 符合条件的结果集合；无结果时返回空集合
     */
    List<Map<String, Object>> selectMembersByGroupId(@Param("groupId") Long groupId);

    /**
     * 访问数据库，完成 {@code deleteMembership} 对应的持久化操作。
     *
     * @param groupId 群组唯一标识
     * @param userId 用户唯一标识
     * @return 计算或处理得到的数值结果
     */
    int deleteMembership(
            @Param("groupId") Long groupId,
            @Param("userId") Long userId
    );
}
