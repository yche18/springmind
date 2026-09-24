package com.yche.springmind.groupmembership.service;

import com.yche.springmind.common.enums.GroupJoinRequestStatus;
import com.yche.springmind.common.enums.GroupRole;
import com.yche.springmind.common.exception.BusinessException;
import com.yche.springmind.groupmembership.mapper.GroupJoinRequestMapper;
import com.yche.springmind.groupmembership.model.dto.CreateJoinRequestRequest;
import com.yche.springmind.groupmembership.model.vo.MyJoinRequestVO;
import com.yche.springmind.groupmembership.model.vo.OwnerJoinRequestVO;
import com.yche.springmind.identity.service.CurrentUserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

/**
 * 编排加入知识组申请的创建、查询、批准和拒绝，并防止重复成员关系。
 *
 * <p>位于业务服务层：编排领域操作和基础设施调用，并集中维护事务、权限校验及失败处理边界。</p>
 */
@Service
public class GroupJoinRequestService {

    private final GroupJoinRequestMapper groupJoinRequestMapper;
    private final GroupMembershipService groupMembershipService;
    private final CurrentUserService currentUserService;

    /**
     * 创建并初始化 {@link GroupJoinRequestService}，保存该组件运行所需的依赖与配置。
     * <p>
     * 实现要点：校验群组成员关系和角色权限；解析并确认当前登录用户。
     *
     * @param groupJoinRequestMapper 方法参数 {@code groupJoinRequestMapper}
     * @param groupMembershipService 方法参数 {@code groupMembershipService}
     * @param currentUserService 方法参数 {@code currentUserService}
     */
    public GroupJoinRequestService(
            GroupJoinRequestMapper groupJoinRequestMapper,
            GroupMembershipService groupMembershipService,
            CurrentUserService currentUserService
    ) {
        this.groupJoinRequestMapper = groupJoinRequestMapper;
        this.groupMembershipService = groupMembershipService;
        this.currentUserService = currentUserService;
    }

    /**
     * 执行 {@code submitJoinRequest} 对应的业务步骤。
     * <p>
     * 实现要点：使用事务保证多次数据库操作的一致性；解析并确认当前登录用户；先校验输入、状态或业务边界。
     *
     * @param request 已经通过控制器基础校验的请求对象
     * @param joinRequestRequest 加入申请请求参数
     * @return 计算或处理得到的数值结果
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    @Transactional
    public Long submitJoinRequest(HttpServletRequest request, CreateJoinRequestRequest joinRequestRequest) {
        CurrentUserService.CurrentUser currentUser = currentUserService.requireBusinessUser(request);
        String groupCode = requireGroupCode(joinRequestRequest.getGroupCode());
        GroupSummary group = loadGroupByCode(groupCode);
        rejectExistingMembership(group.groupId(), currentUser.userId(), "该用户已经是知识库成员");
        if (hasRows(groupJoinRequestMapper.countPendingInvitation(group.groupId(), currentUser.userId()))) {
            throw new BusinessException("该知识库已有待处理邀请，请先处理邀请");
        }
        if (hasRows(groupJoinRequestMapper.countPendingJoinRequest(group.groupId(), currentUser.userId()))) {
            throw new BusinessException("该用户已有待处理加入申请，请先等待审批");
        }
        return groupJoinRequestMapper.insertPendingJoinRequestReturningId(
                group.groupId(),
                currentUser.userId(),
                GroupJoinRequestStatus.PENDING.name()
        );
    }

    /**
     * 执行 {@code listMyJoinRequests} 对应的业务步骤。
     * <p>
     * 实现要点：解析并确认当前登录用户；先校验输入、状态或业务边界。
     *
     * @param request 已经通过控制器基础校验的请求对象
     * @return 符合条件的结果集合；无结果时返回空集合
     */
    public List<MyJoinRequestVO> listMyJoinRequests(HttpServletRequest request) {
        CurrentUserService.CurrentUser currentUser = currentUserService.requireBusinessUser(request);
        return groupJoinRequestMapper.selectMyJoinRequests(currentUser.userId()).stream()
                .map(this::toMyJoinRequest)
                .toList();
    }

    /**
     * 执行 {@code listOwnerJoinRequests} 对应的业务步骤。
     * <p>
     * 实现要点：先校验输入、状态或业务边界；校验群组成员关系和角色权限。
     *
     * @param request 已经通过控制器基础校验的请求对象
     * @param groupId 群组唯一标识
     * @return 符合条件的结果集合；无结果时返回空集合
     */
    public List<OwnerJoinRequestVO> listOwnerJoinRequests(HttpServletRequest request, Long groupId) {
        Long requiredGroupId = requirePositiveId(groupId, "groupId 非法");
        groupMembershipService.requireGroupOwner(request, requiredGroupId);
        return groupJoinRequestMapper.selectPendingJoinRequestsByGroupId(requiredGroupId).stream()
                .map(this::toOwnerJoinRequest)
                .toList();
    }

