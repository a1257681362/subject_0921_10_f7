package com.evops.certificate.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 按版本试算编号：给年度/批次编码/流水，按版本参数拼出完整编号，不写任何数据。
 */
@Data
public class CertNumberTrialRequest {
    /** 年度，4 位；不传按当前年。 */
    private String year;
    @NotBlank(message = "批次编码不能为空")
    private String batchCode;
    private Integer seq;
}
