package com.yche.springmind.groupmembership.service;

import com.yche.springmind.common.enums.GroupInvitationStatus;
import com.yche.springmind.common.enums.GroupRole;
import com.yche.springmind.common.enums.GroupStatus;
import com.yche.springmind.common.exception.BusinessException;
import com.yche.springmind.groupmembership.mapper.GroupJoinRequestMapper;
import com.yche.springmind.groupmembership.mapper.GroupMembershipMapper;
import com.yche.springmind.groupmembership.model.dto.CreateGroupRequest;
import com.yche.springmind.groupmembership.model.dto.CreateInvitationRequest;
import com.yche.springmind.groupmembership.model.vo.GroupMemberVO;
import com.yche.springmind.identity.service.CurrentUserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 编排知识组创建、成员邀请、角色调整和移除成员等组主管理操作。
 *
 * <p>位于业务服务层：编排领域操作和基础设施调用，并集中维护事务、权限校验及失败处理边界。</p>
 */
@Service
public class GroupManagementService {

    private static final int MAX_GROUP_NAME_LENGTH = 128;
    private static final int MAX_GROUP_DESCRIPTION_LENGTH = 512;
    private final GroupMembershipMapper groupMembershipMapper;
    private final GroupJoinRequestMapper groupJoinRequestMapper;
    private final GroupMembershipService groupMembershipService;
    private final CurrentUserService currentUserService;

    /**
     * 创建并初始化 {@link GroupManagementService}，保存该组件运行所需的依赖与配置。
     * <p>
     * 实现要点：校验群组成员关系和角色权限；解析并确认当前登录用户。
     *
     * @param groupMembershipMapper 方法参数 {@code groupMembershipMapper}
     * @param groupJoinRequestMapper 方法参数 {@code groupJoinRequestMapper}
     * @param groupMembershipService 方法参数 {@code groupMembershipService}
     * @param currentUserService 方法参数 {@code currentUserService}
     */
    public GroupManagementService(
            GroupMembershipMapper groupMembershipMapper,
            GroupJoinRequestMapper groupJoinRequestMapper,
            GroupMembershipService groupMembershipService,
            CurrentUserService currentUserService
    ) {
        this.groupMembershipMapper = groupMembershipMapper;
        this.groupJoinRequestMapper = groupJoinRequestMapper;
        this.groupMembershipService = groupMembershipService;
        this.currentUserService = currentUserService;
    }

    /**
     * 执行 {@code createGroup} 对应的业务步骤。
     * <p>
     * 实现要点：使用事务保证多次数据库操作的一致性；解析并确认当前登录用户；先校验输入、状态或业务边界；校验群组成员关系和角色权限。
     *
     * @param request 已经通过控制器基础校验的请求对象
     * @param createGroupRequest 创建群组请求参数
     * @return 计算或处理得到的数值结果
     */
    @Transactional
    public Long createGroup(HttpServletRequest request, CreateGroupRequest createGroupRequest) {
        CurrentUserService.CurrentUser currentUser = currentUserService.requireBusinessUser(request);
        String groupName = requireGroupName(createGroupRequest.getName());
        String description = normalizeDescription(createGroupRequest.getDescription());
        Long groupId = groupMembershipMapper.insertGroupReturningId(
                buildGroupCode(),
                groupName,
                description,
                currentUser.userId(),
                GroupStatus.ACTIVE.name()
        );
        groupMembershipMapper.insertMembership(groupId, currentUser.userId(), GroupRole.OWNER.name());
        return groupId;
    }

    /**
     * 执行 {@code createInvitation} 对应的业务步骤。
     * <p>
     * 实现要点：使用事务保证多次数据库操作的一致性；先校验输入、状态或业务边界；校验群组成员关系和角色权限。
     *
     * @param request 已经通过控制器基础校验的请求对象
     * @param groupId 群组唯一标识
     * @param createInvitationRequest 创建邀请请求参数
     * @return 计算或处理得到的数值结果
     */
    @Transactional
    public Long createInvitation(
            HttpServletRequest request,
            Long groupId,
            CreateInvitationRequest createInvitationRequest
    ) {
        Long requiredGroupId = requirePositiveId(groupId, "groupId 非法");
        Long inviteeUserId = requirePositiveId(createInvitationRequest.getInviteeUserId(), "被邀请用户非法");
        CurrentUserService.CurrentUser currentUser = groupMembershipService.requireGroupOwner(request, requiredGroupId);
        rejectMissingUser(inviteeUserId);
        rejectDuplicateInvitationTarget(requiredGroupId, inviteeUserId);
        return groupMembershipMapper.insertPendingInvitationReturningId(
                requiredGroupId,
                currentUser.userId(),
                inviteeUserId,
                GroupInvitationStatus.PENDING.name()
        );
    }

