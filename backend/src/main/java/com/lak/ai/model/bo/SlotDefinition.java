package com.lak.ai.model.bo;

import lombok.Builder;
import lombok.Data;

/**
 * 槽位定义 — 投诉工单的必填字段。
 */
@Data
@Builder
public class SlotDefinition {

    /** 槽位名称 */
    private String name;
    /** 显示名称 */
    private String label;
    /** 是否必填 */
    private boolean required;
    /** 追问话术模板 */
    private String promptTemplate;
    /** 校验正则（可选） */
    private String validationPattern;
    /** 校验失败提示 */
    private String validationMessage;
}
