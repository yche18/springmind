package com.yche.springmind.groupmembership.model.vo;

/**
 * 表示知识组成员及其角色、账号状态等展示信息。
 *
 * <p>这是接口输出视图，用于向前端稳定地暴露所需字段，避免直接返回数据库实体。</p>
 */
public record GroupMemberVO(
        Long userId,
        String userCode,
        String displayName,
        String role
) {
}