    /**
     * 执行 {@code acceptInvitation} 对应的业务步骤。
     * <p>
     * 实现要点：使用事务保证多次数据库操作的一致性；解析并确认当前登录用户；先校验输入、状态或业务边界；校验群组成员关系和角色权限。
     *
     * @param request 已经通过控制器基础校验的请求对象
     * @param invitationId 邀请唯一标识
     */
    @Transactional
    public void acceptInvitation(HttpServletRequest request, Long invitationId) {
        CurrentUserService.CurrentUser currentUser = currentUserService.requireBusinessUser(request);
        Invitation invitation = loadInvitation(invitationId);
        requireInvitee(currentUser, invitation);
        requirePending(invitation);
        rejectExistingMembership(invitation.groupId(), invitation.inviteeUserId());
        groupMembershipMapper.insertMembership(
                invitation.groupId(),
                invitation.inviteeUserId(),
                GroupRole.MEMBER.name()
        );
        updateInvitationStatus(invitation.id(), GroupInvitationStatus.ACCEPTED);
    }

    /**
     * 执行 {@code rejectInvitation} 对应的业务步骤。
     * <p>
     * 实现要点：使用事务保证多次数据库操作的一致性；解析并确认当前登录用户；先校验输入、状态或业务边界。
     *
     * @param request 已经通过控制器基础校验的请求对象
     * @param invitationId 邀请唯一标识
     */
    @Transactional
    public void rejectInvitation(HttpServletRequest request, Long invitationId) {
        CurrentUserService.CurrentUser currentUser = currentUserService.requireBusinessUser(request);
        Invitation invitation = loadInvitation(invitationId);
        requireInvitee(currentUser, invitation);
        requirePending(invitation);
        updateInvitationStatus(invitation.id(), GroupInvitationStatus.REJECTED);
    }

    /**
     * 执行 {@code cancelInvitation} 对应的业务步骤。
     * <p>
     * 实现要点：使用事务保证多次数据库操作的一致性；校验群组成员关系和角色权限；先校验输入、状态或业务边界。
     *
     * @param request 已经通过控制器基础校验的请求对象
     * @param invitationId 邀请唯一标识
     */
    @Transactional
    public void cancelInvitation(HttpServletRequest request, Long invitationId) {
        Invitation invitation = loadInvitation(invitationId);
        groupMembershipService.requireGroupOwner(request, invitation.groupId());
        requirePending(invitation);
        updateInvitationStatus(invitation.id(), GroupInvitationStatus.CANCELED);
    }

    /**
     * 执行 {@code listMembers} 对应的业务步骤。
     * <p>
     * 实现要点：先校验输入、状态或业务边界；校验群组成员关系和角色权限。
     *
     * @param request 已经通过控制器基础校验的请求对象
     * @param groupId 群组唯一标识
     * @return 符合条件的结果集合；无结果时返回空集合
     */
    public List<GroupMemberVO> listMembers(HttpServletRequest request, Long groupId) {
        Long requiredGroupId = requirePositiveId(groupId, "groupId 非法");
        groupMembershipService.requireGroupOwner(request, requiredGroupId);
        return groupMembershipMapper.selectMembersByGroupId(requiredGroupId).stream()
                .map(this::toGroupMember)
                .toList();
    }

