package com.evops.certificate.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.certificate.common.CertConstants;
import com.evops.certificate.dto.LegacyCertNoRequest;
import com.evops.certificate.entity.LegacyCertNo;
import com.evops.certificate.mapper.CertificateMapper;
import com.evops.certificate.mapper.LegacyCertNoMapper;
import com.evops.certificate.vo.LegacyImportPreview;
import com.evops.common.BusinessException;
import com.evops.common.OperatorHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 旧编号占用登记：手工排号时代发出去、收不回的证号登记进来。
 * - 系统发号避让这些号（流水重起也不撞旧号）；
 * - 验真窗口可查到历史证，作废旧号给明确提示；
 * - 两步预检，文件内 / 与库内重复逐条幂等跳过；空编号、非法状态等结构性问题整批拒收。
 */
@Service
public class LegacyCertNoService {

    private final LegacyCertNoMapper legacyMapper;
    private final CertificateMapper certificateMapper;

    public LegacyCertNoService(LegacyCertNoMapper legacyMapper, CertificateMapper certificateMapper) {
        this.legacyMapper = legacyMapper;
        this.certificateMapper = certificateMapper;
    }

    @Transactional(readOnly = true)
    public LegacyImportPreview preview(LegacyCertNoRequest request) {
        List<LegacyCertNoRequest.Item> items = requireItems(request);
        Set<String> existingLegacy = loadExistingLegacy();
        Set<String> existingCert = loadExistingCert();

        List<LegacyImportPreview.Item> rows = new ArrayList<>();
        Set<String> inFile = new HashSet<>();
        int actionable = 0;
        for (int i = 0; i < items.size(); i++) {
            LegacyCertNoRequest.Item raw = items.get(i);
            String certNo = norm(raw.getCertNo());
            String name = raw.getName() == null ? null : raw.getName().trim();
            String action;
            String reason;
            if (certNo.isEmpty()) {
                action = "SKIP";
                reason = "第 " + (i + 1) + " 条旧编号为空，执行时整批拒收";
            } else if (!inFile.add(certNo)) {
                action = "SKIP";
                reason = "文件内编号重复，跳过";
            } else if (existingLegacy.contains(certNo)) {
                action = "SKIP";
                reason = "库内已登记该旧编号，幂等跳过";
            } else if (existingCert.contains(certNo)) {
                action = "SKIP";
                reason = "该编号已由系统发放，不属于旧号，跳过";
            } else if (!validStatus(raw.getStatus())) {
                action = "SKIP";
                reason = "第 " + (i + 1) + " 条状态只支持 ISSUED / REVOKED：" + raw.getStatus();
            } else {
                action = "IMPORT";
                reason = null;
                actionable++;
            }
            rows.add(row(action, certNo, name, reason));
        }
        LegacyImportPreview preview = new LegacyImportPreview();
        preview.setTotalScanned(items.size());
        preview.setActionableCount(actionable);
        preview.setSkipCount(items.size() - actionable);
        preview.setItems(rows);
        preview.setSummary("旧编号共 " + items.size() + " 条，可登记 " + actionable + " 条，跳过 "
                + (items.size() - actionable) + " 条");
        return preview;
    }

