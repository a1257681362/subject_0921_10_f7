package com.evops.certificate.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 旧编号占用登记：手工排号时代已发出去、收不回的证书编号。
 * 系统发号主动避让这些号，从根上杜绝"流水重起撞旧号"。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_legacy_cert_no")
public class LegacyCertNo extends BaseEntity {
    private String certNo;
    private String name;
    private String batchCode;
    private String certYear;
    private String projectCode;
    private String source;
    /** ISSUED 旧号仍有效（验真可查到）/ REVOKED 旧号已作废（只占号、验真提示作废）。 */
    private String status;
    private String operator;
    private String remark;
}
