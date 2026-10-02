package com.evops.certificate.dto;

import lombok.Data;

import java.util.List;

/**
 * 发证请求：按批次为名册行发证。
 * - batchId 必填；
 * - regNos 为空表示给批次内全部尚未发证的名册行发证；指定时只发给定报名号（须属于本批次）；
 * - 已发证名册行在预检/执行中逐条幂等跳过。
 */
@Data
public class CertIssueRequest {
    private Long batchId;
    /** 指定名册报名号子集；为空表示批次全部未发证名册行。 */
    private List<String> regNos;
    private String remark;
}
