package com.yche.springmind.groupmembership.model.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 承载组主邀请用户加入知识组所需的信息。
 *
 * <p>这是接口输入契约，只承载并校验调用方提交的数据，不在对象内部实现业务流程。</p>
 */
public class CreateInvitationRequest {

    @NotNull(message = "被邀请用户不能为空")
    @Positive(message = "被邀请用户非法")
    private Long inviteeUserId;

    /**
     * 返回 {@code inviteeUserId} 对应的配置或状态值。
     *
     * @return 计算或处理得到的数值结果
     */
    public Long getInviteeUserId() {
        return inviteeUserId;
    }

    /**
     * 更新 {@code inviteeUserId} 对应的配置或状态值。
     *
     * @param inviteeUserId invitee用户唯一标识
     */
    public void setInviteeUserId(Long inviteeUserId) {
        this.inviteeUserId = inviteeUserId;
    }
}
