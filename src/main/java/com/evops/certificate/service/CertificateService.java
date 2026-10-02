package com.evops.certificate.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.certificate.common.CertConstants;
import com.evops.certificate.common.CertNumberFormatter;
import com.evops.certificate.dto.CertIssueRequest;
import com.evops.certificate.dto.CertNumberParams;
import com.evops.certificate.entity.CertNumberRuleVersion;
import com.evops.certificate.entity.Certificate;
import com.evops.certificate.entity.CertificateRevoke;
import com.evops.certificate.entity.CertificateSeq;
import com.evops.certificate.entity.LegacyCertNo;
import com.evops.certificate.mapper.CertNumberRuleVersionMapper;
import com.evops.certificate.mapper.CertificateMapper;
import com.evops.certificate.mapper.CertificateRevokeMapper;
import com.evops.certificate.mapper.CertificateSeqMapper;
import com.evops.certificate.mapper.LegacyCertNoMapper;
import com.evops.certificate.vo.CertIssueItem;
import com.evops.certificate.vo.CertIssuePreview;
import com.evops.certificate.vo.CertIssueResult;
import com.evops.common.BusinessException;
import com.evops.common.OperatorHolder;
import com.evops.exam.entity.ExamBatch;
import com.evops.exam.entity.Registration;
import com.evops.exam.service.ExamBatchService;
import com.evops.exam.service.RegistrationService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 证书发证 / 作废 / 查询：
 * - 发号按项目当前 ACTIVE 规则版本：年度 + 批次编码 + 流水 + 校验码，证书行永久冻结规则版本号（规则调整只管新证）；
 * - 流水按"规则版本 + 年度 + 批次编码"计数，换版本即重起，发号时扫库与旧号占用表避让，绝不撞旧号；
 * - 发证后信息只读（无更新接口）；作废只追加 t_certificate_revoke，不可撤销。
 */
