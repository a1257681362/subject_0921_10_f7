package com.evops.certificate.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 编号计数器：按规则版本 + 年度 + 批次发号。
 * 流水重起（换新规则版本）时从该版本冻结的 seqStart 起；
 * 发号后还会扫库与旧号占用表避让，保证不撞任何已发编号（含手工旧号）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_certificate_seq")
public class CertificateSeq extends BaseEntity {
    private Long ruleVersionId;
    private String certYear;
    private String batchCode;
    private Integer lastSeq;
    private Integer startSeq;
}
