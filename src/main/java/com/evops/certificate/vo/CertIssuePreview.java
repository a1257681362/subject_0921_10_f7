package com.evops.certificate.vo;

import lombok.Data;

import java.util.List;

/**
 * 发证预检：可发 / 跳过逐条列出，并给出编号口径（规则版本、年度、批次编码、流水区间）。
 */
@Data
public class CertIssuePreview {
    private String action = "ISSUE_CERT";
    private Long batchId;
    private String batchCode;
    private String batchName;
    private String projectCode;
    private String certYear;
    private String ruleCode;
    private String ruleVersionNo;
    private int totalScanned;
    private int actionableCount;
    private int skipCount;
    private String summary;
    private List<CertIssueItem> items;
}