    /**
     * 执行 {@code removeMember} 对应的业务步骤。
     * <p>
     * 实现要点：使用事务保证多次数据库操作的一致性；先校验输入、状态或业务边界；校验群组成员关系和角色权限。
     *
     * @param request 已经通过控制器基础校验的请求对象
     * @param groupId 群组唯一标识
     * @param userId 用户唯一标识
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    @Transactional
    public void removeMember(HttpServletRequest request, Long groupId, Long userId) {
        Long requiredGroupId = requirePositiveId(groupId, "groupId 非法");
        Long requiredUserId = requirePositiveId(userId, "成员用户非法");
        groupMembershipService.requireGroupOwner(request, requiredGroupId);
        String role = groupMembershipMapper.selectActiveMembershipRole(requiredUserId, requiredGroupId);
        if (role == null) {
            throw new BusinessException("成员不存在");
        }
        if (GroupRole.OWNER.name().equals(role)) {
            throw new BusinessException("不能移除 OWNER");
        }
        groupMembershipMapper.deleteMembership(requiredGroupId, requiredUserId);
    }

    /**
     * 执行 {@code leaveGroup} 对应的业务步骤。
     * <p>
     * 实现要点：使用事务保证多次数据库操作的一致性；先校验输入、状态或业务边界；解析并确认当前登录用户；校验群组成员关系和角色权限。
     *
     * @param request 已经通过控制器基础校验的请求对象
     * @param groupId 群组唯一标识
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    @Transactional
    public void leaveGroup(HttpServletRequest request, Long groupId) {
        Long requiredGroupId = requirePositiveId(groupId, "groupId 非法");
        CurrentUserService.CurrentUser currentUser = currentUserService.requireBusinessUser(request);
        String role = groupMembershipMapper.selectActiveMembershipRole(currentUser.userId(), requiredGroupId);
        if (role == null) {
            throw new BusinessException("当前用户不是目标群组成员");
        }
        if (GroupRole.OWNER.name().equals(role)) {
            throw new BusinessException("OWNER 不能退出自己的组");
        }
        groupMembershipMapper.deleteMembership(requiredGroupId, currentUser.userId());
    }

    /**
     * 执行 {@code rejectDuplicateInvitationTarget} 对应的业务步骤。
     *
     * @param groupId 群组唯一标识
     * @param inviteeUserId invitee用户唯一标识
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    private void rejectDuplicateInvitationTarget(Long groupId, Long inviteeUserId) {
        rejectExistingMembership(groupId, inviteeUserId);
        if (hasRows(groupMembershipMapper.countPendingInvitation(groupId, inviteeUserId))) {
            throw new BusinessException("已存在待处理邀请");
        }
        if (hasRows(groupJoinRequestMapper.countPendingJoinRequest(groupId, inviteeUserId))) {
            throw new BusinessException("该用户已有待处理加入申请，请先审批申请");
        }
    }

    /**
     * 执行 {@code rejectExistingMembership} 对应的业务步骤。
     *
     * @param groupId 群组唯一标识
     * @param inviteeUserId invitee用户唯一标识
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    private void rejectExistingMembership(Long groupId, Long inviteeUserId) {
        if (hasRows(groupMembershipMapper.countMembershipByGroupIdAndUserId(groupId, inviteeUserId))) {
            throw new BusinessException("被邀请人已是群组成员");
        }
    }

    /**
     * 执行 {@code rejectMissingUser} 对应的业务步骤。
     *
     * @param userId 用户唯一标识
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    private void rejectMissingUser(Long userId) {
        if (!hasRows(groupMembershipMapper.countUserById(userId))) {
            throw new BusinessException("被邀请用户不存在");
        }
    }

    /**
     * 执行 {@code loadInvitation} 对应的业务步骤。
     * <p>
     * 实现要点：先校验输入、状态或业务边界。
     *
     * @param invitationId 邀请唯一标识
     * @return 查询得到的邀请结果
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    private Invitation loadInvitation(Long invitationId) {
        Long requiredInvitationId = requirePositiveId(invitationId, "邀请ID非法");
        Map<String, Object> row = groupMembershipMapper.selectInvitationById(requiredInvitationId);
        if (row == null) {
            throw new BusinessException("邀请不存在");
        }
        return new Invitation(
                toLong(row.get("invitationId")),
                toLong(row.get("groupId")),
                toLong(row.get("inviterUserId")),
                toLong(row.get("inviteeUserId")),
                String.valueOf(row.get("status"))
        );
    }

    /**
     * 执行 {@code requireInvitee} 对应的业务步骤。
     * <p>
     * 实现要点：先校验输入、状态或业务边界。
     *
     * @param currentUser 当前已经通过认证的用户信息
     * @param invitation 方法参数 {@code invitation}
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    private void requireInvitee(CurrentUserService.CurrentUser currentUser, Invitation invitation) {
        if (!currentUser.userId().equals(invitation.inviteeUserId())) {
            throw new BusinessException("无权处理该邀请");
        }
    }

    /**
     * 执行 {@code requirePending} 对应的业务步骤。
     * <p>
     * 实现要点：先校验输入、状态或业务边界。
     *
     * @param invitation 方法参数 {@code invitation}
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    private void requirePending(Invitation invitation) {
        if (!GroupInvitationStatus.PENDING.name().equals(invitation.status())) {
            throw new BusinessException("邀请已处理");
        }
    }

    /**
     * 执行 {@code updateInvitationStatus} 对应的业务步骤。
     *
     * @param invitationId 邀请唯一标识
     * @param status 目标业务状态
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    private void updateInvitationStatus(Long invitationId, GroupInvitationStatus status) {
        int updated = groupMembershipMapper.updateInvitationStatus(
                invitationId,
                GroupInvitationStatus.PENDING.name(),
                status.name()
        );
        if (updated == 0) {
            throw new BusinessException("邀请已处理");
        }
    }

    /**
     * 执行 {@code toGroupMember} 对应的业务步骤。
     *
     * @param row 方法参数 {@code row}
     * @return 方法执行结果，具体结构由返回类型 {@code GroupMemberVO} 表示
     */
    private GroupMemberVO toGroupMember(Map<String, Object> row) {
        return new GroupMemberVO(
                toLong(row.get("userId")),
                String.valueOf(row.get("userCode")),
                String.valueOf(row.get("displayName")),
                String.valueOf(row.get("role"))
        );
    }

