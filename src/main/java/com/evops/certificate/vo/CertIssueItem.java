package com.evops.certificate.vo;

import lombok.Data;

/**
 * 发证预检 / 结果逐条明细。
 */
@Data
public class CertIssueItem {
    /** ISSUE 可发 / SKIP 跳过。 */
    private String action;
    private String regNo;
    private String name;
    /** 预检时的拟发编号；跳过为空。 */
    private String plannedCertNo;
    /** 跳过/拒收原因。 */
    private String reason;

    public static CertIssueItem issue(String regNo, String name, String plannedCertNo) {
        CertIssueItem item = new CertIssueItem();
        item.action = "ISSUE";
        item.regNo = regNo;
        item.name = name;
        item.plannedCertNo = plannedCertNo;
        return item;
    }

    public static CertIssueItem skip(String regNo, String name, String reason) {
        CertIssueItem item = new CertIssueItem();
        item.action = "SKIP";
        item.regNo = regNo;
        item.name = name;
        item.reason = reason;
        return item;
    }
}