    /**
     * 执行 {@code approveJoinRequest} 对应的业务步骤。
     * <p>
     * 实现要点：使用事务保证多次数据库操作的一致性；先校验输入、状态或业务边界；校验群组成员关系和角色权限。
     *
     * @param request 已经通过控制器基础校验的请求对象
     * @param groupId 群组唯一标识
     * @param requestId 申请唯一标识
     */
    @Transactional
    public void approveJoinRequest(HttpServletRequest request, Long groupId, Long requestId) {
        CurrentUserService.CurrentUser owner = requireOwnerAndLoadUser(request, groupId);
        JoinRequest joinRequest = loadJoinRequest(requestId);
        requireSameGroup(groupId, joinRequest);
        requirePending(joinRequest);
        rejectExistingMembership(joinRequest.groupId(), joinRequest.applicantUserId(), "该用户已经是知识库成员");
        groupJoinRequestMapper.insertMembership(
                joinRequest.groupId(),
                joinRequest.applicantUserId(),
                GroupRole.MEMBER.name()
        );
        updateStatus(joinRequest.requestId(), GroupJoinRequestStatus.APPROVED, owner.userId());
    }

    /**
     * 执行 {@code rejectJoinRequest} 对应的业务步骤。
     * <p>
     * 实现要点：使用事务保证多次数据库操作的一致性；先校验输入、状态或业务边界。
     *
     * @param request 已经通过控制器基础校验的请求对象
     * @param groupId 群组唯一标识
     * @param requestId 申请唯一标识
     */
    @Transactional
    public void rejectJoinRequest(HttpServletRequest request, Long groupId, Long requestId) {
        CurrentUserService.CurrentUser owner = requireOwnerAndLoadUser(request, groupId);
        JoinRequest joinRequest = loadJoinRequest(requestId);
        requireSameGroup(groupId, joinRequest);
        requirePending(joinRequest);
        updateStatus(joinRequest.requestId(), GroupJoinRequestStatus.REJECTED, owner.userId());
    }

    /**
     * 执行 {@code requireOwnerAndLoadUser} 对应的业务步骤。
     * <p>
     * 实现要点：先校验输入、状态或业务边界；校验群组成员关系和角色权限。
     *
     * @param request 已经通过控制器基础校验的请求对象
     * @param groupId 群组唯一标识
     * @return 方法执行结果，具体结构由返回类型 {@code CurrentUserService.CurrentUser} 表示
     */
    private CurrentUserService.CurrentUser requireOwnerAndLoadUser(HttpServletRequest request, Long groupId) {
        Long requiredGroupId = requirePositiveId(groupId, "groupId 非法");
        return groupMembershipService.requireGroupOwner(request, requiredGroupId);
    }

