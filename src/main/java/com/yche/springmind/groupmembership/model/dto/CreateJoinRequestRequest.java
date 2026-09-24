package com.yche.springmind.groupmembership.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 承载用户申请加入目标知识组时提交的信息。
 *
 * <p>这是接口输入契约，只承载并校验调用方提交的数据，不在对象内部实现业务流程。</p>
 */
public class CreateJoinRequestRequest {

    @NotBlank(message = "组织 ID 不能为空")
    @Size(max = 80, message = "组织 ID 不能超过 80")
    private String groupCode;

    /**
     * 返回 {@code groupCode} 对应的配置或状态值。
     *
     * @return 处理后得到的字符串结果
     */
    public String getGroupCode() {
        return groupCode;
    }

    /**
     * 更新 {@code groupCode} 对应的配置或状态值。
     *
     * @param groupCode 方法参数 {@code groupCode}
     */
    public void setGroupCode(String groupCode) {
        this.groupCode = groupCode;
    }
}
