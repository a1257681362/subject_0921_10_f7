package com.evops.certificate.vo;

import lombok.Data;

import java.util.List;

/**
 * 旧编号导入预检 / 执行结果：与其他模块批量导入同一形态。
 */
@Data
public class LegacyImportPreview {
    private String action = "IMPORT_LEGACY_CERT_NO";
    private int totalScanned;
    private int actionableCount;
    private int skipCount;
    private String summary;
    private List<Item> items;

    @Data
    public static class Item {
        private String action;
        private String certNo;
        private String name;
        private String reason;
    }
}