@Service
public class CertificateService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final CertificateMapper certificateMapper;
    private final CertificateRevokeMapper revokeMapper;
    private final CertificateSeqMapper seqMapper;
    private final LegacyCertNoMapper legacyMapper;
    private final CertNumberRuleService ruleService;
    private final ExamBatchService batchService;
    private final RegistrationService registrationService;

    public CertificateService(CertificateMapper certificateMapper,
                              CertificateRevokeMapper revokeMapper,
                              CertificateSeqMapper seqMapper,
                              LegacyCertNoMapper legacyMapper,
                              CertNumberRuleService ruleService,
                              ExamBatchService batchService,
                              RegistrationService registrationService) {
        this.certificateMapper = certificateMapper;
        this.revokeMapper = revokeMapper;
        this.seqMapper = seqMapper;
        this.legacyMapper = legacyMapper;
        this.ruleService = ruleService;
        this.batchService = batchService;
        this.registrationService = registrationService;
    }

    // ---------- 发证预检 ----------

    @Transactional(readOnly = true)
    public CertIssuePreview previewIssue(CertIssueRequest request) {
        Target target = resolveTarget(request);
        ExamBatch batch = target.batch;
        String year = String.valueOf(LocalDateTime.now().getYear());

        CertNumberRuleVersion version = ruleService.requireActiveVersion(batch.getProjectCode());
        CertNumberParams params = ruleService.readParams(version.getParams());

        Set<Long> certified = certifiedRegistrationIds(batch.getId());
        List<CertIssueItem> items = new ArrayList<>();
        int actionable = 0;
        int running = peekNextSeq(version.getId(), year, batch.getCode(), params.getSeqStart()) - 1;
        for (Registration reg : target.registrations) {
            if (certified.contains(reg.getId())) {
                items.add(CertIssueItem.skip(reg.getRegNo(), reg.getName(), "该名册行已发证，幂等跳过"));
                continue;
            }
            // 预检按当前计数模拟发号（遇旧号占用顺延）；执行时以实际推进结果为准
            running++;
            while (isNoOccupied(CertNumberFormatter.fullNo(params, year, batch.getCode(), running))) {
                running++;
            }
            String planned = CertNumberFormatter.fullNo(params, year, batch.getCode(), running);
            items.add(CertIssueItem.issue(reg.getRegNo(), reg.getName(), planned));
            actionable++;
        }
        for (CertIssueItem skip : target.skips) {
            items.add(skip);
        }

        CertIssuePreview preview = new CertIssuePreview();
        preview.setBatchId(batch.getId());
        preview.setBatchCode(batch.getCode());
        preview.setBatchName(batch.getName());
        preview.setProjectCode(batch.getProjectCode());
        preview.setCertYear(year);
        preview.setRuleCode(ruleService.getRule(version.getRuleId()).getCode());
        preview.setRuleVersionNo(version.getVersionNo());
        preview.setTotalScanned(target.registrations.size() + target.skips.size());
        preview.setActionableCount(actionable);
        preview.setSkipCount(items.size() - actionable);
        preview.setItems(items);
        preview.setSummary("批次 " + batch.getCode() + " 可发证 " + actionable + " 张，跳过 "
                + preview.getSkipCount() + " 条；编号口径 " + version.getVersionNo() + "，年度 " + year);
        return preview;
    }

    // ---------- 发证执行 ----------

    @Transactional
    public CertIssueResult executeIssue(CertIssueRequest request) {
        Target target = resolveTarget(request);
        ExamBatch batch = target.batch;
        LocalDateTime now = LocalDateTime.now();
        String year = String.valueOf(now.getYear());
        String operator = OperatorHolder.current();

        CertNumberRuleVersion version = ruleService.requireActiveVersion(batch.getProjectCode());
        CertNumberParams params = ruleService.readParams(version.getParams());

        Set<Long> certified = certifiedRegistrationIds(batch.getId());
        List<String> issued = new ArrayList<>();
        List<CertIssueItem> skipped = new ArrayList<>();
        for (Registration reg : target.registrations) {
            if (certified.contains(reg.getId())) {
                skipped.add(CertIssueItem.skip(reg.getRegNo(), reg.getName(), "该名册行已发证，幂等跳过"));
                continue;
            }
            // 原子推进流水；撞库内/旧号则继续推进，直到拿到空号（计数器真实越过占用段，不回头撞旧号）
            int seq = nextFreeSeq(params, version.getId(), year, batch.getCode(), params.getSeqStart());
            String certNo = CertNumberFormatter.fullNo(params, year, batch.getCode(), seq);
            Certificate cert = new Certificate();
            cert.setCertNo(certNo);
            cert.setBatchId(batch.getId());
            cert.setRegistrationId(reg.getId());
            cert.setProjectCode(batch.getProjectCode());
            cert.setName(reg.getName());
            cert.setRegNo(reg.getRegNo());
            cert.setSubject(reg.getSubject());
            cert.setCertYear(year);
            cert.setBatchCode(batch.getCode());
            cert.setSeqValue(seq);
            cert.setRuleId(version.getRuleId());
            cert.setRuleVersionId(version.getId());
            cert.setRuleVersionNo(version.getVersionNo());
            cert.setChecksumValid(!CertConstants.CHECKSUM_NONE.equals(params.getChecksumAlgo()));
            cert.setStatus(CertConstants.CERT_ISSUED);
            cert.setIssuedBy(operator);
            cert.setIssuedAt(now);
            cert.setRemark(request.getRemark());
            try {
                certificateMapper.insert(cert);
            } catch (DuplicateKeyException e) {
                // 并发下编号或名册唯一键冲突：整批发证回滚，提示重试（小流量窗口基本不会发生）
                throw new BusinessException("CERT_ISSUE_CONFLICT",
                        "发证并发冲突（编号或名册行被占用），请重新预检后执行：" + reg.getRegNo());
            }
            issued.add(certNo);
            certified.add(reg.getId());
        }
        skipped.addAll(target.skips);

        if (target.registrations.isEmpty()) {
            throw new BusinessException("CERT_NO_TARGET", "批次内没有可发证的名册行（指定的报名号均无效）");
        }

        CertIssueResult result = new CertIssueResult();
        result.setBatchId(batch.getId());
        result.setBatchCode(batch.getCode());
        result.setCertYear(year);
        result.setRuleVersionNo(version.getVersionNo());
        result.setAffectedCount(issued.size());
        result.setSkipCount(skipped.size());
        result.setOperator(operator);
        result.setIssuedAt(now.format(TS));
        result.setIssuedCertNos(issued);
        result.setSkipped(skipped);
        return result;
    }

    // ---------- 作废（只追加留记录，不可撤销） ----------

    @Transactional
    public Certificate revoke(Long certificateId, String reason) {
        Certificate cert = requireCertificate(certificateId);
        if (CertConstants.CERT_REVOKED.equals(cert.getStatus())) {
            throw new BusinessException("CERT_ALREADY_REVOKED",
                    "证书已作废，不能重复作废：" + cert.getCertNo());
        }
        String r = reason == null ? "" : reason.trim();
        if (r.isEmpty()) {
            throw new BusinessException("REVOKE_REASON_REQUIRED", "作废原因不能为空");
        }
        LocalDateTime now = LocalDateTime.now();
        String operator = OperatorHolder.current();

        CertificateRevoke record = new CertificateRevoke();
        record.setCertificateId(cert.getId());
        record.setCertNo(cert.getCertNo());
        record.setName(cert.getName());
        record.setBatchId(cert.getBatchId());
        record.setReason(r);
        record.setOperator(operator);
        record.setRevokedAt(now);
        revokeMapper.insert(record);

        cert.setStatus(CertConstants.CERT_REVOKED);
        cert.setRevokedAt(now);
        cert.setRevokeReason(r);
        certificateMapper.updateById(cert);
        return cert;
    }

    // ---------- 查询（内部管理视图，走鉴权） ----------

    @Transactional(readOnly = true)
    public List<Certificate> listByBatch(Long batchId) {
        batchService.getBatch(batchId);
        return certificateMapper.selectList(new QueryWrapper<Certificate>()
                .eq("batch_id", batchId)
                .orderByAsc("cert_no"));
    }

    public Certificate requireCertificate(Long id) {
        if (id == null) {
            throw new BusinessException("CERT_ID_REQUIRED", "证书 id 不能为空");
        }
        Certificate cert = certificateMapper.selectById(id);
        if (cert == null) {
            throw new BusinessException("CERT_NOT_FOUND", "证书不存在：id=" + id);
        }
        return cert;
    }

    @Transactional(readOnly = true)
    public Certificate getByCertNo(String certNo) {
        if (certNo == null || certNo.trim().isEmpty()) {
            throw new BusinessException("CERT_NO_REQUIRED", "证书编号不能为空");
        }
        Certificate cert = certificateMapper.selectOne(
                new QueryWrapper<Certificate>().eq("cert_no", certNo.trim().toUpperCase()));
        if (cert == null) {
            throw new BusinessException("CERT_NOT_FOUND", "未查到证书：" + certNo.trim());
        }
        return cert;
    }

    @Transactional(readOnly = true)
    public List<CertificateRevoke> listRevokes(Long certificateId) {
        requireCertificate(certificateId);
        return revokeMapper.selectList(new QueryWrapper<CertificateRevoke>()
                .eq("certificate_id", certificateId)
                .orderByAsc("id"));
    }

    // ---------- 发号核心 ----------

    /** 预检只读视角的下一流水（库内无计数行则从起点开始）。 */
    private int peekNextSeq(Long ruleVersionId, String year, String batchCode, int seqStart) {
        CertificateSeq seq = seqMapper.selectOne(new QueryWrapper<CertificateSeq>()
                .eq("rule_version_id", ruleVersionId)
                .eq("cert_year", year)
                .eq("batch_code", batchCode));
        return (seq == null ? seqStart - 1 : seq.getLastSeq()) + 1;
    }

    /**
     * 取一个不撞任何旧号的流水：原子推进计数器，拼出的编号若已被库内证书或旧号登记占用，
     * 继续推进，直到拿到空号。计数器只增不减，因此"流水重起"（换新规则版本）也不会回头撞旧号。
     */
    private int nextFreeSeq(CertNumberParams params, Long ruleVersionId, String year,
                            String batchCode, int seqStart) {
        ensureSeqRow(ruleVersionId, year, batchCode, seqStart);
        int guard = 0;
        while (true) {
            int seq = advanceSeq(ruleVersionId, year, batchCode);
            String candidate = CertNumberFormatter.fullNo(params, year, batchCode, seq);
            if (!isNoOccupied(candidate)) {
                return seq;
            }
            if (++guard > 10000) {
                throw new BusinessException("CERT_SEQ_EXHAUSTED",
                        "批次 " + batchCode + " 连续万个编号被占用，发号中止，请人工排查旧号数据");
            }
        }
    }

    private boolean isNoOccupied(String certNo) {
        return certificateMapper.selectCount(new QueryWrapper<Certificate>().eq("cert_no", certNo)) > 0
                || legacyMapper.selectCount(new QueryWrapper<LegacyCertNo>().eq("cert_no", certNo)) > 0;
    }

    /** 计数器行不存在则建（起点 last_seq = seqStart - 1）；并发首插撞唯一键时忽略，复用已有行。 */
    private void ensureSeqRow(Long ruleVersionId, String year, String batchCode, int seqStart) {
        Long count = seqMapper.selectCount(new QueryWrapper<CertificateSeq>()
                .eq("rule_version_id", ruleVersionId)
                .eq("cert_year", year)
                .eq("batch_code", batchCode));
        if (count != null && count > 0) {
            return;
        }
        CertificateSeq seq = new CertificateSeq();
        seq.setRuleVersionId(ruleVersionId);
        seq.setCertYear(year);
        seq.setBatchCode(batchCode);
        seq.setStartSeq(seqStart);
        seq.setLastSeq(seqStart - 1);
        try {
            seqMapper.insert(seq);
        } catch (DuplicateKeyException ignored) {
            // 并发首插，复用对方行
        }
    }

    /** 原子 +1 推进：行级 UPDATE 串行化同键并发发证，返回推进后的值。 */
    private int advanceSeq(Long ruleVersionId, String year, String batchCode) {
        CertificateSeq before = seqMapper.selectOne(new QueryWrapper<CertificateSeq>()
                .eq("rule_version_id", ruleVersionId)
                .eq("cert_year", year)
                .eq("batch_code", batchCode));
        int updated = seqMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<CertificateSeq>()
                        .eq("id", before.getId())
                        .eq("last_seq", before.getLastSeq())
                        .setSql("last_seq = last_seq + 1"));
        if (updated == 0) {
            throw new BusinessException("CERT_SEQ_CONFLICT", "流水推进冲突，请重试：" + batchCode);
        }
        CertificateSeq after = seqMapper.selectById(before.getId());
        return after.getLastSeq();
    }

    private Set<Long> certifiedRegistrationIds(Long batchId) {
        Set<Long> ids = new HashSet<>();
        for (Certificate c : certificateMapper.selectList(
                new QueryWrapper<Certificate>().eq("batch_id", batchId))) {
            ids.add(c.getRegistrationId());
        }
        return ids;
    }

    /** 解析发证对象：批次 + 有序名册（指定 regNos 时逐条校验归属，非法的进跳过清单）。 */
    private Target resolveTarget(CertIssueRequest request) {
        if (request == null || request.getBatchId() == null) {
            throw new BusinessException("BATCH_ID_REQUIRED", "发证必须指定批次 batchId");
        }
        ExamBatch batch = batchService.getBatch(request.getBatchId());
        List<Registration> all = registrationService.listByBatch(batch.getId());
        Map<String, Registration> byRegNo = new LinkedHashMap<>();
        for (Registration reg : all) {
            byRegNo.put(reg.getRegNo(), reg);
        }

        Target target = new Target();
        target.batch = batch;
        List<CertIssueItem> skips = new ArrayList<>();
        if (request.getRegNos() == null || request.getRegNos().isEmpty()) {
            target.registrations = new ArrayList<>(byRegNo.values());
        } else {
            List<Registration> picked = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (String regNo : request.getRegNos()) {
                String key = regNo == null ? "" : regNo.trim();
                if (!seen.add(key)) {
                    skips.add(CertIssueItem.skip(key, null, "请求内报名号重复，跳过"));
                    continue;
                }
                Registration reg = byRegNo.get(key);
                if (reg == null) {
                    skips.add(CertIssueItem.skip(key, null, "报名号不存在或不属于本批次，跳过"));
                } else {
                    picked.add(reg);
                }
            }
            target.registrations = picked;
        }
        // 编号按报名号顺序连续分配，保证一批证号可读、可对账
        target.registrations.sort((a, b) -> a.getRegNo().compareTo(b.getRegNo()));
        target.skips = skips;
        return target;
    }

    /** 试算仅供控制器/其他服务需要时复用。 */
    public CertNumberRuleVersion activeVersionOf(String projectCode) {
        return ruleService.requireActiveVersion(projectCode);
    }

    private static class Target {
        private ExamBatch batch;
        private List<Registration> registrations;
        private List<CertIssueItem> skips;
    }
}
