package com.evops.certificate.dto;

import lombok.Data;

/**
 * 证书编号参数（规则版本发布时冻结为 JSON）。
 * 编号 = prefix + 年度(yearDigits 位) + separator + 批次编码 + separator + 流水(seqLength 位) + 校验码。
 * - prefix：编号前缀（字母数字，如 ZS）；
 * - yearDigits：年度位数，2（如 26）或 4（如 2026）；
 * - separator：段间分隔符，只允许 -（连字符）或空串（连写）；
 * - seqLength：流水定宽位数，超宽自然扩展不截断；
 * - seqStart：流水起点（新版本流水重起的起点，默认 1）；
 * - checksumAlgo：末位校验码算法，MOD36-2（两位）或 NONE。
 */
@Data
public class CertNumberParams {
    private String prefix;
    private Integer yearDigits;
    private String separator;
    private Integer seqLength;
    private Integer seqStart;
    private String checksumAlgo;
}
