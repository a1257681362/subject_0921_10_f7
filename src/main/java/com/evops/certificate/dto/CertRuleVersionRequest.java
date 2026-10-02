package com.evops.certificate.dto;

import lombok.Data;

import javax.validation.Valid;

/**
 * 新建编号规则草稿版本：提交编号参数，发布时冻结。
 */
@Data
public class CertRuleVersionRequest {
    @Valid
    private CertNumberParams params;
    private String remark;
}
