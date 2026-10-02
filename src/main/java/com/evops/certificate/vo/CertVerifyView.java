package com.evops.certificate.vo;

import lombok.Data;

/**
 * 验真窗口返回：只回有效性与批次，不含证件号/电话等任何隐私字段。
 * - result=VALID：编号存在、姓名一致、证书有效；
 * - result=REVOKED：编号与姓名一致但已作废（明确提示，附作废时间与原因）；
 * - result=NOT_MATCHED：编号不存在或姓名不一致（统一口径，防按编号枚举姓名）；
 * - result=INVALID_FORMAT：编号格式或校验码不合法。
 */
@Data
public class CertVerifyView {
    private String result;
    private String message;
    /** 持证人姓名（仅编号+姓名命中时回显，供核对）。 */
    private String name;
    private String batchCode;
    private String batchName;
    private String certYear;
    private String issuedAt;
    /** 作废时给出明确提示。 */
    private String revokedAt;
    private String revokeReason;
}
