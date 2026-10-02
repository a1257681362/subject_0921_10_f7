package com.evops.certificate.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 证书：发证即生成编号并冻结编号口径；发证后信息只读（无更新接口），
 * 作废只追加 t_certificate_revoke 并切状态。不存证件号/电话等隐私字段。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_certificate")
public class Certificate extends BaseEntity {
    private String certNo;
    private Long batchId;
    private Long registrationId;
    private String projectCode;
    /** 验真需同时提供编号 + 姓名。 */
    private String name;
    private String regNo;
    private String subject;
    /** 年度取自发证时间。 */
    private String certYear;
    private String batchCode;
    private Integer seqValue;
    /** 发证时冻结的规则口径。 */
    private Long ruleId;
    private Long ruleVersionId;
    private String ruleVersionNo;
    private Boolean checksumValid;
    /** ISSUED 已发证（有效）/ REVOKED 已作废。 */
    private String status;
    private String issuedBy;
    private LocalDateTime issuedAt;
    private LocalDateTime revokedAt;
    private String revokeReason;
    private String remark;
}
