package com.evops.certificate.common;

/**
 * 证书编号与验真业务常量。
 */
public final class CertConstants {

    private CertConstants() {
    }

    /** 默认项目编码（批次未指定项目时）。 */
    public static final String PROJECT_DEFAULT = "DEFAULT";

    // 规则版本状态
    /** 草稿：可改编号参数，未参与发号。 */
    public static final String VERSION_DRAFT = "DRAFT";
    /** 生效：同一规则同时刻仅一版生效，发号只认生效版。 */
    public static final String VERSION_ACTIVE = "ACTIVE";
    /** 停用：被新版本发布顶替或人工停用；已发证书仍保留其版本号。 */
    public static final String VERSION_DISABLED = "DISABLED";

    // 证书状态
    /** 已发证（有效）。 */
    public static final String CERT_ISSUED = "ISSUED";
    /** 已作废：主表只切状态，作废经办与原因在 t_certificate_revoke。 */
    public static final String CERT_REVOKED = "REVOKED";

    // 验真结论（对外窗口口径，不暴露内部状态机细节过多）
    /** 有效：编号存在、姓名一致、证书在效。 */
    public static final String VERIFY_VALID = "VALID";
    /** 已作废：编号与姓名一致，但证书已作废（给用人单位明确提示）。 */
    public static final String VERIFY_REVOKED = "REVOKED";
    /** 未通过：编号不存在，或编号与姓名不一致（统一口径，防止按编号枚举姓名）。 */
    public static final String VERIFY_NOT_MATCHED = "NOT_MATCHED";
    /** 编号格式/校验码不合法（连库都不查）。 */
    public static final String VERIFY_INVALID_FORMAT = "INVALID_FORMAT";
    /** 超出小流量限流。 */
    public static final String VERIFY_RATE_LIMITED = "RATE_LIMITED";

    /** 校验码算法：ISO 7064 MOD 36-2，两位校验符（0-9A-Z）。 */
    public static final String CHECKSUM_MOD36_2 = "MOD36-2";
    /** 无校验码（不建议，仅兼容历史规则时用）。 */
    public static final String CHECKSUM_NONE = "NONE";
}
