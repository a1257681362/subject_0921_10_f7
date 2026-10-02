package com.evops.certificate.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 证书作废：原因必填；作废只追加留记录，不可撤销、不可改。
 */
@Data
public class CertRevokeRequest {
    @NotBlank(message = "作废原因不能为空")
    private String reason;
}
