package com.yche.springmind.groupmembership.service;

import com.yche.springmind.common.enums.GroupInvitationStatus;
import com.yche.springmind.common.enums.GroupRole;
import com.yche.springmind.common.exception.BusinessException;
import com.yche.springmind.groupmembership.mapper.GroupMembershipMapper;
import com.yche.springmind.identity.service.CurrentUserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 查询并校验用户的知识组成员关系，为文档和问答模块提供权限依据。
 *
 * <p>位于业务服务层：编排领域操作和基础设施调用，并集中维护事务、权限校验及失败处理边界。</p>
 */
@Service
public class GroupMembershipService {

    private static final String NON_MEMBER_MESSAGE = "当前用户不是目标群组成员";
    private static final String NON_OWNER_MESSAGE = "当前用户不是目标群组 OWNER";
    private static final String EXISTING_MEMBER_MESSAGE = "被邀请人已是群组成员";
    private static final String EXISTING_PENDING_INVITATION_MESSAGE = "已存在待处理邀请";
    private final GroupMembershipMapper groupMembershipMapper;
    private final CurrentUserService currentUserService;

    /**
     * 创建并初始化 {@link GroupMembershipService}，保存该组件运行所需的依赖与配置。
     * <p>
     * 实现要点：解析并确认当前登录用户。
     *
     * @param groupMembershipMapper 方法参数 {@code groupMembershipMapper}
     * @param currentUserService 方法参数 {@code currentUserService}
     */
    public GroupMembershipService(
            GroupMembershipMapper groupMembershipMapper,
            CurrentUserService currentUserService
    ) {
        this.groupMembershipMapper = groupMembershipMapper;
        this.currentUserService = currentUserService;
    }

    /**
     * 执行 {@code listVisibleGroups} 对应的业务步骤。
     * <p>
     * 实现要点：解析并确认当前登录用户；先校验输入、状态或业务边界。
     *
     * @param request 已经通过控制器基础校验的请求对象
     * @return 查询得到的Visible群组列表结果
     */
    public GroupQueryResult listVisibleGroups(HttpServletRequest request) {
        CurrentUserService.CurrentUser currentUser = currentUserService.requireBusinessUser(request);
        return new GroupQueryResult(
                toVisibleGroups(groupMembershipMapper.selectOwnedGroupsByUserId(currentUser.userId())),
                toVisibleGroups(groupMembershipMapper.selectJoinedGroupsByUserId(currentUser.userId())),
                toPendingInvitations(groupMembershipMapper.selectPendingInvitationsByInviteeUserId(currentUser.userId()))
        );
    }

    /**
     * 执行 {@code requireCurrentUserMember} 对应的业务步骤。
     * <p>
     * 实现要点：先校验输入、状态或业务边界。
     *
     * @param request 已经通过控制器基础校验的请求对象
     * @param groupId 群组唯一标识
     * @return 方法执行结果，具体结构由返回类型 {@code CurrentUserService.CurrentUser} 表示
     */
    public CurrentUserService.CurrentUser requireCurrentUserMember(HttpServletRequest request, Long groupId) {
        return requireGroupReadable(request, groupId);
    }

    /**
     * 执行 {@code requireGroupReadable} 对应的业务步骤。
     * <p>
     * 实现要点：先校验输入、状态或业务边界；解析并确认当前登录用户。
     *
     * @param request 已经通过控制器基础校验的请求对象
     * @param groupId 群组唯一标识
     * @return 方法执行结果，具体结构由返回类型 {@code CurrentUserService.CurrentUser} 表示
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    public CurrentUserService.CurrentUser requireGroupReadable(HttpServletRequest request, Long groupId) {
        CurrentUserService.CurrentUser currentUser = currentUserService.requireBusinessUser(request);
        String role = groupMembershipMapper.selectActiveMembershipRole(currentUser.userId(), requireGroupId(groupId));
        if (role == null) {
            throw new BusinessException(NON_MEMBER_MESSAGE);
        }
        return currentUser;
    }

    /**
     * 执行 {@code requireGroupOwner} 对应的业务步骤。
     * <p>
     * 实现要点：先校验输入、状态或业务边界；解析并确认当前登录用户；校验群组成员关系和角色权限。
     *
     * @param request 已经通过控制器基础校验的请求对象
     * @param groupId 群组唯一标识
     * @return 方法执行结果，具体结构由返回类型 {@code CurrentUserService.CurrentUser} 表示
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    public CurrentUserService.CurrentUser requireGroupOwner(HttpServletRequest request, Long groupId) {
        CurrentUserService.CurrentUser currentUser = currentUserService.requireBusinessUser(request);
        String role = groupMembershipMapper.selectActiveMembershipRole(currentUser.userId(), requireGroupId(groupId));
        if (role == null) {
            throw new BusinessException(NON_MEMBER_MESSAGE);
        }
        if (!GroupRole.OWNER.name().equals(role)) {
            throw new BusinessException(NON_OWNER_MESSAGE);
        }
        return currentUser;
    }

    /**
     * 执行 {@code createPendingInvitation} 对应的业务步骤。
     * <p>
     * 实现要点：使用事务保证多次数据库操作的一致性；先校验输入、状态或业务边界。
     *
     * @param request 已经通过控制器基础校验的请求对象
     * @param groupId 群组唯一标识
     * @param inviteeUserId invitee用户唯一标识
     */
    @Transactional
    public void createPendingInvitation(HttpServletRequest request, Long groupId, Long inviteeUserId) {
        Long requiredGroupId = requireGroupId(groupId);
        Long requiredInviteeUserId = requireUserId(inviteeUserId);
        CurrentUserService.CurrentUser currentUser = requireGroupOwner(request, requiredGroupId);
        rejectDuplicateInvitationTarget(requiredGroupId, requiredInviteeUserId);
        groupMembershipMapper.insertPendingInvitation(
                requiredGroupId,
                currentUser.userId(),
                requiredInviteeUserId,
                GroupInvitationStatus.PENDING.name()
        );
    }

