package com.yche.springmind.groupmembership.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 承载创建知识组所需的名称和描述等信息。
 *
 * <p>这是接口输入契约，只承载并校验调用方提交的数据，不在对象内部实现业务流程。</p>
 */
public class CreateGroupRequest {

    @NotBlank(message = "组名称不能为空")
    @Size(max = 128, message = "组名称不能超过 128")
    private String name;

    @Size(max = 512, message = "组描述不能超过 512")
    private String description;

    /**
     * 返回 {@code name} 对应的配置或状态值。
     *
     * @return 处理后得到的字符串结果
     */
    public String getName() {
        return name;
    }

    /**
     * 更新 {@code name} 对应的配置或状态值。
     *
     * @param name 方法参数 {@code name}
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * 返回 {@code description} 对应的配置或状态值。
     *
     * @return 处理后得到的字符串结果
     */
    public String getDescription() {
        return description;
    }

    /**
     * 更新 {@code description} 对应的配置或状态值。
     *
     * @param description 方法参数 {@code description}
     */
    public void setDescription(String description) {
        this.description = description;
    }
}