    @Transactional
    public LegacyImportPreview execute(LegacyCertNoRequest request) {
        List<LegacyCertNoRequest.Item> items = requireItems(request);
        String operator = OperatorHolder.current();
        String remark = request.getRemark();
        Set<String> existingLegacy = loadExistingLegacy();
        Set<String> existingCert = loadExistingCert();
        Set<String> done = new HashSet<>();
        int imported = 0;
        List<LegacyImportPreview.Item> resultRows = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            LegacyCertNoRequest.Item raw = items.get(i);
            String certNo = norm(raw.getCertNo());
            String name = raw.getName() == null ? null : raw.getName().trim();
            // 结构性问题整批拒收、不落半条
            if (certNo.isEmpty()) {
                throw new BusinessException("LEGACY_NO_EMPTY", "第 " + (i + 1) + " 条旧编号为空，整批拒收");
            }
            String status = normalizeStatus(raw.getStatus());
            // 文件内 / 库内 / 已由系统发放：幂等跳过
            if (!done.add(certNo) || existingLegacy.contains(certNo)) {
                resultRows.add(row("SKIP", certNo, name, "重复或已登记，幂等跳过"));
                continue;
            }
            if (existingCert.contains(certNo)) {
                resultRows.add(row("SKIP", certNo, name, "该编号已由系统发放，不属于旧号，跳过"));
                continue;
            }
            LegacyCertNo legacy = new LegacyCertNo();
            legacy.setCertNo(certNo);
            legacy.setName(name);
            legacy.setBatchCode(raw.getBatchCode() == null ? null : raw.getBatchCode().trim().toUpperCase());
            legacy.setCertYear(raw.getCertYear() == null ? null : raw.getCertYear().trim());
            String project = raw.getProjectCode() == null || raw.getProjectCode().trim().isEmpty()
                    ? CertConstants.PROJECT_DEFAULT : raw.getProjectCode().trim().toUpperCase();
            legacy.setProjectCode(project);
            legacy.setSource(raw.getSource());
            legacy.setStatus(status);
            legacy.setOperator(operator);
            legacy.setRemark(remark);
            legacyMapper.insert(legacy);
            imported++;
            resultRows.add(row("IMPORT", certNo, name, null));
        }
        LegacyImportPreview result = new LegacyImportPreview();
        result.setTotalScanned(items.size());
        result.setActionableCount(imported);
        result.setSkipCount(resultRows.size() - imported);
        result.setItems(resultRows);
        result.setSummary("旧编号登记完成：导入 " + imported + " 条，跳过 "
                + (resultRows.size() - imported) + " 条，经办人 " + operator + "，时间 "
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        return result;
    }

    @Transactional(readOnly = true)
    public List<LegacyCertNo> list(String projectCode, String status) {
        QueryWrapper<LegacyCertNo> wrapper = new QueryWrapper<LegacyCertNo>().orderByDesc("id");
        if (projectCode != null && !projectCode.trim().isEmpty()) {
            wrapper.eq("project_code", projectCode.trim().toUpperCase());
        }
        if (status != null && !status.trim().isEmpty()) {
            wrapper.eq("status", status.trim().toUpperCase());
        }
        return legacyMapper.selectList(wrapper);
    }

    private boolean validStatus(String status) {
        if (status == null || status.trim().isEmpty()) {
            return true;
        }
        String s = status.trim().toUpperCase();
        return CertConstants.CERT_ISSUED.equals(s) || CertConstants.CERT_REVOKED.equals(s);
    }

    private String normalizeStatus(String status) {
        if (status == null || status.trim().isEmpty()) {
            return CertConstants.CERT_ISSUED;
        }
        String s = status.trim().toUpperCase();
        if (!CertConstants.CERT_ISSUED.equals(s) && !CertConstants.CERT_REVOKED.equals(s)) {
            throw new BusinessException("LEGACY_STATUS_INVALID",
                    "旧号状态只支持 ISSUED / REVOKED：" + status);
        }
        return s;
    }

    private LegacyImportPreview.Item row(String action, String certNo, String name, String reason) {
        LegacyImportPreview.Item row = new LegacyImportPreview.Item();
        row.setAction(action);
        row.setCertNo(certNo);
        row.setName(name);
        row.setReason(reason);
        return row;
    }

    private List<LegacyCertNoRequest.Item> requireItems(LegacyCertNoRequest request) {
        if (request == null || request.getItems() == null || request.getItems().isEmpty()) {
            throw new BusinessException("LEGACY_ITEMS_REQUIRED", "旧编号条目不能为空");
        }
        return request.getItems();
    }

    private Set<String> loadExistingLegacy() {
        Set<String> set = new HashSet<>();
        for (LegacyCertNo l : legacyMapper.selectList(null)) {
            set.add(l.getCertNo());
        }
        return set;
    }

    private Set<String> loadExistingCert() {
        Set<String> set = new HashSet<>();
        certificateMapper.selectList(null).forEach(c -> set.add(c.getCertNo()));
        return set;
    }

    private String norm(String certNo) {
        return certNo == null ? "" : certNo.trim().toUpperCase().replace(" ", "");
    }
}
