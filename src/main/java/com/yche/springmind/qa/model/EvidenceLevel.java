package com.yche.springmind.qa.model;

/**
 * 定义检索证据对回答问题的支持强度，用于决定回答、提示不确定性或拒答。
 *
 * <p>用于在查询规划、证据检索和答案生成阶段之间传递结构化数据。</p>
 */
public enum EvidenceLevel {
    NONE,
    WEAK,
    PARTIAL,
    SUFFICIENT
}
