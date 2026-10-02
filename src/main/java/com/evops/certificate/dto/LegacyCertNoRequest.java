package com.evops.certificate.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.util.List;

/**
 * 旧编号占用登记（两步预检）：手工排号时代已发出去、收不回的证号登记进来，
 * 系统发号主动避让；验真也能查到这些证。文件内 / 库内重复逐条跳过。
 */
@Data
public class LegacyCertNoRequest {
    private List<Item> items;
    private String remark;

    @Data
    public static class Item {
        @NotBlank(message = "旧证书编号不能为空")
        private String certNo;
        private String name;
        private String batchCode;
        /** 年度，4 位或 2 位均可，原样保存。 */
        private String certYear;
        /** 项目编码；为空按 DEFAULT。 */
        private String projectCode;
        /** 来源说明（某批次纸面台账等）。 */
        private String source;
        /** ISSUED 旧号有效（默认）/ REVOKED 旧号已作废（只占号，验真提示作废）。 */
        private String status;
    }
}
