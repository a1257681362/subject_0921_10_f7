package com.evops.certificate.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 作废记录：只追加。证书作废的经办、原因与钟点以此表为准，主表仅保留状态与摘要。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_certificate_revoke")
public class CertificateRevoke extends BaseEntity {
    private Long certificateId;
    private String certNo;
    private String name;
    private Long batchId;
    private String reason;
    private String operator;
    private LocalDateTime revokedAt;
}