    /**
     * 执行 {@code requireGroupName} 对应的业务步骤。
     * <p>
     * 实现要点：先校验输入、状态或业务边界。
     *
     * @param name 方法参数 {@code name}
     * @return 处理后得到的字符串结果
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    private String requireGroupName(String name) {
        if (!StringUtils.hasText(name)) {
            throw new BusinessException("组名称不能为空");
        }
        String trimmedName = name.trim();
        if (trimmedName.length() > MAX_GROUP_NAME_LENGTH) {
            throw new BusinessException("组名称不能超过 128");
        }
        return trimmedName;
    }

    /**
     * 执行 {@code normalizeDescription} 对应的业务步骤。
     * <p>
     * 实现要点：先校验输入、状态或业务边界。
     *
     * @param description 方法参数 {@code description}
     * @return 处理后得到的字符串结果
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    private String normalizeDescription(String description) {
        if (!StringUtils.hasText(description)) {
            return null;
        }
        String trimmedDescription = description.trim();
        if (trimmedDescription.length() > MAX_GROUP_DESCRIPTION_LENGTH) {
            throw new BusinessException("组描述不能超过 512");
        }
        return trimmedDescription;
    }

    /**
     * 执行 {@code requirePositiveId} 对应的业务步骤。
     * <p>
     * 实现要点：先校验输入、状态或业务边界。
     *
     * @param id 方法参数 {@code id}
     * @param message 方法参数 {@code message}
     * @return 计算或处理得到的数值结果
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    private Long requirePositiveId(Long id, String message) {
        if (id == null || id <= 0) {
            throw new BusinessException(message);
        }
        return id;
    }

    /**
     * 执行 {@code buildGroupCode} 对应的业务步骤。
     *
     * @return 处理后得到的字符串结果
     */
    private String buildGroupCode() {
        return "group-" + UUID.randomUUID().toString().replace("-", "");
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
     * 执行 {@code toLong} 对应的业务步骤。
     *
     * @param value 方法参数 {@code value}
     * @return 计算或处理得到的数值结果
     */
    private Long toLong(Object value) {
        return ((Number) value).longValue();
    }

    /**
     * 表示组主管理流程中加载的一条成员邀请记录。
     *
     * <p>仅在 {@code GroupManagementService} 的实现过程中使用，用不可变数据结构收拢中间结果，避免参数和值的含义混淆。</p>
     */
    private record Invitation(
            Long id,
            Long groupId,
            Long inviterUserId,
            Long inviteeUserId,
            String status
    ) {
    }
}