    /**
     * 执行 {@code rejectDuplicateInvitationTarget} 对应的业务步骤。
     *
     * @param groupId 群组唯一标识
     * @param inviteeUserId invitee用户唯一标识
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    private void rejectDuplicateInvitationTarget(Long groupId, Long inviteeUserId) {
        if (hasRows(groupMembershipMapper.countMembershipByGroupIdAndUserId(groupId, inviteeUserId))) {
            throw new BusinessException(EXISTING_MEMBER_MESSAGE);
        }
        if (hasRows(groupMembershipMapper.countPendingInvitation(groupId, inviteeUserId))) {
            throw new BusinessException(EXISTING_PENDING_INVITATION_MESSAGE);
        }
    }

    /**
     * 执行 {@code requireGroupId} 对应的业务步骤。
     * <p>
     * 实现要点：先校验输入、状态或业务边界。
     *
     * @param groupId 群组唯一标识
     * @return 计算或处理得到的数值结果
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    private Long requireGroupId(Long groupId) {
        if (groupId == null || groupId <= 0) {
            throw new BusinessException("groupId 非法");
        }
        return groupId;
    }

    /**
     * 执行 {@code requireUserId} 对应的业务步骤。
     * <p>
     * 实现要点：先校验输入、状态或业务边界。
     *
     * @param userId 用户唯一标识
     * @return 计算或处理得到的数值结果
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    private Long requireUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BusinessException("被邀请用户非法");
        }
        return userId;
    }

    /**
     * 判断当前对象是否具有 {@code rows} 特征。
     *
     * @param count 方法参数 {@code count}
     * @return 满足条件时返回 {@code true}，否则返回 {@code false}
     */
    private boolean hasRows(Long count) {
        return count != null && count > 0;
    }

    /**
     * 执行 {@code toVisibleGroups} 对应的业务步骤。
     *
     * @param rows 方法参数 {@code rows}
     * @return 符合条件的结果集合；无结果时返回空集合
     */
    private List<VisibleGroup> toVisibleGroups(List<Map<String, Object>> rows) {
        return rows.stream().map(this::toVisibleGroup).toList();
    }

    /**
     * 执行 {@code toPendingInvitations} 对应的业务步骤。
     *
     * @param rows 方法参数 {@code rows}
     * @return 符合条件的结果集合；无结果时返回空集合
     */
    private List<PendingInvitation> toPendingInvitations(List<Map<String, Object>> rows) {
        return rows.stream().map(this::toPendingInvitation).toList();
    }

    /**
     * 执行 {@code toVisibleGroup} 对应的业务步骤。
     *
     * @param row 方法参数 {@code row}
     * @return 方法执行结果，具体结构由返回类型 {@code VisibleGroup} 表示
     */
    private VisibleGroup toVisibleGroup(Map<String, Object> row) {
        Number groupId = (Number) row.get("groupId");
        return new VisibleGroup(groupId.longValue(), String.valueOf(row.get("groupCode")), String.valueOf(row.get("groupName")));
    }

    /**
     * 执行 {@code toPendingInvitation} 对应的业务步骤。
     *
     * @param row 方法参数 {@code row}
     * @return 方法执行结果，具体结构由返回类型 {@code PendingInvitation} 表示
     */
    private PendingInvitation toPendingInvitation(Map<String, Object> row) {
        return new PendingInvitation(
                toLong(row.get("invitationId")),
                toLong(row.get("groupId")),
                String.valueOf(row.get("groupName")),
                toLong(row.get("inviterUserId")),
                String.valueOf(row.get("inviterDisplayName")),
                String.valueOf(row.get("status"))
        );
    }

    /**
     * 执行 {@code toLong} 对应的业务步骤。
     *
     * @param value 方法参数 {@code value}
     * @return 计算或处理得到的数值结果
     */
    private Long toLong(Object value) {
        return ((Number) value).longValue();
    }

    /**
     * 汇总当前用户可见知识组、邀请和相关查询结果。
     *
     * <p>仅在 {@code GroupMembershipService} 的实现过程中使用，用不可变数据结构收拢中间结果，避免参数和值的含义混淆。</p>
     */
    public record GroupQueryResult(
            List<VisibleGroup> ownedGroups,
            List<VisibleGroup> joinedGroups,
            List<PendingInvitation> pendingInvitations
    ) {
    }

    /**
     * 表示当前用户有权访问的一个知识组摘要。
     *
     * <p>仅在 {@code GroupMembershipService} 的实现过程中使用，用不可变数据结构收拢中间结果，避免参数和值的含义混淆。</p>
     */
    public record VisibleGroup(Long groupId, String groupCode, String groupName) {
    }

    /**
     * 表示当前用户尚未处理的一条知识组邀请。
     *
     * <p>仅在 {@code GroupMembershipService} 的实现过程中使用，用不可变数据结构收拢中间结果，避免参数和值的含义混淆。</p>
     */
    public record PendingInvitation(
            Long invitationId,
            Long groupId,
            String groupName,
            Long inviterUserId,
            String inviterDisplayName,
            String status
    ) {
    }
}