    /**
     * 执行 {@code requireGroupCode} 对应的业务步骤。
     * <p>
     * 实现要点：先校验输入、状态或业务边界。
     *
     * @param groupCode 方法参数 {@code groupCode}
     * @return 处理后得到的字符串结果
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    private String requireGroupCode(String groupCode) {
        if (!StringUtils.hasText(groupCode)) {
            throw new BusinessException("组织 ID 不能为空");
        }
        return groupCode.trim();
    }

    /**
     * 执行 {@code loadGroupByCode} 对应的业务步骤。
     *
     * @param groupCode 方法参数 {@code groupCode}
     * @return 查询得到的群组按编码结果
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    private GroupSummary loadGroupByCode(String groupCode) {
        Map<String, Object> row = groupJoinRequestMapper.selectActiveGroupByCode(groupCode);
        if (row == null) {
            throw new BusinessException("组织 ID 不存在");
        }
        return new GroupSummary(toLong(row.get("groupId")));
    }

    /**
     * 执行 {@code loadJoinRequest} 对应的业务步骤。
     * <p>
     * 实现要点：先校验输入、状态或业务边界。
     *
     * @param requestId 申请唯一标识
     * @return 查询得到的加入申请结果
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    private JoinRequest loadJoinRequest(Long requestId) {
        Long requiredRequestId = requirePositiveId(requestId, "申请ID非法");
        Map<String, Object> row = groupJoinRequestMapper.selectJoinRequestById(requiredRequestId);
        if (row == null) {
            throw new BusinessException("申请不存在");
        }
        return new JoinRequest(
                toLong(row.get("requestId")),
                toLong(row.get("groupId")),
                toLong(row.get("applicantUserId")),
                String.valueOf(row.get("status"))
        );
    }

    /**
     * 执行 {@code rejectExistingMembership} 对应的业务步骤。
     *
     * @param groupId 群组唯一标识
     * @param userId 用户唯一标识
     * @param message 方法参数 {@code message}
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    private void rejectExistingMembership(Long groupId, Long userId, String message) {
        if (hasRows(groupJoinRequestMapper.countActiveMembership(groupId, userId))) {
            throw new BusinessException(message);
        }
    }

    /**
     * 执行 {@code requireSameGroup} 对应的业务步骤。
     * <p>
     * 实现要点：先校验输入、状态或业务边界。
     *
     * @param groupId 群组唯一标识
     * @param joinRequest 加入请求参数
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    private void requireSameGroup(Long groupId, JoinRequest joinRequest) {
        Long requiredGroupId = requirePositiveId(groupId, "groupId 非法");
        if (!requiredGroupId.equals(joinRequest.groupId())) {
            throw new BusinessException("申请不存在");
        }
    }

    /**
     * 执行 {@code requirePending} 对应的业务步骤。
     * <p>
     * 实现要点：先校验输入、状态或业务边界。
     *
     * @param joinRequest 加入请求参数
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    private void requirePending(JoinRequest joinRequest) {
        if (!GroupJoinRequestStatus.PENDING.name().equals(joinRequest.status())) {
            throw new BusinessException("申请已处理");
        }
    }

    /**
     * 执行 {@code updateStatus} 对应的业务步骤。
     *
     * @param requestId 申请唯一标识
     * @param status 目标业务状态
     * @param decidedByUserId decided按用户唯一标识
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    private void updateStatus(Long requestId, GroupJoinRequestStatus status, Long decidedByUserId) {
        int updated = groupJoinRequestMapper.updateJoinRequestStatus(
                requestId,
                GroupJoinRequestStatus.PENDING.name(),
                status.name(),
                decidedByUserId
        );
        if (updated == 0) {
            throw new BusinessException("申请已处理");
        }
    }

    /**
     * 执行 {@code toMyJoinRequest} 对应的业务步骤。
     *
     * @param row 方法参数 {@code row}
     * @return 方法执行结果，具体结构由返回类型 {@code MyJoinRequestVO} 表示
     */
    private MyJoinRequestVO toMyJoinRequest(Map<String, Object> row) {
        return new MyJoinRequestVO(
                toLong(row.get("requestId")),
                toLong(row.get("groupId")),
                String.valueOf(row.get("groupCode")),
                String.valueOf(row.get("groupName")),
                String.valueOf(row.get("status")),
                toLocalDateTime(row.get("createdAt")),
                toNullableLocalDateTime(row.get("decidedAt"))
        );
    }

    /**
     * 执行 {@code toOwnerJoinRequest} 对应的业务步骤。
     *
     * @param row 方法参数 {@code row}
     * @return 方法执行结果，具体结构由返回类型 {@code OwnerJoinRequestVO} 表示
     */
    private OwnerJoinRequestVO toOwnerJoinRequest(Map<String, Object> row) {
        return new OwnerJoinRequestVO(
                toLong(row.get("requestId")),
                toLong(row.get("groupId")),
                toLong(row.get("applicantUserId")),
                String.valueOf(row.get("applicantUserCode")),
                String.valueOf(row.get("applicantDisplayName")),
                String.valueOf(row.get("status")),
                toLocalDateTime(row.get("createdAt"))
        );
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
     * 执行 {@code toLocalDateTime} 对应的业务步骤。
     *
     * @param value 方法参数 {@code value}
     * @return 方法执行结果，具体结构由返回类型 {@code LocalDateTime} 表示
     */
    private LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        return ((Timestamp) value).toLocalDateTime();
    }

    /**
     * 执行 {@code toNullableLocalDateTime} 对应的业务步骤。
     *
     * @param value 方法参数 {@code value}
     * @return 方法执行结果，具体结构由返回类型 {@code LocalDateTime} 表示
     */
    private LocalDateTime toNullableLocalDateTime(Object value) {
        return value == null ? null : toLocalDateTime(value);
    }

    /**
     * 表示审批加入申请时所需的最小知识组信息。
     *
     * <p>仅在 {@code GroupJoinRequestService} 的实现过程中使用，用不可变数据结构收拢中间结果，避免参数和值的含义混淆。</p>
     */
    private record GroupSummary(Long groupId) {
    }

    /**
     * 表示审批流程中加载的一条加入申请及其当前状态。
     *
     * <p>仅在 {@code GroupJoinRequestService} 的实现过程中使用，用不可变数据结构收拢中间结果，避免参数和值的含义混淆。</p>
     */
    private record JoinRequest(Long requestId, Long groupId, Long applicantUserId, String status) {
    }
}
