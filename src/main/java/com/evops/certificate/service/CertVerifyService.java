package com.evops.certificate.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.certificate.common.CertConstants;
import com.evops.certificate.common.ChecksumCodec;
import com.evops.certificate.common.VerifyRateLimiter;
import com.evops.certificate.entity.Certificate;
import com.evops.certificate.entity.LegacyCertNo;
import com.evops.certificate.mapper.CertificateMapper;
import com.evops.certificate.mapper.LegacyCertNoMapper;
import com.evops.certificate.vo.CertVerifyView;
import com.evops.exam.entity.ExamBatch;
import com.evops.exam.service.ExamBatchService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;

/**
 * 对外验真窗口：凭编号 + 姓名查询，只回有效性与批次，不带证件号/电话等任何隐私字段。
 * 编号不存在与姓名不一致统一回 NOT_MATCHED，防止按编号枚举姓名。
 */
@Service
public class CertVerifyService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final CertificateMapper certificateMapper;
    private final LegacyCertNoMapper legacyMapper;
    private final ExamBatchService batchService;
    private final VerifyRateLimiter rateLimiter;

    public CertVerifyService(CertificateMapper certificateMapper,
                             LegacyCertNoMapper legacyMapper,
                             ExamBatchService batchService,
                             VerifyRateLimiter rateLimiter) {
        this.certificateMapper = certificateMapper;
        this.legacyMapper = legacyMapper;
        this.batchService = batchService;
        this.rateLimiter = rateLimiter;
    }

    @Transactional(readOnly = true)
    public CertVerifyView verify(String certNo, String name, String clientIp) {
        // 小流量闸门放在最前：格式探测也算一次请求，防止用废号刷接口
        rateLimiter.check(clientIp);

        String no = normalize(certNo);
        String nm = name == null ? "" : name.trim();
        if (no.isEmpty() || nm.isEmpty()) {
            return view(CertConstants.VERIFY_NOT_MATCHED, "编号与姓名均为必填项", null);
        }

        Certificate cert = certificateMapper.selectOne(
                new QueryWrapper<Certificate>().eq("cert_no", no).last("LIMIT 1"));
        if (cert != null) {
            return forCertificate(cert, nm, no);
        }
        LegacyCertNo legacy = legacyMapper.selectOne(
                new QueryWrapper<LegacyCertNo>().eq("cert_no", no).last("LIMIT 1"));
        if (legacy != null) {
            return forLegacy(legacy, nm);
        }

        // 库内没有：先看编号校验码是否自洽，不自洽直接回格式不合法（连姓名比对都不做，不回显输入）
        String compact = no.replace("-", "");
        if (!ChecksumCodec.isValid(compact)) {
            return view(CertConstants.VERIFY_INVALID_FORMAT,
                    "编号格式或校验码不正确，请核对原件后重新输入（注意区分大小写，可直接输入完整编号）", null);
        }
        return view(CertConstants.VERIFY_NOT_MATCHED,
                "未核验到匹配的证书：编号不存在，或编号与姓名不一致", null);
    }

    private CertVerifyView forCertificate(Certificate cert, String inputName, String inputNo) {
        if (!inputName.equals(cert.getName())) {
            // 与"编号不存在"同口径，避免暴露编号真伪
            return view(CertConstants.VERIFY_NOT_MATCHED,
                    "未核验到匹配的证书：编号不存在，或编号与姓名不一致", null);
        }
        CertVerifyView v = base(cert.getName(), cert.getBatchId(), cert.getBatchCode(),
                cert.getCertYear(), cert.getIssuedAt() == null ? null : cert.getIssuedAt().format(TS));
        if (CertConstants.CERT_REVOKED.equals(cert.getStatus())) {
            v.setResult(CertConstants.VERIFY_REVOKED);
            v.setMessage("该证书已作废，不再有效");
            v.setRevokedAt(cert.getRevokedAt() == null ? null : cert.getRevokedAt().format(TS));
            v.setRevokeReason(cert.getRevokeReason());
        } else {
            v.setResult(CertConstants.VERIFY_VALID);
            v.setMessage("核验通过：证书有效");
        }
        return v;
    }

    private CertVerifyView forLegacy(LegacyCertNo legacy, String inputName) {
        String legacyName = legacy.getName();
        if (legacyName == null || !inputName.equals(legacyName)) {
            return view(CertConstants.VERIFY_NOT_MATCHED,
                    "未核验到匹配的证书：编号不存在，或编号与姓名不一致", null);
        }
        CertVerifyView v = base(legacy.getName(), null, legacy.getBatchCode(), legacy.getCertYear(), null);
        if (CertConstants.CERT_REVOKED.equals(legacy.getStatus())) {
            v.setResult(CertConstants.VERIFY_REVOKED);
            v.setMessage("该编号为历史登记证书，且已作废，不再有效");
            v.setRevokeReason(legacy.getRemark());
        } else {
            v.setResult(CertConstants.VERIFY_VALID);
            v.setMessage("核验通过：该编号为系统启用前发放的历史证书，登记有效");
        }
        return v;
    }

    private CertVerifyView base(String name, Long batchId, String batchCode, String year, String issuedAt) {
        CertVerifyView v = new CertVerifyView();
        v.setName(name);
        v.setBatchCode(batchCode);
        v.setCertYear(year);
        v.setIssuedAt(issuedAt);
        if (batchId != null) {
            try {
                ExamBatch batch = batchService.getBatch(batchId);
                v.setBatchName(batch.getName());
                if (v.getBatchCode() == null) {
                    v.setBatchCode(batch.getCode());
                }
            } catch (RuntimeException ignored) {
                // 批次已不在册不影响证书核验结论
            }
        }
        return v;
    }

    private CertVerifyView view(String result, String message, String batchCode) {
        CertVerifyView v = new CertVerifyView();
        v.setResult(result);
        v.setMessage(message);
        v.setBatchCode(batchCode);
        return v;
    }

    private String normalize(String certNo) {
        return certNo == null ? "" : certNo.trim().toUpperCase().replace(" ", "");
    }
}
