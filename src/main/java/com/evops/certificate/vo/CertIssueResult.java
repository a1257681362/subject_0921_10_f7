package com.evops.certificate.vo;

import lombok.Data;

import java.util.List;

/**
 * 发证执行结果：编号按名册顺序连续分配，成功与跳过分开报。
 */
@Data
public class CertIssueResult {
    private String action = "ISSUE_CERT";
    private Long batchId;
    private String batchCode;
    private String certYear;
    private String ruleVersionNo;
    private int affectedCount;
    private int skipCount;
    private String operator;
    private String issuedAt;
    private List<String> issuedCertNos;
    private List<CertIssueItem> skipped;
}
